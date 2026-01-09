package com.campilot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.campilot.core.camera.CameraState

@Composable
fun CameraStatusIndicator(
    state: CameraState,
    modifier: Modifier = Modifier
) {
    val (icon, tint) = when (state) {
        CameraState.Disconnected -> Icons.Filled.LinkOff to MaterialTheme.colorScheme.outline
        CameraState.Connecting -> Icons.Filled.HourglassEmpty to MaterialTheme.colorScheme.tertiary
        CameraState.Connected -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        CameraState.Busy -> Icons.Filled.PhotoCamera to MaterialTheme.colorScheme.secondary
        is CameraState.Error -> Icons.Filled.Error to MaterialTheme.colorScheme.error
    }

    val rotation = if (state == CameraState.Busy) 10f else 0f

    StatusIcon(
        modifier = modifier,
        icon = icon,
        tint = tint,
        contentDescription = when (state) {
            CameraState.Disconnected -> "未连接"
            CameraState.Connecting -> "连接中"
            CameraState.Connected -> "已连接"
            CameraState.Busy -> "拍摄中"
            is CameraState.Error -> "异常"
        },
        rotation = rotation
    )
}

@Composable
private fun StatusIcon(
    modifier: Modifier,
    icon: ImageVector,
    tint: Color,
    contentDescription: String,
    rotation: Float
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.rotate(rotation)
        )
    }
}
