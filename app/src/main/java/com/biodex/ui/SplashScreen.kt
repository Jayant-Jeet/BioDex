package com.biodex.ui

import android.Manifest
import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.biodex.data.ModelDownloadCoordinator
import com.biodex.data.ModelDownloadStage
import com.biodex.data.ModelDownloadState
import com.biodex.data.ModelDownloadStateStore
import com.biodex.data.ModelManager
import kotlinx.coroutines.delay
import kotlin.math.hypot

@Composable
fun SplashScreen(
    onDone: () -> Unit,
    previewMode: Boolean = false,
    previewState: ModelDownloadState? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val stateStore = remember(context) { ModelDownloadStateStore.get(context) }
    val observedDownloadState by stateStore.state.collectAsState()
    val downloadState = if (previewMode) previewState ?: observedDownloadState else observedDownloadState
    var networkConnected by remember { mutableStateOf(ModelDownloadCoordinator.isNetworkConnected(context)) }
    var launchError by remember { mutableStateOf<String?>(null) }
    var hasModel by remember { mutableStateOf(false) }

    val enqueueDownload = {
        launchError = null
        if (ModelDownloadCoordinator.isMetered(context) && !ModelDownloadCoordinator.isMobileDataAllowed(context)) {
            stateStore.update(downloadState.copy(stage = ModelDownloadStage.NEEDS_MOBILE_CONFIRMATION))
        } else {
            ModelDownloadCoordinator.enqueue(context, replace = downloadState.stage in setOf(
                ModelDownloadStage.ERROR,
                ModelDownloadStage.CANCELLED,
                ModelDownloadStage.NEEDS_MOBILE_CONFIRMATION,
            ))
        }
    }
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            enqueueDownload()
        }
    val requestNotificationPermissionAndDownload = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enqueueDownload()
        }
    }

    DisposableEffect(view, previewMode) {
        val activity = if (previewMode) null else view.context.findActivity()
        val window = activity?.window
        val wasKeepingScreenOn = window?.let {
            it.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        } ?: false
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                networkConnected = true
            }

            override fun onLost(network: Network) {
                networkConnected = ModelDownloadCoordinator.isNetworkConnected(context)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                networkConnected = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
        }
        var networkCallbackRegistered = false
        if (!previewMode) {
            val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try {
                connectivity.registerDefaultNetworkCallback(callback)
                networkCallbackRegistered = true
            } catch (exception: SecurityException) {
                launchError = "Network status is unavailable: ${exception.localizedMessage ?: "permission denied"}"
            }
        }
        onDispose {
            if (networkCallbackRegistered) {
                try {
                    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                    connectivity.unregisterNetworkCallback(callback)
                } catch (exception: IllegalArgumentException) {
                    android.util.Log.w("BioDexModelDownload", "Network callback was already unregistered.", exception)
                }
            }
            if (window != null && !wasKeepingScreenOn) {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    LaunchedEffect(previewMode) {
        if (previewMode) return@LaunchedEffect
        try {
            hasModel = ModelManager.hasVerifiedModel(context)
            if (hasModel) {
                stateStore.update(
                    ModelDownloadState(
                        stage = ModelDownloadStage.DONE,
                        downloadedBytes = ModelManager.EXPECTED_MODEL_SIZE_BYTES,
                        totalBytes = ModelManager.EXPECTED_MODEL_SIZE_BYTES,
                    ),
                )
                delay(700)
                onDone()
                return@LaunchedEffect
            }
            if (downloadState.stage == ModelDownloadStage.IDLE) requestNotificationPermissionAndDownload()
        } catch (exception: Exception) {
            launchError = exception.localizedMessage ?: "Could not verify the offline model."
            stateStore.update(downloadState.copy(stage = ModelDownloadStage.ERROR, errorMessage = launchError))
        }
    }

    LaunchedEffect(downloadState.stage) {
        if (downloadState.stage == ModelDownloadStage.DONE && !hasModel && !previewMode) {
            val verified = ModelManager.hasVerifiedModel(context)
            hasModel = verified
            if (verified) {
                delay(500)
                onDone()
            } else {
                launchError = "The model could not be verified after download. Retry to download it again."
                stateStore.update(downloadState.copy(stage = ModelDownloadStage.ERROR, errorMessage = launchError))
            }
        }
    }

    LaunchedEffect(networkConnected, downloadState.stage) {
        if (!networkConnected && downloadState.stage in setOf(
                ModelDownloadStage.QUEUED,
                ModelDownloadStage.DOWNLOADING,
            )
        ) {
            stateStore.update(downloadState.copy(stage = ModelDownloadStage.WAITING_FOR_NETWORK))
        } else if (networkConnected && downloadState.stage == ModelDownloadStage.WAITING_FOR_NETWORK) {
            stateStore.update(downloadState.copy(stage = ModelDownloadStage.QUEUED))
        }
    }

    val slide = rememberInfiniteTransition(label = "load")
    val bar by slide.animateFloat(
        initialValue = -0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "bar",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(BColor.Ink)
            .drawGlow(0.2f, 0.2f, 0.24f, Color(0x2EB7D28E))
            .drawGlow(0.8f, 0.77f, 0.23f, Color(0x1ADE9D48)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
            Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .matchParentSizeRing()
                        .border(1.dp, Color(0x21E1EED0), CircleShape),
                )
                Box(
                    Modifier
                        .size(82.dp)
                        .rotate(-3f)
                        .shadow(18.dp, RoundedCornerShape(28.dp, 28.dp, 28.dp, 10.dp), ambientColor = Color.Black, spotColor = Color.Black)
                        .background(Color(0xFFF5F2DF), RoundedCornerShape(28.dp, 28.dp, 28.dp, 10.dp)),
                    contentAlignment = Alignment.Center,
                ) { BIcon("leaf", 44f, BColor.Forest) }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 17.dp, end = 15.dp)
                        .size(8.dp)
                        .background(Color(0xFFEDA34F), CircleShape),
                )
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(bottom = 10.dp, start = 23.dp)
                        .size(5.dp)
                        .background(Color(0xFFAECA8C), CircleShape),
                )
            }
            Wordmark(45f, Color(0xFFF8F5E9), Modifier.padding(top = 18.dp))
            BText(
                "The living index", 14f, Color(0xFFB9CDBD),
                Modifier.padding(top = 7.dp, bottom = 30.dp), family = SerifItalic, italic = true,
            )

            val isActive = downloadState.stage in setOf(
                ModelDownloadStage.QUEUED,
                ModelDownloadStage.DOWNLOADING,
                ModelDownloadStage.WAITING_FOR_NETWORK,
                ModelDownloadStage.VERIFYING,
            )
            Box(
                Modifier
                    .fillMaxWidth(0.7f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0x1FFFFFFF)),
            ) {
                if (downloadState.stage == ModelDownloadStage.DOWNLOADING) {
                    Box(
                        Modifier
                            .fillMaxWidth(downloadState.progress)
                            .height(4.dp)
                            .background(BColor.Amber, CircleShape),
                    )
                } else if (!isActive && !previewMode) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.5f)
                            .height(4.dp)
                            .offset(x = (132 * bar).dp)
                            .background(BColor.Amber, CircleShape),
                    )
                } else if (isActive) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(BColor.Amber, CircleShape))
                }
            }

            BText(
                statusMessage(downloadState, networkConnected), 10f, Color(0xFF7FA087),
                Modifier.padding(top = 14.dp), weight = 600, spacing = 0.9f, align = TextAlign.Center,
            )
            if (downloadState.stage == ModelDownloadStage.DOWNLOADING) {
                val speed = downloadState.bytesPerSecond / (1024.0 * 1024.0)
                val eta = downloadState.etaMillis?.let(::formatEta) ?: "calculating"
                BText(
                    "${formatMegabytes(downloadState.downloadedBytes)} MB of ${formatMegabytes(downloadState.totalBytes)} MB · ${"%.1f".format(java.util.Locale.ROOT, speed)} MB/s · $eta left",
                    9f,
                    Color(0xFFA8B8AA),
                    Modifier.padding(top = 7.dp),
                    align = TextAlign.Center,
                )
            }
            launchError?.let {
                BText(it, 10f, Color(0xFFE57373), Modifier.padding(top = 8.dp), align = TextAlign.Center)
            }
            if (downloadState.stage == ModelDownloadStage.NEEDS_MOBILE_CONFIRMATION) {
                BText(
                    "This download is about 1.26 GB. Continue using mobile data?",
                    10f,
                    Color(0xFFF0DDB5),
                    Modifier.padding(top = 12.dp),
                    align = TextAlign.Center,
                )
                ActionButton("DOWNLOAD WITH MOBILE DATA") {
                    ModelDownloadCoordinator.allowMobileData(context)
                    requestNotificationPermissionAndDownload()
                }
            } else if (downloadState.stage == ModelDownloadStage.ERROR ||
                downloadState.stage == ModelDownloadStage.CANCELLED
            ) {
                val error = downloadState.errorMessage
                if (error != null) {
                    BText(error, 10f, Color(0xFFE57373), Modifier.padding(top = 8.dp), align = TextAlign.Center)
                }
                ActionButton("RETRY DOWNLOAD", requestNotificationPermissionAndDownload)
            } else if (isActive) {
                if (downloadState.stage == ModelDownloadStage.WAITING_FOR_NETWORK || !networkConnected) {
                    BText("Your download will resume when a connection returns.", 9f, Color(0xFFA8B8AA), Modifier.padding(top = 7.dp))
                }
                ActionButton("CANCEL DOWNLOAD") { ModelDownloadCoordinator.cancel(context) }
            } else if (downloadState.stage == ModelDownloadStage.IDLE && !previewMode) {
                ActionButton("DOWNLOAD OFFLINE MODEL", requestNotificationPermissionAndDownload)
            }

            if (!hasModel && !previewMode && shouldShowBatteryGuidance(context)) {
                BText(
                    "Keep BioDex open during this large download. Your phone's battery settings may pause background work.",
                    9f,
                    Color(0xFFA8B8AA),
                    Modifier.padding(top = 14.dp),
                    align = TextAlign.Center,
                )
                ActionButton("BATTERY SETTINGS") {
                    openBatterySettings(context) { launchError = it }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BColor.Amber)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        BText(label, 10f, Color.Black, weight = 700)
    }
}

