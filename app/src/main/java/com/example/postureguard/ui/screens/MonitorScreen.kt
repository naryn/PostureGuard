package com.example.postureguard.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.postureguard.camera.CameraStatus
import com.example.postureguard.data.entity.SessionEntity
import com.example.postureguard.data.settings.AppSettings
import com.example.postureguard.monitoring.MonitorPhase
import com.example.postureguard.monitoring.MonitorState
import com.example.postureguard.posture.model.ConditionSeverity
import com.example.postureguard.posture.model.PostureState
import com.example.postureguard.posture.model.QualityIssue
import com.example.postureguard.ui.components.CameraPermissionGate
import com.example.postureguard.ui.components.CameraPreviewCard
import com.example.postureguard.ui.components.PageScaffold
import com.example.postureguard.ui.components.PgBarRow
import com.example.postureguard.ui.components.PgCard
import com.example.postureguard.ui.components.PgDivider
import com.example.postureguard.ui.components.PgInfoRow
import com.example.postureguard.ui.components.PgMetricTile
import com.example.postureguard.ui.components.PgNotice
import com.example.postureguard.ui.components.PgPill
import com.example.postureguard.ui.components.PgPrimaryButton
import com.example.postureguard.ui.components.PgQuietButton
import com.example.postureguard.ui.components.PgSectionTitle
import com.example.postureguard.ui.components.PgSwitchRow
import com.example.postureguard.ui.theme.Alert
import com.example.postureguard.ui.theme.AlertPale
import com.example.postureguard.ui.theme.Hairline
import com.example.postureguard.ui.theme.Ink
import com.example.postureguard.ui.theme.InkSoft
import com.example.postureguard.ui.theme.Lavender
import com.example.postureguard.ui.theme.MintPale
import com.example.postureguard.ui.theme.Mist
import com.example.postureguard.ui.theme.SurfaceWhite
import com.example.postureguard.ui.theme.Teal
import com.example.postureguard.ui.theme.TealDeep
import com.example.postureguard.ui.theme.TealPale
import com.example.postureguard.ui.theme.Warm
import com.example.postureguard.ui.theme.WarmPale
import com.example.postureguard.ui.util.facingLabel
import com.example.postureguard.ui.util.formatCompactDuration
import com.example.postureguard.ui.util.formatDecimal
import com.example.postureguard.ui.util.formatElapsed
import com.example.postureguard.ui.util.formatClock
import com.example.postureguard.ui.util.postureStateColor
import com.example.postureguard.ui.util.postureStateContainer
import com.example.postureguard.ui.util.severityLabel
import com.example.postureguard.ui.viewmodel.CalibrationSnapshot
import com.example.postureguard.ui.viewmodel.MonitorViewModel
import com.example.postureguard.ui.viewmodel.rememberAppContainer
import kotlinx.coroutines.flow.flowOf
import kotlin.math.roundToInt

@Composable
fun MonitorScreen(
    viewModel: MonitorViewModel,
    onExit: () -> Unit,
    onRecalibrate: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSession: (String) -> Unit,
    onNeedCalibration: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val calibration by viewModel.calibration.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    when {
        !calibration.loaded -> MonitorLoading()
        calibration.profile == null -> NoCalibration(onBack = onExit, onCalibrate = onNeedCalibration)
        state.phase == MonitorPhase.FINISHED -> FinishedContent(
            sessionId = state.sessionId,
            onExit = onExit,
            onOpenReports = onOpenReports,
            onOpenSession = onOpenSession,
        )
        else -> CameraPermissionGate {
            MonitorBody(
                viewModel = viewModel,
                state = state,
                calibration = calibration,
                onExit = onExit,
                onRecalibrate = onRecalibrate,
            )
        }
    }
}

@Composable
private fun MonitorLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Mist),
        contentAlignment = Alignment.Center,
    ) {
        Text("正在读取校准数据…", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
    }
}

