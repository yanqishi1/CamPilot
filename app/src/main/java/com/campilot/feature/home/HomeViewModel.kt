package com.campilot.feature.home

import android.view.SurfaceHolder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campilot.core.ai.AiRecommendationEngine
import com.campilot.core.ai.CompositionHint
import com.campilot.core.ai.ExposureRecommendation
import com.campilot.core.camera.CameraConnectionManager
import com.campilot.core.camera.CameraState
import com.campilot.core.camera.LiveViewState
import com.campilot.core.camera.LiveViewStreamer
import com.campilot.core.camera.SupportedCamera
import com.campilot.feature.hdr.HdrBracketRequest
import com.campilot.feature.hdr.HdrWorkflowController
import com.campilot.feature.hdr.HdrWorkflowState
import com.campilot.feature.timelapse.TimelapseController
import com.campilot.feature.timelapse.TimelapsePlan
import com.campilot.feature.timelapse.TimelapseState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val cameraState: CameraState = CameraState.Disconnected,
    val exposureRecommendation: ExposureRecommendation? = null,
    val compositionHints: List<CompositionHint> = emptyList(),
    val hdrState: HdrWorkflowState = HdrWorkflowState(false, 0f, null),
    val timelapseState: TimelapseState = TimelapseState(false, 0, 0, null),
    val liveViewState: LiveViewState = LiveViewState.Idle
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val cameraConnectionManager: CameraConnectionManager,
    private val aiRecommendationEngine: AiRecommendationEngine,
    private val hdrWorkflowController: HdrWorkflowController,
    private val timelapseController: TimelapseController,
    private val liveViewStreamer: LiveViewStreamer
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        cameraConnectionManager.state,
        aiRecommendationEngine.recommendations(),
        aiRecommendationEngine.compositionHints(),
        hdrWorkflowController.state,
        timelapseController.state,
        liveViewStreamer.state
    ) { cameraState, exposure, hints, hdrState, timelapseState, liveViewState ->
        HomeUiState(
            cameraState = cameraState,
            exposureRecommendation = exposure,
            compositionHints = hints,
            hdrState = hdrState,
            timelapseState = timelapseState,
            liveViewState = liveViewState
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    init {
        aiRecommendationEngine.refresh()
    }

    fun connectToPrimaryCamera() {
        viewModelScope.launch {
            cameraConnectionManager.connect(SupportedCamera.A7C2)
        }
    }

    fun disconnectCamera() {
        viewModelScope.launch {
            hdrWorkflowController.stop()
            timelapseController.stop()
            cameraConnectionManager.disconnect()
        }
    }

    fun refreshRecommendations() {
        aiRecommendationEngine.refresh()
    }

    fun triggerHdrSequence() {
        viewModelScope.launch {
            hdrWorkflowController.start(HdrBracketRequest(shots = 3, prioritizeHighlights = true))
        }
    }

    fun stopHdrSequence() {
        hdrWorkflowController.stop()
    }

    fun triggerTimelapseDemo() {
        viewModelScope.launch {
            timelapseController.start(TimelapsePlan(intervalSeconds = 5, durationMinutes = 1))
        }
    }

    fun stopTimelapse() {
        timelapseController.stop()
    }

    fun attachLiveViewSurface(holder: SurfaceHolder) {
        liveViewStreamer.attachSurface(holder)
    }

    fun detachLiveViewSurface(holder: SurfaceHolder) {
        liveViewStreamer.detachSurface(holder)
    }
}
