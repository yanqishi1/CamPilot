package com.campilot.feature.home.ui

import androidx.compose.foundation.background
import android.graphics.Color as AndroidColor
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.campilot.core.ai.CompositionHint
import com.campilot.core.ai.ExposureRecommendation
import com.campilot.core.ai.HintSeverity
import com.campilot.core.camera.CameraState
import com.campilot.core.camera.LiveViewState
import com.campilot.feature.home.HomeUiState
import com.campilot.feature.hdr.HdrWorkflowState
import com.campilot.feature.timelapse.TimelapseState
import com.campilot.ui.components.CameraStatusIndicator
import com.campilot.ui.theme.CamPilotTheme

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    state: HomeUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefreshAi: () -> Unit,
    onStartHdr: () -> Unit,
    onStopHdr: () -> Unit,
    onStartTimelapse: () -> Unit,
    onStopTimelapse: () -> Unit,
    onSurfaceAvailable: (SurfaceHolder) -> Unit,
    onSurfaceDestroyed: (SurfaceHolder) -> Unit
) {
    val isConnected = state.cameraState == CameraState.Connected || state.cameraState == CameraState.Busy
    val fabText = if (isConnected) "断开相机" else "连接 A7CⅡ"
    val fabAction = if (isConnected) onDisconnect else onConnect
    Scaffold(
        modifier = modifier,
        topBar = {
            CamPilotTopBar(
                cameraState = state.cameraState,
                onRefreshAi = onRefreshAi
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = fabAction,
                icon = {
                    Icon(
                        imageVector = if (isConnected) Icons.Default.Cameraswitch else Icons.Default.Link,
                        contentDescription = null
                    )
                },
                text = { Text(fabText) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LiveViewPreview(
                cameraState = state.cameraState,
                liveViewState = state.liveViewState,
                onSurfaceAvailable = onSurfaceAvailable,
                onSurfaceDestroyed = onSurfaceDestroyed
            )
            RecommendationCard(
                recommendation = state.exposureRecommendation,
                hints = state.compositionHints
            )
            WorkflowCard(
                title = "自动 HDR",
                description = "曝光包围 + 智能融合",
                state = WorkflowUiState(
                    active = state.hdrState.active,
                    progressLabel = "${(state.hdrState.progress * 100).toInt()}%",
                    message = state.hdrState.lastError
                ),
                actionLabel = "开始 HDR",
                onAction = onStartHdr,
                onStop = onStopHdr
            )
            WorkflowCard(
                title = "延时摄影",
                description = "基础间隔拍摄",
                state = WorkflowUiState(
                    active = state.timelapseState.active,
                    progressLabel = "${state.timelapseState.capturedFrames}/${state.timelapseState.plannedFrames}",
                    message = state.timelapseState.lastError
                ),
                actionLabel = "开始延时",
                onAction = onStartTimelapse,
                onStop = onStopTimelapse
            )
        }
    }
}

@Composable
fun CamPilotTopBar(
    cameraState: CameraState,
    onRefreshAi: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(text = "CamPilot", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Sony USB 控制",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        actions = {
            IconButton(onClick = onRefreshAi) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "刷新 AI")
            }
            CameraStatusIndicator(
                state = cameraState,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    )
}

@Composable
private fun LiveViewPreview(
    cameraState: CameraState,
    liveViewState: LiveViewState,
    onSurfaceAvailable: (SurfaceHolder) -> Unit,
    onSurfaceDestroyed: (SurfaceHolder) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSurfaceAvailable by rememberUpdatedState(onSurfaceAvailable)
    val currentSurfaceDestroyed by rememberUpdatedState(onSurfaceDestroyed)
    val overlayState = liveViewOverlayState(
        cameraState = cameraState,
        liveViewState = liveViewState
    )
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .height(240.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    SurfaceView(context).apply {
                        setBackgroundColor(AndroidColor.BLACK)
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                currentSurfaceAvailable(holder)
                            }

                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                currentSurfaceAvailable(holder)
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                currentSurfaceDestroyed(holder)
                            }
                        })
                    }
                }
            )
            LiveViewStatusPill(
                cameraState = cameraState,
                label = overlayState.statusLabel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
            )
            if (overlayState.blockingOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(overlayState.overlayColor.copy(alpha = overlayState.overlayAlpha)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = overlayState.overlayText,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Immutable
private data class LiveViewOverlayState(
    val overlayText: String,
    val overlayColor: Color,
    val overlayAlpha: Float,
    val blockingOverlay: Boolean,
    val statusLabel: String
)

@Composable
private fun liveViewOverlayState(
    cameraState: CameraState,
    liveViewState: LiveViewState
): LiveViewOverlayState {
    val overlayColor = when {
        cameraState is CameraState.Error -> MaterialTheme.colorScheme.errorContainer
        liveViewState is LiveViewState.Error -> MaterialTheme.colorScheme.errorContainer
        cameraState == CameraState.Disconnected -> Color(0xFF2D2D2D)
        cameraState == CameraState.Connecting -> Color(0xFF424242)
        cameraState == CameraState.Busy -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val (text, blocking) = when {
        cameraState is CameraState.Error -> cameraState.reason to true
        liveViewState is LiveViewState.Error -> liveViewState.reason to true
        cameraState == CameraState.Disconnected -> "等待连接 Sony A7CⅡ" to true
        cameraState == CameraState.Connecting -> "正在建立 USB 会话" to true
        liveViewState is LiveViewState.Preparing -> "Live View 初始化中" to true
        cameraState == CameraState.Busy -> "拍摄中..." to false
        liveViewState is LiveViewState.Streaming -> "" to false
        else -> "Live View 待命" to true
    }
    val statusLabel = when {
        cameraState is CameraState.Error -> "连接异常"
        liveViewState is LiveViewState.Error -> "预览异常"
        liveViewState is LiveViewState.Streaming -> "Live View ${liveViewState.fps}fps"
        liveViewState is LiveViewState.Preparing -> "预览准备"
        cameraState == CameraState.Busy -> "拍摄中"
        cameraState == CameraState.Connecting -> "连接中"
        cameraState == CameraState.Disconnected -> "未连接"
        else -> "预览待命"
    }
    return LiveViewOverlayState(
        overlayText = text,
        overlayColor = overlayColor,
        overlayAlpha = if (blocking) 0.85f else 0.35f,
        blockingOverlay = blocking && text.isNotEmpty(),
        statusLabel = statusLabel
    )
}

@Composable
private fun LiveViewStatusPill(
    cameraState: CameraState,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 3.dp,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CameraStatusIndicator(
                state = cameraState,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: ExposureRecommendation?,
    hints: List<CompositionHint>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = "AI 参数推荐", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                RecommendationStat(label = "ISO", value = recommendation?.iso?.toString() ?: "--")
                RecommendationStat(label = "快门", value = recommendation?.shutterSpeed ?: "--")
                RecommendationStat(label = "光圈", value = recommendation?.aperture ?: "--")
            }
            Text(
                text = "构图提示",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            if (hints.isEmpty()) {
                Text(text = "暂无提示", color = MaterialTheme.colorScheme.outline)
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(hints) { hint ->
                        AssistChip(onClick = {}, label = { Text(hint.description) })
                    }
                }
            }
        }
    }
}

@Composable
private fun RecommendationStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        Text(text = value, style = MaterialTheme.typography.titleLarge)
    }
}