@Composable
private fun NoCalibration(onBack: () -> Unit, onCalibrate: () -> Unit) {
    PageScaffold(title = "姿态监测", subtitle = "开始前需要一次个人校准", onBack = onBack) {
        PgCard {
            PgSectionTitle("尚未建立个人基准")
            Text(
                "坐姿判断会以你本人自然舒适的坐姿为参考。请先完成约 15 秒的校准。",
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
            PgPrimaryButton(text = "去校准", onClick = onCalibrate)
        }
    }
}

@Composable
private fun MonitorBody(
    viewModel: MonitorViewModel,
    state: MonitorState,
    calibration: CalibrationSnapshot,
    onExit: () -> Unit,
    onRecalibrate: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val view = LocalView.current
    val profile = calibration.profile ?: return
    var mirrored by remember { mutableStateOf<Boolean?>(null) }
    var autoStarted by rememberSaveable { mutableStateOf(false) }

    val uiSettings = settings ?: AppSettings()

    LaunchedEffect(profile.id, settings) {
        val loaded = settings ?: return@LaunchedEffect
        if (!autoStarted) {
            autoStarted = true
            if (viewModel.state.value.phase != MonitorPhase.PAUSED) {
                viewModel.ensureStarted(profile, loaded)
            }
        }
    }

    val persistentAlert = state.phase == MonitorPhase.RUNNING &&
        state.lastAlert != null &&
        state.snapshot?.let { it.metricsValid && it.state.isConfirmedAbnormal && !it.recovering } == true

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.pause()
    }

    BackHandler {
        viewModel.pause()
        onExit()
    }

    val keepScreenOn = uiSettings.keepScreenOn && state.isActive
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mist)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .clickable {
                        viewModel.pause()
                        onExit()
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = InkSoft,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text("返回", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = when (state.phase) {
                        MonitorPhase.RUNNING -> "监测中"
                        MonitorPhase.PAUSED -> "已暂停"
                        else -> "准备中"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink,
                )
                Text(
                    formatElapsed(state.elapsedMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSoft,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            ) {
                CameraPreviewCard(
                    active = state.phase == MonitorPhase.RUNNING,
                    targetFps = uiSettings.targetFps,
                    lens = uiSettings.cameraLens,
                    modifier = Modifier.fillMaxSize(),
                    onFrame = { frame ->
                        mirrored = frame.mirrored
                        viewModel.onFrame(frame)
                    },
                    onCameraStatus = { status ->
                        if (status is CameraStatus.Error) viewModel.onCameraError(status.message)
                    },
                )
                CameraBadge(
                    text = "本机处理",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                )
                CameraBadge(
                    text = when (mirrored) {
                        true -> "镜像预览"
                        false -> "未镜像"
                        null -> "相机预览"
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                )
            }

            StatusCard(state)

            if (persistentAlert) {
                PgNotice(
                    title = "姿态提醒",
                    detail = state.lastAlert!!.message,
                    containerColor = AlertPale,
                    contentColor = Alert,
                )
            }

            if (state.cameraMoved) {
                PgNotice(
                    title = "手机位置可能变化",
                    detail = "画面中的人体比例明显偏离校准基准。建议结束本次监测后重新校准。",
                    containerColor = WarmPale,
                    contentColor = Warm,
                    actionLabel = "结束并重新校准",
                    onAction = {
                        viewModel.stop()
                        onRecalibrate()
                    },
                )
            }

            if (state.errorMessage != null) {
                PgNotice(
                    title = "相机中断",
                    detail = "${state.errorMessage}，监测已暂停。",
                    containerColor = AlertPale,
                    contentColor = Alert,
                    actionLabel = "重试",
                    onAction = viewModel::resume,
                )
            }

            if (state.phase == MonitorPhase.PAUSED) {
                PgNotice(
                    title = "监测已暂停",
                    detail = "相机与姿态模型已释放。继续后会重新开始累计持续时长。",
                    containerColor = TealPale,
                    contentColor = TealDeep,
                )
            }

            PgCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (state.phase == MonitorPhase.RUNNING) {
                        PgQuietButton(
                            text = "暂停",
                            onClick = viewModel::pause,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        PgPrimaryButton(
                            text = "继续监测",
                            onClick = viewModel::resume,
                            modifier = Modifier.weight(1f),
                            enabled = settings != null,
                        )
                    }
                    PgPrimaryButton(
                        text = "结束监测",
                        onClick = viewModel::stop,
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(onClick = {
                    viewModel.stop()
                    onRecalibrate()
                }) {
                    Text("结束本次并重新校准", color = InkSoft)
                }
            }

            PgCard {
                PgSectionTitle("提醒开关")
                PgSwitchRow(
                    title = "声音提醒",
                    checked = uiSettings.soundEnabled,
                    onCheckedChange = viewModel::updateSoundEnabled,
                )
                PgSwitchRow(
                    title = "语音提示",
                    description = "用系统中文语音朗读问题，如「低头了，请抬头」",
                    checked = uiSettings.speechEnabled,
                    onCheckedChange = viewModel::updateSpeechEnabled,
                )
                PgSwitchRow(
                    title = "震动提醒",
                    checked = uiSettings.vibrationEnabled,
                    onCheckedChange = viewModel::updateVibrationEnabled,
                )
                PgDivider()
                PgInfoRow("分析帧率", "${state.effectiveFps.roundToInt()} FPS")
                PgInfoRow("推理延迟", "${state.inferenceLatencyMs} ms")
                PgInfoRow("面向方向", facingLabel(profile.facingDirection))
                Text(
                    "画面在手机本地分析，不上传服务器，也不保存预览或视频。",
                    style = MaterialTheme.typography.labelSmall,
                    color = InkSoft,
                )
            }

            Text(
                "异常持续达到设定时长后会开始提醒；只要姿态没有恢复，就按设置的间隔重复提醒。",
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun CameraBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = SurfaceWhite.copy(alpha = 0.92f),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = InkSoft,
        )
    }
}

@Composable
private fun StatusCard(state: MonitorState) {
    val snapshot = state.snapshot
    val displayState = when {
        state.phase == MonitorPhase.PAUSED -> PostureState.PAUSED
        snapshot == null -> PostureState.NOT_READY
        !snapshot.metricsValid -> {
            if (QualityIssue.PERSON_NOT_FOUND in snapshot.quality.issues) {
                PostureState.PERSON_NOT_FOUND
            } else {
                PostureState.LOW_CONFIDENCE
            }
        }
        else -> snapshot.state
    }
    val accent = postureStateColor(displayState)
    val container = postureStateContainer(displayState)
    val deltas = snapshot?.deltas

    Surface(shape = RoundedCornerShape(24.dp), color = SurfaceWhite) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(999.dp), color = container) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(accent, CircleShape),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            displayState.label,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = accent,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (snapshot?.recovering == true) {
                    PgPill("正在恢复", MintPale, TealDeep)
                } else if (snapshot?.severity != null && snapshot.severity != ConditionSeverity.NONE) {
                    PgPill(severityLabel(snapshot.severity), container, accent)
                }
            }
            Text(
                qualityDescription(state, snapshot),
                style = MaterialTheme.typography.bodySmall,
                color = InkSoft,
            )
            PgDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PgMetricTile(
                    label = "头肩偏移",
                    value = deltas?.let { formatDecimal(it.forwardHead, 2) } ?: "—",
                    unit = "",
                    accent = levelColor(snapshot, 0),
                    modifier = Modifier.weight(1f),
                )
                PgMetricTile(
                    label = "低头变化",
                    value = deltas?.takeIf { it.headDownAvailable }?.let { formatDecimal(it.headDown, 1) } ?: "—",
                    unit = "°",
                    accent = levelColor(snapshot, 1),
                    modifier = Modifier.weight(1f),
                )
                PgMetricTile(
                    label = "肩髋角度",
                    value = deltas?.takeIf { it.torsoAvailable }?.let { formatDecimal(it.torsoLean, 1) } ?: "—",
                    unit = "°",
                    accent = levelColor(snapshot, 2),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                when {
                    deltas == null -> "数值为相对个人校准基准的变化量，仅用于自我观察。"
                    !deltas.torsoAvailable && !deltas.headDownAvailable ->
                        "未拍到髋部、未检测到眼睛和嘴部时，仅用眼睛、鼻子、耳朵与头肩偏移判断前倾；低头与驼背/弯腰暂不判断。"
                    !deltas.torsoAvailable ->
                        "未拍到髋部时，用眼睛、鼻子、耳朵与头肩偏移判断前倾，用面部轴线判断低头；驼背/弯腰（肩髋角度）暂不判断。"
                    !deltas.headDownAvailable ->
                        "未检测到眼睛和嘴部时，暂不判断低头；前倾与驼背/弯腰正常判断。"
                    else -> "前倾看眼睛、鼻子、耳朵相对肩膀的偏移；低头看眼睛-鼻子-嘴部；驼背/弯腰看肩膀与髋部的垂直角度。数值为相对校准基准的变化量。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft,
            )
        }
    }
}