private fun statusMessage(state: ModelDownloadState, networkConnected: Boolean): String = when {
    state.stage == ModelDownloadStage.QUEUED && !networkConnected -> "WAITING FOR NETWORK…"
    state.stage == ModelDownloadStage.QUEUED -> "DOWNLOAD QUEUED…"
    state.stage == ModelDownloadStage.DOWNLOADING -> "DOWNLOADING MODEL: ${(state.progress * 100).toInt()}%"
    state.stage == ModelDownloadStage.WAITING_FOR_NETWORK -> "PAUSED · WAITING FOR NETWORK"
    state.stage == ModelDownloadStage.NEEDS_MOBILE_CONFIRMATION -> "MOBILE DATA CONFIRMATION"
    state.stage == ModelDownloadStage.VERIFYING -> "VERIFYING MODEL INTEGRITY…"
    state.stage == ModelDownloadStage.ERROR -> "DOWNLOAD FAILED"
    state.stage == ModelDownloadStage.CANCELLED -> "DOWNLOAD CANCELLED"
    state.stage == ModelDownloadStage.DONE -> "MODEL READY!"
    else -> "GROWING YOUR COLLECTION…"
}

private fun formatMegabytes(bytes: Long): String =
    "%.0f".format(java.util.Locale.ROOT, bytes / (1024.0 * 1024.0))

