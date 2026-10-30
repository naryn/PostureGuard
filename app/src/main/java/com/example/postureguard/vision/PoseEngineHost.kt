package com.example.postureguard.vision

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

sealed interface PoseEngineState {
    data object Loading : PoseEngineState
    data class Ready(val engine: PoseLandmarkerEngine) : PoseEngineState
    data class Unavailable(val message: String) : PoseEngineState
}

/**
 * Loads the landmarker on a dedicated thread and guarantees it is closed even if the UI
 * is disposed while the model is still loading.
 */
class PoseEngineHost(context: Context) {
    private val appContext = context.applicationContext
    private val engineRef = AtomicReference<PoseLandmarkerEngine?>(null)
    private val closed = AtomicBoolean(false)
    private val mutableState = MutableStateFlow<PoseEngineState>(PoseEngineState.Loading)

    val state: StateFlow<PoseEngineState> = mutableState.asStateFlow()

    fun load() {
        Thread(
            {
                val result = PoseLandmarkerEngine.create(appContext)
                if (closed.get()) {
                    result.getOrNull()?.close()
                    return@Thread
                }
                result.fold(
                    onSuccess = { engine ->
                        engineRef.set(engine)
                        mutableState.value = PoseEngineState.Ready(engine)
                    },
                    onFailure = { error ->
                        mutableState.value = PoseEngineState.Unavailable(
                            error.message ?: "姿态模型加载失败",
                        )
                    },
                )
            },
            "pose-engine-init",
        ).start()
    }

    fun close() {
        if (closed.compareAndSet(false, true)) {
            engineRef.getAndSet(null)?.close()
        }
    }
}
