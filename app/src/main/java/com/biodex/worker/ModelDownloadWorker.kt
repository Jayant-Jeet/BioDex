package com.biodex.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.biodex.MainActivity
import com.biodex.R
import com.biodex.data.ModelDownloadCoordinator
import com.biodex.data.ModelDownloadStage
import com.biodex.data.ModelDownloadState
import com.biodex.data.ModelDownloadStateStore
import com.biodex.data.ModelManager
import com.biodex.data.ModelHttpException
import com.biodex.data.ModelDownloadPausedException
import com.biodex.data.ModelTransferProgress
import com.biodex.data.ResumableModelDownloader
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ThreadLocalRandom

class ModelDownloadWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    private val stateStore = ModelDownloadStateStore.get(applicationContext)
    private val notificationManager =
        applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    override suspend fun doWork(): Result {
        createNotificationChannel()
        setForeground(foregroundInfo(stateStore.state.value))
        val partialFile = ModelManager.getPartialModelFile(applicationContext)
        if (stateStore.state.value.stage == ModelDownloadStage.DOWNLOADING) {
            val count = incrementStoppedCount()
            Log.w(TAG, "Resuming a download that stopped unexpectedly (count=$count).")
        }
        try {
            if (!ModelDownloadCoordinator.isNetworkConnected(applicationContext)) {
                stateStore.update(stateStore.state.value.copy(stage = ModelDownloadStage.WAITING_FOR_NETWORK))
                return Result.retry()
            }
            if (ModelDownloadCoordinator.isMetered(applicationContext) &&
                !ModelDownloadCoordinator.isMobileDataAllowed(applicationContext)
            ) {
                logState("Mobile data confirmation required before downloading the model.")
                stateStore.update(
                    stateStore.state.value.copy(
                        stage = ModelDownloadStage.NEEDS_MOBILE_CONFIRMATION,
                        errorMessage = null,
                    ),
                )
                updateNotification(stateStore.state.value, finished = true)
                return Result.success()
            }

            val freeBytes = ModelManager.getModelFile(applicationContext).parentFile?.usableSpace ?: 0L
            val remainingBytes = (ModelManager.EXPECTED_MODEL_SIZE_BYTES - partialFile.length()).coerceAtLeast(0L)
            if (freeBytes < remainingBytes + STORAGE_SAFETY_MARGIN_BYTES) {
                val message = "Not enough free storage. Free at least ${formatBytes(remainingBytes + STORAGE_SAFETY_MARGIN_BYTES)} and retry."
                logFailure(message)
                stateStore.update(stateStore.state.value.copy(stage = ModelDownloadStage.ERROR, errorMessage = message))
                updateNotification(stateStore.state.value, finished = true)
                return Result.failure()
            }

            stateStore.update(
                stateStore.state.value.copy(
                    stage = ModelDownloadStage.DOWNLOADING,
                    totalBytes = ModelManager.EXPECTED_MODEL_SIZE_BYTES,
                    errorMessage = null,
                ),
            )
            logState("Starting resumable model download at ${partialFile.length()} bytes.")
            val downloader = ResumableModelDownloader(
                partFile = partialFile,
                destinationFile = ModelManager.getModelFile(applicationContext),
                expectedBytes = ModelManager.EXPECTED_MODEL_SIZE_BYTES,
                expectedSha256 = ModelManager.EXPECTED_MODEL_SHA256,
                log = { Log.i(TAG, it) },
            )
            withContext(Dispatchers.IO) {
                downloader.download(
                    ModelManager.DEFAULT_MODEL_URL,
                    onProgress = ::publishProgress,
                    isCancelled = { isStopped },
                    onVerifying = {
                        val verifyingState = stateStore.state.value.copy(
                            stage = ModelDownloadStage.VERIFYING,
                            bytesPerSecond = 0L,
                            etaMillis = null,
                        )
                        stateStore.update(verifyingState)
                        updateNotification(verifyingState)
                    },
                    pauseForConsent = {
                        val needsConsent = ModelDownloadCoordinator.isMetered(applicationContext) &&
                            !ModelDownloadCoordinator.isMobileDataAllowed(applicationContext)
                        if (needsConsent) {
                            val pausedState = stateStore.state.value.copy(
                                stage = ModelDownloadStage.NEEDS_MOBILE_CONFIRMATION,
                                errorMessage = null,
                            )
                            stateStore.update(pausedState)
                            updateNotification(pausedState, finished = true)
                        }
                        needsConsent
                    },
                )
            }

            stateStore.update(
                stateStore.state.value.copy(
                    stage = ModelDownloadStage.VERIFYING,
                    downloadedBytes = ModelManager.EXPECTED_MODEL_SIZE_BYTES,
                    totalBytes = ModelManager.EXPECTED_MODEL_SIZE_BYTES,
                    bytesPerSecond = 0L,
                    etaMillis = null,
                ),
            )
            ModelManager.markVerified(applicationContext)
            stateStore.update(
                ModelDownloadState(
                    stage = ModelDownloadStage.DONE,
                    downloadedBytes = ModelManager.EXPECTED_MODEL_SIZE_BYTES,
                    totalBytes = ModelManager.EXPECTED_MODEL_SIZE_BYTES,
                ),
            )
            clearStoppedCount()
            logState("Model download, checksum verification, and atomic install completed.")
            updateNotification(stateStore.state.value, finished = true)
            return Result.success()
        } catch (cancelled: CancellationException) {
            if (stateStore.state.value.stage != ModelDownloadStage.CANCELLED) {
                val count = incrementStoppedCount()
                stateStore.update(
                    stateStore.state.value.copy(stage = ModelDownloadStage.WAITING_FOR_NETWORK),
                )
                Log.w(TAG, "Worker was stopped while transferring the model (count=$count); partial bytes retained.")
            }
            throw cancelled
        } catch (paused: ModelDownloadPausedException) {
            logState(paused.localizedMessage ?: "Paused for mobile data consent.")
            updateNotification(stateStore.state.value, finished = true)
            return Result.success()
        } catch (exception: IOException) {
            val waitingForNetwork = !ModelDownloadCoordinator.isNetworkConnected(applicationContext)
            val state = stateStore.state.value.copy(
                stage = if (waitingForNetwork) ModelDownloadStage.WAITING_FOR_NETWORK else ModelDownloadStage.DOWNLOADING,
                errorMessage = exception.localizedMessage,
            )
            stateStore.update(state)
            logFailure(exception.localizedMessage ?: "Model download I/O failure.", exception)
            val permanentHttpFailure = exception is ModelHttpException && !exception.isTransient
            if (permanentHttpFailure || runAttemptCount >= MAX_RETRIES) {
                val message = exception.localizedMessage ?: "The model download could not be completed."
                stateStore.update(state.copy(stage = ModelDownloadStage.ERROR, errorMessage = message))
                updateNotification(stateStore.state.value, finished = true)
                return Result.failure()
            }
            val jitterMillis = ThreadLocalRandom.current().nextLong(0L, MAX_RETRY_JITTER_MILLIS + 1L)
            Log.i(TAG, "Retrying model download after ${jitterMillis}ms of jitter.")
            delay(jitterMillis)
            return Result.retry()
        } catch (exception: Exception) {
            val message = exception.localizedMessage ?: "Unexpected model download failure."
            stateStore.update(stateStore.state.value.copy(stage = ModelDownloadStage.ERROR, errorMessage = message))
            logFailure(message, exception)
            updateNotification(stateStore.state.value, finished = true)
            return Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        foregroundInfo(stateStore.state.value)

    private fun publishProgress(progress: ModelTransferProgress) {
        val state = ModelDownloadState(
            stage = ModelDownloadStage.DOWNLOADING,
            downloadedBytes = progress.downloadedBytes,
            totalBytes = progress.totalBytes,
            bytesPerSecond = progress.bytesPerSecond,
            etaMillis = progress.etaMillis,
        )
        stateStore.update(state)
        updateNotification(state)
    }

    private fun foregroundInfo(state: ModelDownloadState): ForegroundInfo {
        val notification = buildNotification(state)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: ModelDownloadState, finished: Boolean = false): Notification {
        val launchIntent = Intent(applicationContext, MainActivity::class.java)
        val contentIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("BioCLIP field guide")
            .setContentText(notificationText(state))
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(!finished)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (state.stage == ModelDownloadStage.DOWNLOADING && state.totalBytes > 0L) {
            builder.setProgress(100, (state.progress * 100).toInt(), false)
        } else {
            builder.setProgress(0, 0, state.stage == ModelDownloadStage.QUEUED)
        }
        if (!finished) {
            val cancelIntent = Intent(applicationContext, CancelModelDownloadReceiver::class.java)
                .setAction(CancelModelDownloadReceiver.ACTION_CANCEL)
            val cancelPendingIntent = PendingIntent.getBroadcast(
                applicationContext,
                NOTIFICATION_ID,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Cancel", cancelPendingIntent)
        }
        return builder.build()
    }

    private fun notificationText(state: ModelDownloadState): String = when (state.stage) {
        ModelDownloadStage.DOWNLOADING ->
            "${formatBytes(state.downloadedBytes)} of ${formatBytes(state.totalBytes)} · ${state.progressPercent()}%"
        ModelDownloadStage.VERIFYING -> "Verifying model integrity…"
        ModelDownloadStage.WAITING_FOR_NETWORK -> "Waiting for a network connection"
        ModelDownloadStage.NEEDS_MOBILE_CONFIRMATION -> "Waiting for mobile data confirmation"
        ModelDownloadStage.ERROR -> state.errorMessage ?: "Download failed"
        ModelDownloadStage.DONE -> "Model ready for offline identification"
        ModelDownloadStage.CANCELLED -> "Download cancelled"
        else -> "Preparing offline plant identification"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "BioCLIP model download", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun updateNotification(state: ModelDownloadState, finished: Boolean = false) {
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(state, finished))
        } catch (exception: SecurityException) {
            Log.w(TAG, "Notification permission is unavailable; download continues with in-app progress.", exception)
        }
    }

    private fun incrementStoppedCount(): Int {
        val preferences = applicationContext.getSharedPreferences(STOP_COUNT_PREFERENCES, Context.MODE_PRIVATE)
        val count = preferences.getInt(STOP_COUNT_KEY, 0) + 1
        preferences.edit().putInt(STOP_COUNT_KEY, count).apply()
        return count
    }

    private fun clearStoppedCount() {
        applicationContext.getSharedPreferences(STOP_COUNT_PREFERENCES, Context.MODE_PRIVATE)
            .edit().remove(STOP_COUNT_KEY).apply()
    }

    private fun logState(message: String) = Log.i(TAG, message)
    private fun logFailure(message: String, exception: Exception? = null) {
        if (exception == null) Log.e(TAG, message) else Log.e(TAG, message, exception)
    }

    private fun ModelDownloadState.progressPercent() = (progress * 100).toInt()

    private fun formatBytes(bytes: Long): String =
        String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))

    companion object {
        private const val TAG = "BioDexModelDownload"
        private const val CHANNEL_ID = "biodex_model_download"
        private const val NOTIFICATION_ID = 4257
        private const val MAX_RETRIES = 5
        private const val MAX_RETRY_JITTER_MILLIS = 5_000L
        private const val STORAGE_SAFETY_MARGIN_BYTES = 256L * 1024 * 1024
        private const val STOP_COUNT_PREFERENCES = "biodex_model_download"
        private const val STOP_COUNT_KEY = "worker_stopped_count"
    }
}

class CancelModelDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_CANCEL) return
        Log.i("BioDexModelDownload", "User cancelled the model download from its notification.")
        ModelDownloadCoordinator.cancel(context)
    }

    companion object {
        const val ACTION_CANCEL = "com.biodex.action.CANCEL_MODEL_DOWNLOAD"
    }
}