private fun qualityDescription(state: MonitorState, snapshot: com.example.postureguard.posture.model.PostureSnapshot?): String {
    if (state.phase == MonitorPhase.PAUSED) return "相机与模型已释放，继续后恢复本地检测。"
    if (snapshot == null) return "正在等待相机帧与姿态模型…"
    if (!snapshot.metricsValid) {
        if (QualityIssue.PERSON_NOT_FOUND in snapshot.quality.issues) {
            return "未检测到人体。请回到座位，并让头、肩进入画面。"
        }
        val issues = snapshot.quality.issues.joinToString(" · ") { it.label }
        return if (issues.isNotEmpty()) "$issues，暂不累计姿态时长。" else "关键点质量不足，暂不累计姿态时长。"
    }
    return when {
        snapshot.recovering -> "正在恢复，继续保持自然坐姿。"
        snapshot.state.isConfirmedAbnormal ->
            "已持续 ${snapshot.heldMs / 1_000} 秒 · ${severityLabel(snapshot.severity)}"
        snapshot.state.isAbnormal ->
            "观察中 ${snapshot.heldMs / 1_000} 秒，持续达到阈值后会提醒。"
        else -> "关键点稳定，正在本地分析。"
    }
}

private fun levelColor(
    snapshot: com.example.postureguard.posture.model.PostureSnapshot?,
    index: Int,
): Color {
    val severity = when (index) {
        0 -> snapshot?.levels?.forwardHead
        1 -> snapshot?.levels?.headDown
        else -> snapshot?.levels?.torsoLean
    } ?: ConditionSeverity.NONE
    return when (severity) {
        ConditionSeverity.SEVERE -> Alert
        ConditionSeverity.MILD -> Warm
        ConditionSeverity.NONE -> Ink
    }
}

