package com.campilot.core.camera

sealed interface CameraState {
    data object Disconnected : CameraState
    data object Connecting : CameraState
    data object Connected : CameraState
    data object Busy : CameraState
    data class Error(val reason: String) : CameraState
}

enum class SupportedCamera(
    val modelName: String,
    val usbProductAliases: List<String>
) {
    A7C2(
        modelName = "Sony A7C2",
        usbProductAliases = listOf("ILCE-7CM2", "ILCE-7C2", "A7CII", "A7C2")
    ),
    A7IV(
        modelName = "Sony A7 IV",
        usbProductAliases = listOf("ILCE-7M4", "A7M4", "A7IV", "A7 IV")
    ),
    A7RV(
        modelName = "Sony A7R V",
        usbProductAliases = listOf("ILCE-7RM5", "A7RM5", "A7RV", "A7R V")
    ),
    A6700(
        modelName = "Sony A6700",
        usbProductAliases = listOf("ILCE-6700", "A6700")
    )
}
