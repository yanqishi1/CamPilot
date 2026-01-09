package com.campilot.feature.hdr

import com.campilot.core.camera.CameraConnectionManager
import com.campilot.core.camera.CameraState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HdrBracketRequest(
    val shots: Int,
    val prioritizeHighlights: Boolean
)

data class HdrWorkflowState(
    val active: Boolean,
    val progress: Float,
    val lastError: String?
)

@Singleton
class HdrWorkflowController @Inject constructor(
    private val connectionManager: CameraConnectionManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val mutableState = MutableStateFlow(HdrWorkflowState(active = false, progress = 0f, lastError = null))
    val state: Flow<HdrWorkflowState> = mutableState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var activeJob: Job? = null

    suspend fun start(request: HdrBracketRequest) {
        if (activeJob?.isActive == true) return
        if (connectionManager.state.value != CameraState.Connected) {
            mutableState.value = HdrWorkflowState(false, 0f, "相机未连接，请检查 Type-C 连接")
            return
        }
        val shots = request.shots.coerceIn(3, 9)
        mutableState.value = HdrWorkflowState(true, 0f, null)
        activeJob = scope.launch {
            try {
                repeat(shots) { index ->
                    triggerBracketExposure(index, shots, request.prioritizeHighlights)
                }
                mutableState.value = HdrWorkflowState(false, 1f, null)
            } catch (cancellation: CancellationException) {
                mutableState.value = HdrWorkflowState(false, 0f, "HDR 已取消")
                throw cancellation
            } catch (t: Throwable) {
                mutableState.value = HdrWorkflowState(false, 0f, t.message ?: "HDR 执行失败")
            } finally {
                connectionManager.markIdle()
                activeJob = null
            }
        }
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
    }

    private suspend fun triggerBracketExposure(
        index: Int,
        totalShots: Int,
        prioritizeHighlights: Boolean
    ) {
        when (connectionManager.state.value) {
            CameraState.Disconnected,
            is CameraState.Error -> throw IllegalStateException("相机 Type-C 连接异常")
            else -> Unit
        }
        connectionManager.markBusy()
        // TODO: 调整实际曝光值并触发快门。
        val simulatedShotDelay = if (prioritizeHighlights) 550L else 400L
        delay(simulatedShotDelay)
        val progress = (index + 1) / totalShots.toFloat()
        mutableState.value = HdrWorkflowState(active = true, progress = progress, lastError = null)
        connectionManager.markIdle()
    }
}