@Composable
private fun FinishedContent(
    sessionId: String?,
    onExit: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenSession: (String) -> Unit,
) {
    val container = rememberAppContainer()
    val sessionFlow = remember(sessionId) {
        sessionId?.let { container.monitoringRepository.observeSession(it) } ?: flowOf(null)
    }
    val eventsFlow = remember(sessionId) {
        sessionId?.let { container.monitoringRepository.observeEvents(it) } ?: flowOf(emptyList())
    }
    val session by sessionFlow.collectAsStateWithLifecycle(initialValue = null)
    val events by eventsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    PageScaffold(
        title = "监测结束",
        subtitle = "本次记录已保存在本机",
        onBack = onExit,
    ) {
        if (session == null) {
            PgCard {
                Text("正在保存本次监测…", style = MaterialTheme.typography.bodyMedium, color = InkSoft)
            }
        } else {
            val s = session!!
            val paused = s.pausedDurationMs
            val monitored = (s.totalDurationMs - paused).coerceAtLeast(0)
            val abnormal = s.forwardHeadDurationMs + s.headDownDurationMs +
                s.headDownForwardDurationMs + s.torsoLeanDurationMs
            val maxValue = maxOf(
                s.normalDurationMs,
                s.forwardHeadDurationMs,
                s.headDownDurationMs,
                s.headDownForwardDurationMs,
                s.torsoLeanDurationMs,
                s.lowConfidenceDurationMs,
                1L,
            )

            PgCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            formatClock(s.startedAt),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Ink,
                        )
                        Text(
                            if (s.endedAt != null) "至 ${formatClock(s.endedAt)}" else "未正常结束",
                            style = MaterialTheme.typography.bodySmall,
                            color = InkSoft,
                        )
                    }
                    PgPill(
                        text = if (s.finalized) "已结束" else "已恢复",
                        containerColor = Mist,
                        contentColor = InkSoft,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PgMetricTile(
                        label = "监测时长",
                        value = formatCompactDuration(monitored),
                        unit = "",
                        accent = Ink,
                        modifier = Modifier.weight(1.1f),
                    )
                    PgMetricTile(
                        label = "正常观察",
                        value = formatCompactDuration(s.normalDurationMs),
                        unit = "",
                        accent = Teal,
                        modifier = Modifier.weight(1.1f),
                    )
                    PgMetricTile(
                        label = "异常累计",
                        value = formatCompactDuration(abnormal),
                        unit = "",
                        accent = Alert,
                        modifier = Modifier.weight(1.1f),
                    )
                }
                PgDivider()
                PgInfoRow("异常事件", "${events.size} 次")
                PgInfoRow("有效检测时长", formatCompactDuration(s.validDurationMs))
            }

            PgCard {
                PgSectionTitle("本次分布")
                PgBarRow("正常", s.normalDurationMs, maxValue, Teal)
                PgBarRow("头部前倾", s.forwardHeadDurationMs, maxValue, Warm)
                PgBarRow("低头", s.headDownDurationMs, maxValue, Lavender)
                PgBarRow("埋头", s.headDownForwardDurationMs, maxValue, Alert)
                PgBarRow("驼背/弯腰", s.torsoLeanDurationMs, maxValue, TealDeep)
                PgBarRow("低质量/离开画面", s.lowConfidenceDurationMs, maxValue, InkSoft)
                if (paused > 0) {
                    PgInfoRow("暂停时长", formatCompactDuration(paused))
                }
            }

            PgPrimaryButton(text = "完成", onClick = onExit)
            PgQuietButton(text = "查看统计", onClick = onOpenReports)
            if (sessionId != null && events.isNotEmpty()) {
                TextButton(onClick = { onOpenSession(sessionId) }) {
                    Text("查看本次事件明细", color = TealDeep)
                }
            }
            Text(
                "不保存视频、截图或原始画面。事件明细只包含时间与姿态指标。",
                style = MaterialTheme.typography.labelSmall,
                color = InkSoft,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