@Immutable
data class WorkflowUiState(
    val active: Boolean,
    val progressLabel: String,
    val message: String?
)

@Composable
private fun WorkflowCard(
    title: String,
    description: String,
    state: WorkflowUiState,
    actionLabel: String,
    onAction: () -> Unit,
    onStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleMedium)
                    Text(text = description, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    text = if (state.active) "进行中 ${state.progressLabel}" else state.progressLabel,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onAction, enabled = !state.active) {
                    Text(text = actionLabel)
                }
                if (state.active && onStop != null) {
                    OutlinedButton(onClick = onStop) {
                        Text(text = "停止")
                    }
                }
            }
            if (state.message != null) {
                Text(text = state.message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewHomeScreen() {
    CamPilotTheme {
        HomeScreen(
            state = HomeUiState(
                cameraState = CameraState.Connected,
                exposureRecommendation = ExposureRecommendation(iso = 200, shutterSpeed = "1/60", aperture = "f/4.0", confidence = 0.8f),
                compositionHints = listOf(CompositionHint("偏暗，建议调高曝光", com.campilot.core.ai.HintSeverity.INFO)),
                hdrState = HdrWorkflowState(true, 0.5f, null),
                timelapseState = TimelapseState(true, 12, 120, null),
                liveViewState = LiveViewState.Streaming(fps = 30, lastFrameTimestamp = System.currentTimeMillis())
            ),
            onConnect = {},
            onDisconnect = {},
            onRefreshAi = {},
            onStartHdr = {},
            onStopHdr = {},
            onStartTimelapse = {},
            onStopTimelapse = {},
            onSurfaceAvailable = {},
            onSurfaceDestroyed = {}
        )
    }
}
