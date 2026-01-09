package com.campilot.core.camera

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.SurfaceHolder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface LiveViewState {
    data object Idle : LiveViewState
    data object Preparing : LiveViewState
    data class Streaming(val fps: Int, val lastFrameTimestamp: Long) : LiveViewState
    data class Error(val reason: String) : LiveViewState
}

/**
 * 负责管理 Live View Surface 生命周期，并在没有真实相机流的情况下生成调试画面。
 * 一旦底层 USB/PTP 推流实现就绪，可以在此处接入真实帧数据。
 */
@Singleton
class LiveViewStreamer @Inject constructor(
    private val connectionManager: CameraConnectionManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutableState = MutableStateFlow<LiveViewState>(LiveViewState.Idle)
    val state: StateFlow<LiveViewState> = mutableState.asStateFlow()

    private var surfaceHolder: SurfaceHolder? = null
    private var renderJob: Job? = null

    private val backgroundPaint = Paint().apply { style = Paint.Style.FILL }
    private val gridPaint = Paint().apply {
        color = Color.argb(120, 255, 255, 255)
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }
    private val horizonPaint = Paint().apply {
        color = Color.argb(200, 33, 150, 243)
        strokeWidth = 8f
    }
    private val indicatorPaint = Paint().apply {
        color = Color.argb(200, 255, 193, 7)
        style = Paint.Style.FILL
    }

    fun attachSurface(holder: SurfaceHolder) {
        surfaceHolder = holder
        mutableState.value = LiveViewState.Preparing
        startRenderLoopIfNeeded()
    }

    fun detachSurface(holder: SurfaceHolder?) {
        if (surfaceHolder == holder || holder == null) {
            surfaceHolder = null
            stopRenderLoop()
            mutableState.value = LiveViewState.Idle
        }
    }

    private fun startRenderLoopIfNeeded() {
        if (renderJob?.isActive == true) return
        renderJob = scope.launch {
            var frameIndex = 0L
            while (isActive) {
                val holder = surfaceHolder ?: break
                val cameraState = connectionManager.state.value
                if (cameraState is CameraState.Error) {
                    mutableState.value = LiveViewState.Error(cameraState.reason)
                    delay(250)
                    continue
                }
                if (cameraState == CameraState.Disconnected) {
                    mutableState.value = LiveViewState.Error("相机未连接")
                    delay(250)
                    continue
                }
                drawSyntheticFrame(holder, frameIndex)
                frameIndex++
                mutableState.value = LiveViewState.Streaming(
                    fps = 30,
                    lastFrameTimestamp = System.currentTimeMillis()
                )
                delay(33L)
            }
            mutableState.value = if (surfaceHolder == null) {
                LiveViewState.Idle
            } else {
                LiveViewState.Preparing
            }
        }
    }

    private fun stopRenderLoop() {
        renderJob?.cancel()
        renderJob = null
    }

    private fun drawSyntheticFrame(holder: SurfaceHolder, frameIndex: Long) {
        var canvas: Canvas? = null
        try {
            canvas = holder.lockCanvas()
            if (canvas == null) return
            canvas.drawBackground(frameIndex)
            canvas.drawRuleOfThirds()
            canvas.drawHorizon(frameIndex)
            canvas.drawFocusIndicator(frameIndex)
        } catch (t: Throwable) {
            mutableState.value = LiveViewState.Error(t.message ?: "Live View 渲染失败")
        } finally {
            canvas?.let(holder::unlockCanvasAndPost)
        }
    }

    private fun Canvas.drawBackground(frameIndex: Long) {
        val hue = ((frameIndex % 360).toInt())
        val color = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.45f, 0.35f))
        backgroundPaint.color = color
        drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
    }

    private fun Canvas.drawRuleOfThirds() {
        val thirdWidth = width / 3f
        val thirdHeight = height / 3f
        for (i in 1..2) {
            drawLine(thirdWidth * i, 0f, thirdWidth * i, height.toFloat(), gridPaint)
            drawLine(0f, thirdHeight * i, width.toFloat(), thirdHeight * i, gridPaint)
        }
    }

    private fun Canvas.drawHorizon(frameIndex: Long) {
        val normalized = (frameIndex % 120) / 120f
        val offset = (normalized - 0.5f) * height * 0.2f
        val centerY = height / 2f + offset
        drawLine(0f, centerY, width.toFloat(), centerY, horizonPaint)
    }

    private fun Canvas.drawFocusIndicator(frameIndex: Long) {
        val rectSize = width.coerceAtMost(height) * 0.18f
        val padding = 24f
        val travel = (width - rectSize - padding * 2).coerceAtLeast(1f)
        val progress = ((frameIndex % 180) / 180f)
        val left = padding + travel * progress
        val top = height / 2f - rectSize / 2f
        val rect = RectF(left, top, left + rectSize, top + rectSize)
        drawRoundRect(rect, 24f, 24f, indicatorPaint)
    }
}
