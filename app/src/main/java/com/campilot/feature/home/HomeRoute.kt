package com.campilot.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campilot.feature.home.ui.HomeScreen

@Composable
fun HomeRoute(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        modifier = modifier,
        state = uiState,
        onConnect = viewModel::connectToPrimaryCamera,
        onDisconnect = viewModel::disconnectCamera,
        onRefreshAi = viewModel::refreshRecommendations,
        onStartHdr = viewModel::triggerHdrSequence,
        onStopHdr = viewModel::stopHdrSequence,
        onStartTimelapse = viewModel::triggerTimelapseDemo,
        onStopTimelapse = viewModel::stopTimelapse,
        onSurfaceAvailable = viewModel::attachLiveViewSurface,
        onSurfaceDestroyed = viewModel::detachLiveViewSurface
    )
}
