package com.campilot.core.camera

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 管理 Sony 相机的 USB 连接、权限申请与状态机输出。
 */
@Singleton
class CameraConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usbManager: UsbManager,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    companion object {
        private const val SONY_VENDOR_ID = 0x054C
    }

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val permissionAction = "${context.packageName}.USB_PERMISSION"
    private val permissionIntent: PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(permissionAction),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private val mutableState = MutableStateFlow<CameraState>(CameraState.Disconnected)
    val state: StateFlow<CameraState> = mutableState.asStateFlow()

    private var connectedDevice: UsbDevice? = null
    private var deviceConnection: UsbDeviceConnection? = null
    private var targetCamera: SupportedCamera? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val device = intent.getUsbDevice()
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> device?.let { handleDeviceAttached(it) }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> device?.let { handleDeviceDetached(it) }
                permissionAction -> handlePermissionResult(
                    device = device,
                    granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                )
            }
        }
    }

    init {
        registerReceiver()
        // 如果启动 App 前就插着 Sony 相机且已有权限，自动恢复连接。
        scope.launch {
            usbManager.deviceList.values
                .firstOrNull { supportedCameraFor(it) != null && usbManager.hasPermission(it) }
                ?.let { establishConnection(it) }
        }
    }

    suspend fun connect(camera: SupportedCamera) {
        targetCamera = camera
        withContext(dispatcher) {
            if (mutableState.value == CameraState.Connected) return@withContext
            val device = findDevice(camera)
            if (device == null) {
                mutableState.value = CameraState.Error("未检测到 ${camera.modelName}，请确认 Type-C 线缆连接")
                return@withContext
            }
            if (!usbManager.hasPermission(device)) {
                mutableState.value = CameraState.Connecting
                usbManager.requestPermission(device, permissionIntent)
            } else {
                establishConnection(device)
            }
        }
    }

    suspend fun disconnect() {
        withContext(dispatcher) {
            targetCamera = null
            closeConnection()
            mutableState.value = CameraState.Disconnected
        }
    }

    suspend fun markBusy() {
        withContext(dispatcher) {
            if (mutableState.value == CameraState.Connected) {
                mutableState.value = CameraState.Busy
            }
        }
    }

    suspend fun markIdle() {
        withContext(dispatcher) {
            if (mutableState.value == CameraState.Busy) {
                mutableState.value = CameraState.Connected
            }
        }
    }

    suspend fun setError(reason: String) {
        withContext(dispatcher) {
            mutableState.value = CameraState.Error(reason)
        }
    }

    private suspend fun establishConnection(device: UsbDevice) {
        withContext(dispatcher) {
            mutableState.value = CameraState.Connecting
            runCatching {
                closeConnection()
                deviceConnection = usbManager.openDevice(device)
                    ?: error("打开 USB 设备失败")
                connectedDevice = device
                delay(300) // 预留 PTP 会话建立时间。
                mutableState.value = CameraState.Connected
            }.onFailure { throwable ->
                connectedDevice = null
                mutableState.value = CameraState.Error(throwable.message ?: "连接失败")
            }
        }
    }

    private fun handleDeviceAttached(device: UsbDevice) {
        val supported = supportedCameraFor(device) ?: return
        if (targetCamera == null) {
            targetCamera = supported
        }
        if (!usbManager.hasPermission(device)) {
            mutableState.value = CameraState.Connecting
            usbManager.requestPermission(device, permissionIntent)
        } else if (mutableState.value == CameraState.Disconnected) {
            scope.launch { establishConnection(device) }
        }
    }

    private fun handleDeviceDetached(device: UsbDevice) {
        if (connectedDevice?.deviceId == device.deviceId) {
            scope.launch {
                closeConnection()
                mutableState.value = CameraState.Disconnected
            }
        }
    }

    private fun handlePermissionResult(device: UsbDevice?, granted: Boolean) {
        val target = device ?: return
        if (supportedCameraFor(target) == null) return
        if (granted) {
            scope.launch { establishConnection(target) }
        } else {
            mutableState.value = CameraState.Error("USB 权限被拒绝")
        }
    }

    private fun findDevice(camera: SupportedCamera): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull { matchesCamera(it, camera) }
    }

    private fun supportedCameraFor(device: UsbDevice): SupportedCamera? {
        if (device.vendorId != SONY_VENDOR_ID) return null
        return SupportedCamera.entries.firstOrNull { matchesCamera(device, it) }
    }

    private fun matchesCamera(device: UsbDevice, camera: SupportedCamera): Boolean {
        if (device.vendorId != SONY_VENDOR_ID) return false
        val productName = device.productName?.uppercase()?.trim() ?: return false
        return camera.usbProductAliases.any { alias ->
            productName.contains(alias.uppercase())
        }
    }

    private fun closeConnection() {
        deviceConnection?.close()
        deviceConnection = null
        connectedDevice = null
    }

    private fun Intent.getUsbDevice(): UsbDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
    }

    private fun registerReceiver() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(permissionAction)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(usbReceiver, filter)
        }
    }
}
