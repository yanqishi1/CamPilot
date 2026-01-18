package com.campilot

import android.Manifest
import android.content.ContentValues
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.campilot.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageCapture: ImageCapture? = null
    private var activeCamera: Camera? = null

    private var selectedCameraId: String? = null
    private var selectedZoomRatio: Float? = null
    private var lensItems: List<LensItem> = emptyList()

    private var isMultiExposureMode = false
    private var isReviewingMultiExposure = false
    private var exposureCount = 2
    private var exposureWeights: MutableList<Float> = mutableListOf(0.5f, 0.9f)
    private var capturedBitmaps: MutableList<Bitmap> = mutableListOf()
    private var pendingMultiExposureResult: Bitmap? = null
    private val logBuffer: MutableList<String> = mutableListOf()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            toast("需要相机权限才能使用")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.menuCamera.setOnClickListener { showCameraMenu(it) }
        binding.menuLens.setOnClickListener { showLensMenu(it) }
        binding.btnMode.setOnClickListener { showModeMenu(it) }
        bindGalleryButton()
        binding.btnShutter.setOnClickListener { onShutterPressed() }
        binding.btnDebug.setOnClickListener {
            if (isReviewingMultiExposure) {
                discardMultiExposureResult()
            } else {
                showDebugLog()
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        logEvent("应用启动")
    }

    override fun onResume() {
        super.onResume()
        bindGalleryButton()
    }

    private fun bindGalleryButton() {
        binding.btnGallery.setOnClickListener {
            if (isReviewingMultiExposure) {
                saveMultiExposureResult()
            } else {
                openSystemGallery()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            lensItems = loadLensItems()
            val selected = lensItems.firstOrNull {
                it.id == selectedCameraId && it.zoomRatio == selectedZoomRatio
            } ?: lensItems.firstOrNull()
            if (selected != null) {
                selectedCameraId = selected.id
                selectedZoomRatio = selected.zoomRatio
                binding.menuLens.text = "镜头：${selected.name}"
            }
            bindUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindUseCases() {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder().build().apply {
            setSurfaceProvider(binding.viewFinder.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val selector = selectedCameraId?.let { cameraId ->
            CameraSelector.Builder()
                .addCameraFilter { cameras ->
                    cameras.filter {
                        Camera2CameraInfo.from(it).cameraId == cameraId
                    }
                }
                .build()
        } ?: CameraSelector.DEFAULT_BACK_CAMERA

        provider.unbindAll()
        activeCamera = provider.bindToLifecycle(this, selector, preview, imageCapture)
        applyZoomRatioIfAvailable()
        logEvent("相机绑定完成")
    }

    private fun applyZoomRatioIfAvailable() {
        val ratio = selectedZoomRatio ?: return
        val camera = activeCamera ?: return
        val zoomState = camera.cameraInfo.zoomState.value
        val clamped = if (zoomState != null) {
            ratio.coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
        } else {
            ratio
        }
        camera.cameraControl.setZoomRatio(clamped)
    }

    private fun onShutterPressed() {
        if (isReviewingMultiExposure) {
            toast("请先处理合成结果")
            return
        }
        if (isMultiExposureMode) {
            captureMultiExposureStep()
        } else {
            captureNormalPhoto()
        }
    }

    private fun captureNormalPhoto() {
        val capture = imageCapture ?: return
        val name = "CamPilot_${timestamp()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CamPilot")
            }
        }
        val outputOptions = ImageCapture.OutputFileOptions
            .Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            .build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    toast("已保存到相册")
                    logEvent("普通拍照保存成功")
                }

                override fun onError(exception: ImageCaptureException) {
                    toast("保存失败：${exception.message}")
                    logEvent("普通拍照保存失败：${exception.message}")
                }
            }
        )
    }

    private fun captureMultiExposureStep() {
        val capture = imageCapture ?: return
        val tempFile = File.createTempFile("campilot_", ".jpg", cacheDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val bitmap = decodeBitmapWithOrientation(tempFile.absolutePath)
                    tempFile.delete()
                    if (bitmap == null) {
                        toast("拍摄失败")
                        return
                    }

                    capturedBitmaps.add(bitmap)
                    val index = capturedBitmaps.size
                    if (index < exposureCount) {
                        val previewBlend = blendBitmaps(
                            capturedBitmaps,
                            exposureWeights.take(index)
                        )
                        binding.overlayView.setImageBitmap(previewBlend)
                        binding.overlayView.alpha = 0.8f
                        toast("第 $index 次曝光完成，请继续")
                        logEvent("多重曝光第 $index 次完成")
                    } else {
                        val finalBlend = blendBitmaps(capturedBitmaps, exposureWeights)
                        showMultiExposureResult(finalBlend)
                        capturedBitmaps.clear()
                        logEvent("多重曝光合成完成")
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    toast("拍摄失败：${exception.message}")
                    logEvent("多重曝光拍摄失败：${exception.message}")
                }
            }
        )
    }

    private fun showMultiExposureResult(bitmap: Bitmap) {
        pendingMultiExposureResult = bitmap
        isReviewingMultiExposure = true
        binding.overlayView.setImageBitmap(bitmap)
        binding.overlayView.alpha = 1f
        updateBottomBarForReview(true)
    }

    private fun discardMultiExposureResult() {
        if (!isReviewingMultiExposure) return
        pendingMultiExposureResult = null
        isReviewingMultiExposure = false
        capturedBitmaps.clear()
        binding.overlayView.setImageBitmap(null)
        binding.overlayView.alpha = 0f
        updateBottomBarForReview(false)
        toast("已丢弃")
    }

    private fun saveMultiExposureResult() {
        val result = pendingMultiExposureResult ?: return
        saveBitmapToGallery(result)
        pendingMultiExposureResult = null
        isReviewingMultiExposure = false
        capturedBitmaps.clear()
        binding.overlayView.setImageBitmap(null)
        binding.overlayView.alpha = 0f
        updateBottomBarForReview(false)
    }

    private fun updateBottomBarForReview(inReview: Boolean) {
        binding.btnMode.visibility = if (inReview) View.GONE else View.VISIBLE
        binding.btnShutter.visibility = if (inReview) View.GONE else View.VISIBLE
        binding.btnDebug.text = if (inReview) "x" else getString(R.string.debug)
        binding.btnGallery.text = if (inReview) "✔" else getString(R.string.gallery)
        binding.viewFinder.visibility = if (inReview) View.INVISIBLE else View.VISIBLE
    }

    private fun decodeBitmapWithOrientation(path: String): Bitmap? {
        val bitmap = android.graphics.BitmapFactory.decodeFile(path) ?: return null
        val exif = ExifInterface(path)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        return if (matrix.isIdentity) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val name = "CamPilot_ME_${timestamp()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CamPilot")
            }
        }
        val uri: Uri? = contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        if (uri == null) {
            toast("保存失败")
            return
        }
        contentResolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            toast("多重曝光已保存到相册")
            logEvent("多重曝光保存成功")
        } ?: toast("保存失败")
    }

    private fun blendBitmaps(bitmaps: List<Bitmap>, weights: List<Float>): Bitmap {
        val base = bitmaps.first()
        val width = base.width
        val height = base.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val sum = weights.sum().takeIf { it > 0f } ?: 1f

        bitmaps.forEachIndexed { index, bitmap ->
            val weight = weights.getOrElse(index) { 1f }
            val normalized = (weight / sum).coerceIn(0f, 1f)
            val paint = Paint().apply { alpha = (255 * normalized).toInt() }
            val scaled = if (bitmap.width == width && bitmap.height == height) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, width, height, true)
            }
            canvas.drawBitmap(scaled, 0f, 0f, paint)
        }
        return result
    }

    private fun showCameraMenu(anchor: View) {
        val items = listOf("手机相机")
        showSelectionDialog("选择相机", items) { index ->
            val selected = items[index]
            binding.menuCamera.text = "相机：$selected"
            logEvent("选择相机：$selected")
        }
    }

    private fun showLensMenu(anchor: View) {
        if (lensItems.isEmpty()) {
            toast("未检测到镜头")
            return
        }
        val items = lensItems.map { it.name }
        showSelectionDialog("选择镜头", items) { index ->
            val selected = lensItems[index]
            selectedCameraId = selected.id
            selectedZoomRatio = selected.zoomRatio
            binding.menuLens.text = "镜头：${selected.name}"
            bindUseCases()
            logEvent("切换镜头：${selected.name}")
        }
    }

    private fun showModeMenu(anchor: View) {
        val items = listOf("普通", "多重曝光")
        showSelectionDialog("选择模式", items) { index ->
            val selected = items[index]
            when (selected) {
                "普通" -> {
                    isMultiExposureMode = false
                    isReviewingMultiExposure = false
                    capturedBitmaps.clear()
                    pendingMultiExposureResult = null
                    binding.overlayView.setImageBitmap(null)
                    binding.overlayView.alpha = 0f
                    updateBottomBarForReview(false)
                    toast("普通模式")
                    logEvent("切换模式：普通")
                }
                "多重曝光" -> {
                    if (isReviewingMultiExposure) {
                        discardMultiExposureResult()
                    }
                    showMultiExposureDialog()
                }
            }
        }
    }

    private fun showMultiExposureDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 0)
        }

        val countLabel = TextView(this).apply {
            text = "曝光次数"
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            textSize = 14f
        }
        val countSpinner = Spinner(this)
        val countValues = listOf(2, 3, 4, 5)
        countSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            countValues
        )
        countSpinner.setSelection(countValues.indexOf(exposureCount).coerceAtLeast(0))

        val weightLabel = TextView(this).apply {
            text = "合成强度（每次曝光）"
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            textSize = 14f
            setPadding(0, 16, 0, 0)
        }

        val weightsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        fun buildWeightSpinners(count: Int) {
            weightsContainer.removeAllViews()
            val choices = listOf("弱", "中", "强")
            repeat(count) { index ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val label = TextView(this).apply {
                    text = "第 ${index + 1} 次"
                    setPadding(0, 10, 20, 10)
                }
                val spinner = Spinner(this)
                spinner.adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    choices
                )
                val defaultIndex = when (index) {
                    0 -> 0
                    1 -> 2
                    else -> 1
                }
                spinner.setSelection(defaultIndex)
                row.addView(label)
                row.addView(spinner)
                weightsContainer.addView(row)
            }
        }

        buildWeightSpinners(exposureCount)
        countSpinner.setOnItemSelectedListener(SimpleItemSelectedListener { pos ->
            exposureCount = countValues[pos]
            buildWeightSpinners(exposureCount)
        })

        container.addView(countLabel)
        container.addView(countSpinner)
        container.addView(weightLabel)
        container.addView(weightsContainer)

        AlertDialog.Builder(this)
            .setTitle("多重曝光设置")
            .setView(container)
            .setPositiveButton("开始") { _, _ ->
                exposureWeights = buildWeightsFromContainer(weightsContainer)
                if (exposureWeights.size < exposureCount) {
                    exposureWeights = MutableList(exposureCount) { 1f }
                }
                isMultiExposureMode = true
                isReviewingMultiExposure = false
                capturedBitmaps.clear()
                pendingMultiExposureResult = null
                binding.overlayView.setImageBitmap(null)
                binding.overlayView.alpha = 0f
                updateBottomBarForReview(false)
                toast("已进入多重曝光")
                logEvent("多重曝光设置：次数=$exposureCount，权重=$exposureWeights")
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun buildWeightsFromContainer(container: LinearLayout): MutableList<Float> {
        val weights = mutableListOf<Float>()
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i) as LinearLayout
            val spinner = row.getChildAt(1) as Spinner
            val choice = spinner.selectedItem?.toString() ?: "中"
            weights.add(
                when (choice) {
                    "弱" -> 0.5f
                    "强" -> 0.9f
                    else -> 0.7f
                }
            )
        }
        return weights
    }

    private fun loadLensItems(): List<LensItem> {
        val cameraManager = getSystemService(CAMERA_SERVICE) as android.hardware.camera2.CameraManager
        val cameraIds = cameraManager.cameraIdList.toList()
        val cameraIdSet = cameraIds.toSet()
        val items = mutableListOf<LensItem>()
        val logicalGroups = mutableListOf<LogicalGroup>()

        cameraIds.forEach { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val capabilities = characteristics.get(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
            ) ?: intArrayOf()
            val physicalIds = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                characteristics.physicalCameraIds
            } else {
                emptySet()
            }
            val isLogical = capabilities.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
            )
            val hasPhysicalInList = isLogical && physicalIds.any { cameraIdSet.contains(it) }
            if (isLogical && physicalIds.isNotEmpty() && !hasPhysicalInList) {
                logicalGroups.add(LogicalGroup(id, physicalIds))
                return@forEach
            }
            if (isLogical && hasPhysicalInList) {
                return@forEach
            }
            items.add(buildLensItem(id, characteristics, null))
        }

        logicalGroups.forEach { group ->
            val physicalItems = group.physicalIds.mapNotNull { physicalId ->
                runCatching {
                    val characteristics = cameraManager.getCameraCharacteristics(physicalId)
                    buildLensItem(group.logicalId, characteristics, null)
                }.getOrNull()
            }
            if (physicalItems.isEmpty()) {
                val logicalCharacteristics = cameraManager.getCameraCharacteristics(group.logicalId)
                items.add(buildLensItem(group.logicalId, logicalCharacteristics, null))
                return@forEach
            }
            val sorted = physicalItems.sortedBy { it.sortKey }
            val baseKey = sorted.getOrNull(sorted.size / 2)?.sortKey
            sorted.forEach { item ->
                val ratio = if (baseKey != null && baseKey > 0f) {
                    (item.sortKey / baseKey).coerceAtLeast(0.1f)
                } else {
                    null
                }
                items.add(item.copy(zoomRatio = ratio))
            }
        }

        val normalized = normalizeLensNames(items)
        val back = normalized.filter { it.facing == CameraCharacteristics.LENS_FACING_BACK }
            .sortedBy { it.sortKey }
        val front = normalized.filter { it.facing == CameraCharacteristics.LENS_FACING_FRONT }
            .sortedBy { it.sortKey }
        val others = normalized.filter {
            it.facing != CameraCharacteristics.LENS_FACING_BACK &&
                it.facing != CameraCharacteristics.LENS_FACING_FRONT
        }.sortedBy { it.sortKey }
        return back + front + others
    }

    private fun openSystemGallery() {
        try {
            val miuiComponents = listOf(
                android.content.ComponentName(
                    "com.miui.gallery",
                    "com.miui.gallery.activity.HomePageActivity"
                ),
                android.content.ComponentName(
                    "com.miui.gallery",
                    "com.miui.gallery.activity.GalleryActivity"
                )
            )
            miuiComponents.forEach { component ->
                val intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(component)
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                    logEvent("打开系统相册")
                    return
                }
            }
            val selectorIntent = Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN,
                Intent.CATEGORY_APP_GALLERY
            )
            val resolvedSelector = selectorIntent.resolveActivity(packageManager)
            if (resolvedSelector != null) {
                selectorIntent.component = resolvedSelector
                startActivity(selectorIntent)
                logEvent("打开系统相册")
                return
            }
            val viewIntent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val resolvedView = viewIntent.resolveActivity(packageManager)
            if (resolvedView != null) {
                viewIntent.component = resolvedView
                startActivity(viewIntent)
                logEvent("打开系统相册")
                return
            }
            toast("未找到系统相册应用")
            logEvent("打开相册失败：未找到应用")
        } catch (exception: Exception) {
            toast("打开相册失败")
            logEvent("打开相册失败：${exception.message}")
        }
    }

    private fun buildLensItem(
        cameraId: String,
        characteristics: CameraCharacteristics,
        zoomRatio: Float?
    ): LensItem {
        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
        val focal = characteristics.get(
            CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
        )?.firstOrNull()
        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val equivalentFocal = computeEquivalentFocal(focal, sensorSize)
        val baseName = when (facing) {
            CameraCharacteristics.LENS_FACING_FRONT -> "前置"
            CameraCharacteristics.LENS_FACING_BACK ->
                classifyBackLens(equivalentFocal, focal)
            else -> "镜头"
        }
        val sortKey = equivalentFocal ?: focal ?: Float.MAX_VALUE
        return LensItem(
            id = cameraId,
            name = baseName,
            focal = focal,
            facing = facing,
            zoomRatio = zoomRatio,
            sortKey = sortKey,
            equivalentFocal = equivalentFocal
        )
    }

    private fun normalizeLensNames(items: List<LensItem>): List<LensItem> {
        val nameCounts = items.groupingBy { it.name }.eachCount()
        return items.map { item ->
            val count = nameCounts[item.name] ?: 0
            if (count > 1) {
                val value = item.equivalentFocal ?: item.focal
                val suffix = value?.let { " ${formatFocal(it)}" } ?: " ${item.id}"
                item.copy(name = "${item.name}$suffix")
            } else {
                item
            }
        }
    }

    private fun computeEquivalentFocal(focal: Float?, sensorSize: SizeF?): Float? {
        if (focal == null || sensorSize == null || sensorSize.width <= 0f) return null
        return focal * (36f / sensorSize.width)
    }

    private fun classifyBackLens(equivalentFocal: Float?, focal: Float?): String {
        val eqValue = equivalentFocal
        if (eqValue != null) {
            return when {
                eqValue <= 20f -> "超广角"
                eqValue <= 35f -> "广角"
                else -> "长焦"
            }
        }
        val rawValue = focal ?: return "广角"
        return when {
            rawValue <= 2.5f -> "超广角"
            rawValue <= 4.5f -> "广角"
            else -> "长焦"
        }
    }

    private fun timestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun showSelectionDialog(title: String, items: List<String>, onSelect: (Int) -> Unit) {
        if (items.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(items.toTypedArray()) { dialog, which ->
                onSelect(which)
                dialog.dismiss()
            }
            .show()
    }

    private fun showDebugLog() {
        val content = if (logBuffer.isEmpty()) {
            "暂无日志"
        } else {
            logBuffer.joinToString("\n")
        }
        val view = TextView(this).apply {
            text = content
            setTextColor(ContextCompat.getColor(this@MainActivity, android.R.color.black))
            textSize = 12f
            setPadding(32, 24, 32, 24)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.log_title))
            .setView(view)
            .setPositiveButton(getString(R.string.copy_log)) { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("CamPilotLogs", content))
                toast("日志已复制")
            }
            .setNegativeButton(getString(R.string.close), null)
            .show()
    }

    private fun logEvent(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "[$time] $message"
        logBuffer.add(line)
        if (logBuffer.size > 200) {
            logBuffer.removeAt(0)
        }
    }

    private fun formatFocal(value: Float): String {
        return String.format(Locale.US, "%.1fmm", value)
    }
}

data class LensItem(
    val id: String,
    val name: String,
    val focal: Float?,
    val facing: Int?,
    val zoomRatio: Float?,
    val sortKey: Float,
    val equivalentFocal: Float?
)

private data class LogicalGroup(
    val logicalId: String,
    val physicalIds: Set<String>
)

private class SimpleItemSelectedListener(
    val onSelected: (Int) -> Unit
) : android.widget.AdapterView.OnItemSelectedListener {
    override fun onItemSelected(
        parent: android.widget.AdapterView<*>?,
        view: View?,
        position: Int,
        id: Long
    ) {
        onSelected(position)
    }

    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {
        // no-op
    }
}

