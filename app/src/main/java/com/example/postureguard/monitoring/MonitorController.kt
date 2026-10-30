package com.example.postureguard.monitoring

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.example.postureguard.alert.AlertChannel
import com.example.postureguard.alert.AlertDispatcher
import com.example.postureguard.alert.AlertPlanner
import com.example.postureguard.alert.AlertPolicy
import com.example.postureguard.camera.CameraFrameResult
import com.example.postureguard.data.entity.SessionEntity
import com.example.postureguard.data.repository.MonitoringRepository
import com.example.postureguard.data.settings.AppSettings
import com.example.postureguard.data.settings.SettingsRepository
import com.example.postureguard.posture.engine.PostureStateMachine
import com.example.postureguard.posture.features.CameraDriftDetector
import com.example.postureguard.posture.features.PostureFeaturePipeline
import com.example.postureguard.posture.filters.AngleEmaFilter
import com.example.postureguard.posture.filters.EmaFilter
import com.example.postureguard.posture.model.CalibrationProfile
import com.example.postureguard.posture.model.EffectiveThresholds
import com.example.postureguard.posture.model.PostureThresholdConfig
import com.example.postureguard.posture.model.PoseFrame
import com.example.postureguard.posture.model.RawMetrics
import com.example.postureguard.service.MonitoringService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Application scoped owner of one monitoring session: it consumes camera frame results on a
 * worker executor, applies filtering and the state machine, records aggregates, dispatches
 * alerts and checkpoints the session into Room. All lifecycle entry points are safe to call
 * from the main thread.
 */
