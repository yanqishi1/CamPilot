package com.campilot.feature.timelapse

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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TimelapsePlan(
    val intervalSeconds: Int,
    val durationMinutes: Int
)

data class TimelapseState(
    val active: Boolean,
    val capturedFrames: Int,
    val plannedFrames: Int,
    val lastError: String?
)

@Singleton
class TimelapseController @Inject constructor(
    private val cameraConnectionManager: CameraConnectionManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val mutableState = MutableStateFlow(TimelapseState(false, 0, 0, null))
    val state: Flow<TimelapseState> = mutableState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private var activeJob: Job? = null

    suspend fun start(plan: TimelapsePlan) {
        if (activeJob?.isActive == true) return
        if (cameraConnectionManager.state.value != CameraState.Connected) {
            mutableState.value = TimelapseState(false, 0, 0, "相机未连接，请确认 Type-C 线缆")
            return
        }
        val frames = (plan.durationMinutes * 60) / plan.intervalSeconds
        if (frames <= 0) {
            mutableState.value = TimelapseState(false, 0, 0, "计划参数不合法")
            return
        }
        mutableState.value = TimelapseState(active = true, capturedFrames = 0, plannedFrames = frames, lastError = null)
        activeJob = scope.launch {
            try {
                repeat(frames) { index ->
                    ensureConnectedOrAbort()
                    triggerCapture(index + 1)
                    if (!isActive) return@launch
                    if (index < frames - 1) {
                        delay(plan.intervalSeconds * 1_000L)
                    }
                }
                mutableState.value = TimelapseState(false, frames, frames, null)
            } catch (cancellation: CancellationException) {
                mutableState.value = TimelapseState(false, 0, 0, "延时任务已取消")
                throw cancellation
            } catch (t: Throwable) {
                mutableState.value = TimelapseState(false, 0, 0, t.message ?: "延时拍摄失败")
            } finally {
                cameraConnectionManager.markIdle()
                activeJob = null
            }
        }
    }

    fun stop() {
        activeJob?.cancel()
        activeJob = null
    }

    private suspend fun triggerCapture(frameNumber: Int) {
        cameraConnectionManager.markBusy()
        // TODO: 替换为实际快门触发命令。
        delay(400)
        mutableState.value = mutableState.value.copy(capturedFrames = frameNumber)
        cameraConnectionManager.markIdle()
    }

    private fun ensureConnectedOrAbort() {
        when (cameraConnectionManager.state.value) {
            CameraState.Disconnected,
            is CameraState.Error -> throw IllegalStateException("相机 Type-C 连接已断开")
            else -> Unit
        }
    }
}