private fun formatEta(millis: Long): String {
    val seconds = (millis / 1000L).coerceAtLeast(0L)
    return when {
        seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

private fun shouldShowBatteryGuidance(context: Context): Boolean {
    val manufacturer = (Build.MANUFACTURER + " " + Build.BRAND).lowercase(java.util.Locale.ROOT)
    val aggressiveManufacturer = listOf("xiaomi", "redmi", "poco", "oppo", "vivo", "realme", "samsung", "oneplus")
        .any(manufacturer::contains)
    val stoppedCount = context.getSharedPreferences("biodex_model_download", Context.MODE_PRIVATE)
        .getInt("worker_stopped_count", 0)
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return (aggressiveManufacturer || stoppedCount >= 2) &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !powerManager.isIgnoringBatteryOptimizations(context.packageName))
}

private fun openBatterySettings(context: Context, onError: (String) -> Unit) {
    val directIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
    try {
        context.startActivity(directIntent)
    } catch (exception: ActivityNotFoundException) {
        openBatterySettingsFallback(context, exception, onError)
    } catch (exception: SecurityException) {
        openBatterySettingsFallback(context, exception, onError)
    }
}

private fun openBatterySettingsFallback(
    context: Context,
    originalException: Exception,
    onError: (String) -> Unit,
) {
    try {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    } catch (exception: ActivityNotFoundException) {
        onError(exception.localizedMessage ?: originalException.localizedMessage ?: "Could not open battery settings.")
    } catch (exception: SecurityException) {
        onError(exception.localizedMessage ?: originalException.localizedMessage ?: "Could not open battery settings.")
    }
}

private fun Context.findActivity(): android.app.Activity? =
    when (this) {
        is android.app.Activity -> this
        is android.content.ContextWrapper -> baseContext.findActivity()
        else -> null
    }

@Composable
fun Wordmark(size: Float, color: Color, modifier: Modifier = Modifier, spacing: Float = -0.5f) {
    Row(modifier) {
        BText("BioDex", size, color, family = Serif, spacing = spacing)
        BText(".", size, BColor.Orange, family = Serif)
    }
}

private fun Modifier.matchParentSizeRing(): Modifier = this
    .fillMaxSize()
    .padding(3.dp)

private fun Modifier.drawGlow(cx: Float, cy: Float, stop: Float, color: Color): Modifier = drawBehindCompat { size ->
    val farthest = hypot(maxOf(cx, 1 - cx) * size.width, maxOf(cy, 1 - cy) * size.height)
    drawRect(
        Brush.radialGradient(
            listOf(color, Color.Transparent),
            center = Offset(cx * size.width, cy * size.height),
            radius = farthest * stop,
        ),
    )
}

private fun Modifier.drawBehindCompat(
    block: androidx.compose.ui.graphics.drawscope.DrawScope.(androidx.compose.ui.geometry.Size) -> Unit,
) = drawBehind { block(size) }

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashScreenPreview() {
    MaterialTheme {
        Surface {
            SplashScreen(
                onDone = {},
                previewMode = true,
                previewState = ModelDownloadState(
                    stage = ModelDownloadStage.DOWNLOADING,
                    downloadedBytes = 540L * 1024 * 1024,
                    bytesPerSecond = 6L * 1024 * 1024,
                    etaMillis = 120_000L,
                ),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun SplashScreenErrorPreview() {
    MaterialTheme {
        Surface {
            SplashScreen(
                onDone = {},
                previewMode = true,
                previewState = ModelDownloadState(
                    stage = ModelDownloadStage.ERROR,
                    errorMessage = "The download was interrupted. Your saved progress is ready to resume.",
                ),
            )
        }
    }
}