class MonitorController(
    context: Context,
    private val settingsRepository: SettingsRepository,
    private val monitoringRepository: MonitoringRepository,
    private val alertDispatcher: AlertDispatcher,
    private val now: () -> Long = System::currentTimeMillis,
    private val monotonicNow: () -> Long = SystemClock::elapsedRealtime,
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private val _state = MutableStateFlow(MonitorState())
    val state: StateFlow<MonitorState> = _state.asStateFlow()

    private val processor = Executors.newSingleThreadExecutor { Thread(it, "monitor-frame") }
    private val persistence = Executors.newSingleThreadExecutor { Thread(it, "monitor-store") }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var liveAlertChannels: Set<AlertChannel> =
        setOf(AlertChannel.SOUND, AlertChannel.VIBRATION)

    private var tickerJob: Job? = null
    private var phase = MonitorPhase.IDLE
    private var baseline: CalibrationProfile? = null
    private var config = PostureThresholdConfig()
    private var machine: PostureStateMachine? = null
    private var pipeline = PostureFeaturePipeline()
    private val driftDetector = CameraDriftDetector()
    private var recorder: MonitoringSessionRecorder? = null
    private var planner: AlertPlanner? = null

    private var sessionId: String? = null
    private var startedAtWallMs = 0L
    private var pausedAtWallMs: Long? = null
    private var pausedAccumulatedMs = 0L
    private var lastFrameMonoMs: Long? = null
    private var lastCheckpointWallMs = 0L

    private var forwardFilter = EmaFilter(config.emaAlpha, config.emaReferenceIntervalMs)
    private var headFilter = AngleEmaFilter(config.emaAlpha, config.emaReferenceIntervalMs)
    private var torsoFilter = AngleEmaFilter(config.emaAlpha, config.emaReferenceIntervalMs)

    init {
        scope.launch {
            settingsRepository.settings.collect { liveAlertChannels = it.enabledChannels() }
        }
    }

    fun start(profile: CalibrationProfile, settings: AppSettings) {
        val startWall = now()
        synchronized(lock) {
            if (phase == MonitorPhase.RUNNING || phase == MonitorPhase.PAUSED) return
            config = PostureThresholdConfig().copy(
                mildHoldDurationMs = settings.mildHoldDurationMs,
                alertCooldownMs = settings.alertCooldownMs,
            )
            val thresholds = EffectiveThresholds.from(config, settings.sensitivity, profile)
            baseline = profile
            machine = PostureStateMachine(config, thresholds)
            pipeline = PostureFeaturePipeline(config)
            recorder = MonitoringSessionRecorder(startWall)
            planner = AlertPlanner(
                AlertPolicy(
                    soundEnabled = true,
                    speechEnabled = true,
                    vibrationEnabled = true,
                    mildHoldDurationMs = settings.mildHoldDurationMs,
                    severeHoldDurationMs = config.severeHoldDurationMs,
                    cooldownMs = settings.alertCooldownMs,
                    recoveryHoldDurationMs = config.recoveryHoldDurationMs,
                ),
            )
            liveAlertChannels = settings.enabledChannels()
            sessionId = UUID.randomUUID().toString()
            startedAtWallMs = startWall
            pausedAtWallMs = null
            pausedAccumulatedMs = 0L
            lastFrameMonoMs = null
            lastCheckpointWallMs = startWall
            resetFilters()
            driftDetector.reset(profile.torsoScaleMedian)
            phase = MonitorPhase.RUNNING
            _state.value = MonitorState(
                phase = MonitorPhase.RUNNING,
                sessionId = sessionId,
                startedAtMs = startWall,
                calibrationProfileId = profile.id,
            )
            startTicker()
        }
        MonitoringService.start(appContext)
    }

    fun pause() {
        synchronized(lock) {
            if (phase != MonitorPhase.RUNNING) return
            val wall = now()
            pausedAtWallMs = wall
            recorder?.onPause(wall)
            val snapshot = machine?.pause(monotonicNow())
            lastFrameMonoMs = null
            phase = MonitorPhase.PAUSED
            checkpointLocked(wall)
            _state.update {
                it.copy(
                    phase = MonitorPhase.PAUSED,
                    snapshot = snapshot ?: it.snapshot,
                    elapsedMs = elapsedLocked(wall),
                )
            }
        }
        MonitoringService.stop(appContext)
    }

    fun resume() {
        synchronized(lock) {
            if (phase != MonitorPhase.PAUSED) return
            val wall = now()
            pausedAtWallMs?.let { pausedAccumulatedMs += (wall - it).coerceAtLeast(0) }
            pausedAtWallMs = null
            recorder?.onResume(wall)
            machine?.reset()
            pipeline.reset()
            resetFilters()
            lastFrameMonoMs = null
            lastCheckpointWallMs = wall
            phase = MonitorPhase.RUNNING
            _state.update {
                it.copy(
                    phase = MonitorPhase.RUNNING,
                    snapshot = null,
                    errorMessage = null,
                    elapsedMs = elapsedLocked(wall),
                )
            }
            startTicker()
        }
        MonitoringService.start(appContext)
    }

    fun stop() {
        var stopped = false
        synchronized(lock) {
            if (phase == MonitorPhase.IDLE || phase == MonitorPhase.FINISHED) return
            val wall = now()
            val activeRecorder = recorder
            val activeSessionId = sessionId
            if (activeRecorder != null && activeSessionId != null) {
                activeRecorder.finish(wall)
                enqueuePersist(payloadLocked(activeRecorder, activeSessionId, wall, finalized = true))
            }
            tickerJob?.cancel()
            tickerJob = null
            phase = MonitorPhase.FINISHED
            recorder = null
            machine = null
            planner = null
            pausedAtWallMs = null
            _state.update {
                it.copy(
                    phase = MonitorPhase.FINISHED,
                    snapshot = null,
                    lastAlert = null,
                    errorMessage = null,
                    elapsedMs = if (startedAtWallMs > 0) (wall - startedAtWallMs).coerceAtLeast(0) else 0L,
                )
            }
            stopped = true
        }
        if (stopped) MonitoringService.stop(appContext)
    }

    fun reset() {
        synchronized(lock) {
            if (phase == MonitorPhase.RUNNING || phase == MonitorPhase.PAUSED) return
            phase = MonitorPhase.IDLE
            baseline = null
            sessionId = null
            startedAtWallMs = 0L
            _state.value = MonitorState()
        }
    }

    fun onFrame(result: CameraFrameResult) {
        val profile = synchronized(lock) {
            if (phase != MonitorPhase.RUNNING) return
            baseline ?: return
        }
        processor.execute { processFrame(result, profile) }
    }

    fun onCameraUnavailable(message: String) {
        pause()
        _state.update { it.copy(errorMessage = message) }
    }

    fun close() {
        stop()
        tickerJob?.cancel()
        scope.cancel()
        processor.shutdown()
        persistence.shutdown()
    }

    private fun processFrame(result: CameraFrameResult, profile: CalibrationProfile) {
        val alert = synchronized(lock) {
            if (phase != MonitorPhase.RUNNING) return
            val activeMachine = machine ?: return
            val activeRecorder = recorder ?: return

            val mono = result.timestampMs.takeIf { it > 0 } ?: monotonicNow()
            val wall = now()
            val feature = pipeline.process(
                PoseFrame(result.pose, mono, result.imageWidth, result.imageHeight),
                profile.facingDirection,
            )
            val previousMono = lastFrameMonoMs
            val filtered = if (feature.quality.valid && feature.metrics != null) {
                val raw = feature.metrics
                RawMetrics(
                    forwardOffset = forwardFilter.update(raw.forwardOffset, mono, previousMono),
                    headAngleDeg = headFilter.update(raw.headAngleDeg, mono, previousMono),
                    torsoAngleDeg = torsoFilter.update(raw.torsoAngleDeg, mono, previousMono),
                    torsoScale = raw.torsoScale,
                )
            } else {
                null
            }
            val deltas = filtered?.let { PostureFeaturePipeline.deltas(it, profile) }
            val snapshot = activeMachine.update(feature.quality, deltas, mono)
            lastFrameMonoMs = mono
            activeRecorder.onSnapshot(snapshot, wall)
            val drifting = driftDetector.observe(feature.metrics?.torsoScale, mono)

            val request = if (liveAlertChannels.isNotEmpty()) {
                planner?.onSnapshot(snapshot)?.let { plan ->
                    val channels = plan.channels intersect liveAlertChannels
                    if (channels.isEmpty()) null else plan.copy(channels = channels)
                }
            } else {
                null
            }
            val fps = if (result.frameIntervalMs in 1..1_000) {
                1_000f / result.frameIntervalMs
            } else {
                _state.value.effectiveFps
            }
            _state.update { current ->
                current.copy(
                    phase = MonitorPhase.RUNNING,
                    snapshot = snapshot,
                    elapsedMs = elapsedLocked(wall),
                    cameraMoved = drifting,
                    effectiveFps = fps,
                    inferenceLatencyMs = result.inferenceLatencyMs,
                    droppedFrames = result.droppedFrames,
                    lastAlert = request ?: current.lastAlert,
                    lastAlertAtMs = if (request != null) wall else current.lastAlertAtMs,
                    errorMessage = null,
                )
            }
            if (wall - lastCheckpointWallMs >= CHECKPOINT_INTERVAL_MS) {
                checkpointLocked(wall)
            }
            request
        }
        if (alert != null) alertDispatcher.dispatch(alert)
    }

    private fun checkpointLocked(wallMs: Long) {
        val activeRecorder = recorder ?: return
        val activeSessionId = sessionId ?: return
        lastCheckpointWallMs = wallMs
        enqueuePersist(payloadLocked(activeRecorder, activeSessionId, wallMs, finalized = false))
    }

    private fun payloadLocked(
        activeRecorder: MonitoringSessionRecorder,
        activeSessionId: String,
        wallMs: Long,
        finalized: Boolean,
    ): SessionPayload {
        val durations = activeRecorder.sessionTotals
        val session = SessionEntity(
            id = activeSessionId,
            startedAt = startedAtWallMs,
            endedAt = if (finalized) wallMs else null,
            totalDurationMs = (wallMs - startedAtWallMs).coerceAtLeast(0),
            validDurationMs = activeRecorder.validDurationMs,
            normalDurationMs = durations.normalMs,
            forwardHeadDurationMs = durations.forwardHeadMs,
            headDownDurationMs = durations.headDownMs,
            headDownForwardDurationMs = durations.headDownForwardMs,
            torsoLeanDurationMs = durations.torsoLeanMs,
            lowConfidenceDurationMs = durations.lowConfidenceMs,
            pausedDurationMs = durations.pausedMs,
            calibrationProfileId = baseline?.id ?: "",
            finalized = finalized,
        )
        return SessionPayload(
            session = session,
            days = activeRecorder.dayAggregates,
            hours = activeRecorder.hourAggregates,
            events = activeRecorder.drainCompletedEvents(),
        )
    }

    private fun enqueuePersist(payload: SessionPayload) {
        persistence.execute {
            runCatching {
                runBlocking {
                    monitoringRepository.checkpointSession(
                        payload.session,
                        payload.days,
                        payload.hours,
                        payload.events,
                    )
                }
            }.onFailure { Log.e(TAG, "Failed to persist monitoring session", it) }
        }
    }

    private fun elapsedLocked(wallMs: Long): Long {
        val currentPause = pausedAtWallMs?.let { (wallMs - it).coerceAtLeast(0) } ?: 0L
        return (wallMs - startedAtWallMs - pausedAccumulatedMs - currentPause).coerceAtLeast(0)
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1_000)
                val elapsed = synchronized(lock) {
                    if (phase != MonitorPhase.RUNNING && phase != MonitorPhase.PAUSED) {
                        return@synchronized null
                    }
                    elapsedLocked(now())
                } ?: break
                _state.update { current ->
                    if (current.phase == MonitorPhase.RUNNING || current.phase == MonitorPhase.PAUSED) {
                        current.copy(elapsedMs = elapsed)
                    } else {
                        current
                    }
                }
            }
        }
    }

    private fun resetFilters() {
        forwardFilter = EmaFilter(config.emaAlpha, config.emaReferenceIntervalMs)
        headFilter = AngleEmaFilter(config.emaAlpha, config.emaReferenceIntervalMs)
        torsoFilter = AngleEmaFilter(config.emaAlpha, config.emaReferenceIntervalMs)
    }

    private fun AppSettings.enabledChannels(): Set<AlertChannel> = buildSet {
        if (soundEnabled) add(AlertChannel.SOUND)
        if (speechEnabled) add(AlertChannel.SPEECH)
        if (vibrationEnabled) add(AlertChannel.VIBRATION)
    }

    private data class SessionPayload(
        val session: SessionEntity,
        val days: List<DayAggregate>,
        val hours: List<HourAggregate>,
        val events: List<RecordedPostureEvent>,
    )

    companion object {
        private const val TAG = "MonitorController"
        private const val CHECKPOINT_INTERVAL_MS = 20_000L
    }
}
