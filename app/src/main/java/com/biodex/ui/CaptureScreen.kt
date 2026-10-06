package com.biodex.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

@Composable
fun CaptureScreen(
    onPhotoCaptured: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var capture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) message = "Camera access is needed to scan a find."
    }
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) requestPermission.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(previewView, hasPermission, lifecycleOwner) {
        var active = true
        if (hasPermission) {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                if (!active) return@addListener
                try {
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val imageCapture = ImageCapture.Builder()
                        .setTargetResolution(Size(1280, 960))
                        .setJpegQuality(85)
                        .build()
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                    cameraProvider = provider
                    capture = imageCapture
                } catch (exception: Exception) {
                    message = "Camera could not start: ${exception.localizedMessage ?: "unknown error"}"
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose {
            active = false
            capture = null
            cameraProvider?.unbindAll()
            cameraProvider = null
        }
    }

    val shutterSource = remember { MutableInteractionSource() }
    val pressed by shutterSource.collectIsPressedAsState()
    val shutterScale by animateFloatAsState(if (pressed) 0.87f else 1f, label = "shutter")
    val takePhoto = takePhoto@{
        val outputDirectory = File(context.filesDir, "captures")
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            message = "Could not prepare local photo storage."
            return@takePhoto
        }
        val photo = File(outputDirectory, "${UUID.randomUUID()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(photo).build()
        busy = true
        capture?.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    busy = false
                    onPhotoCaptured(photo.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    message = "Capture failed: ${exception.message ?: "try again"}"
                }
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF183522))) {
        val frameWidth = minOf(maxWidth * 0.73f, 380.dp)
        val frameHeight = minOf(maxHeight * 0.52f, 430.dp)
        if (hasPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                cssGradient(
                    180f,
                    0f to Color(0xB308190F), 0.32f to Color.Transparent,
                    0.63f to Color.Transparent, 1f to Color(0xD908190F),
                ),
            ),
        )

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 20.dp, vertical = 27.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraButton("close", onBack)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                BText("NEW DISCOVERY", 8f, Color(0xFFEFAD5B), weight = 700, spacing = 1.4f)
                BText("Point at a plant", 20f, Color.White, Modifier.padding(top = 2.dp), family = Serif)
            }
            CameraButton("flash", {})
        }

        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (maxWidth - frameWidth) / 2, y = maxHeight * 0.48f - frameHeight / 2)
                .size(frameWidth, frameHeight),
        ) {
            val bracket = Color(0xD9FFFFFF)
            Canvas(Modifier.fillMaxSize()) {
                val len = 35.dp.toPx()
                val w = 2.dp.toPx()
                val r = 14.dp.toPx()
                val s = Stroke(w, cap = StrokeCap.Round)
                val half = w / 2
                val right = size.width - half
                val bottom = size.height - half
                fun corner(x: Float, y: Float, sx: Float, sy: Float) {
                    val path = Path().apply {
                        moveTo(x, y + sy * len)
                        lineTo(x, y + sy * r)
                        quadraticTo(x, y, x + sx * r, y)
                        lineTo(x + sx * len, y)
                    }
                    drawPath(path, bracket, style = s)
                }
                corner(half, half, 1f, 1f)
                corner(right, half, -1f, 1f)
                corner(half, bottom, 1f, -1f)
                corner(right, bottom, -1f, -1f)
            }
            if (busy) {
                Row(
                    Modifier
                        .align(Alignment.Center)
                        .background(BColor.Forest, CircleShape)
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    BIcon("spark", 15f, Color(0xFFF6BA62))
                    BText("Capturing…", 12f, Color.White, weight = 700)
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 29.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BText(
                message ?: if (hasPermission) "Keep the plant inside the frame" else "Allow camera access to scan a plant",
                11f, Color(0xC7FFFFFF), Modifier.padding(bottom = 23.dp), align = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Box(
                    Modifier
                        .size(47.dp)
                        .background(Color(0xFF496C46), RoundedCornerShape(12.dp))
                        .border(2.dp, Color(0x9EFFFFFF), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) { BIcon("leaf", 22f, Color(0xB3FFFFFF)) }
                Box(
                    Modifier
                        .size(76.dp)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable(
                            interactionSource = shutterSource,
                            indication = null,
                            enabled = hasPermission && !busy && capture != null,
                            onClick = takePhoto,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(61.dp).scale(shutterScale).background(Color.White, CircleShape))
                }
                Box(Modifier.size(47.dp))
            }
            if (!hasPermission) {
                BText(
                    "Allow camera", 12f, Color(0xFFF6BA62),
                    Modifier.padding(top = 16.dp).pressable { requestPermission.launch(Manifest.permission.CAMERA) },
                    weight = 700,
                )
            }
        }
    }
}

@Composable
private fun CameraButton(icon: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(42.dp)
            .background(Color(0x6B0F2618), CircleShape)
            .border(1.dp, Color(0x38FFFFFF), CircleShape)
            .pressable(onClick),
        contentAlignment = Alignment.Center,
    ) { BIcon(icon, 20f, Color.White) }
}
