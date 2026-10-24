package com.biodex.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.biodex.worker.ModelDownloadWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class ModelDownloadStage {
    IDLE,
    QUEUED,
    DOWNLOADING,
    WAITING_FOR_NETWORK,
    NEEDS_MOBILE_CONFIRMATION,
    VERIFYING,
    ERROR,
    DONE,
    CANCELLED,
}

data class ModelDownloadState(
    val stage: ModelDownloadStage = ModelDownloadStage.IDLE,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = ModelManager.EXPECTED_MODEL_SIZE_BYTES,
    val bytesPerSecond: Long = 0L,
    val etaMillis: Long? = null,
    val errorMessage: String? = null,
) {
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

class ModelDownloadStateStore private constructor(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(readState())
    val state: StateFlow<ModelDownloadState> = mutableState

    @Synchronized
    fun update(state: ModelDownloadState) {
        val previousStage = mutableState.value.stage
        if (previousStage != state.stage) {
            android.util.Log.i("BioDexModelDownload", "State transition: $previousStage -> ${state.stage}.")
        }
        preferences.edit()
            .putString(KEY_STAGE, state.stage.name)
            .putLong(KEY_DOWNLOADED, state.downloadedBytes)
            .putLong(KEY_TOTAL, state.totalBytes)
            .putLong(KEY_SPEED, state.bytesPerSecond)
            .putLong(KEY_ETA, state.etaMillis ?: -1L)
            .putString(KEY_ERROR, state.errorMessage)
            .apply()
        mutableState.value = state
    }

    @Synchronized
    fun reset() = update(ModelDownloadState())

    private fun readState(): ModelDownloadState {
        val storedStage = preferences.getString(KEY_STAGE, ModelDownloadStage.IDLE.name)
        val stage = try {
            ModelDownloadStage.valueOf(storedStage ?: ModelDownloadStage.IDLE.name)
        } catch (exception: IllegalArgumentException) {
            android.util.Log.w("BioDexModelDownload", "Ignoring invalid persisted download stage: $storedStage.", exception)
            ModelDownloadStage.IDLE
        }
        return ModelDownloadState(
            stage = stage,
            downloadedBytes = preferences.getLong(KEY_DOWNLOADED, 0L),
            totalBytes = preferences.getLong(KEY_TOTAL, ModelManager.EXPECTED_MODEL_SIZE_BYTES),
            bytesPerSecond = preferences.getLong(KEY_SPEED, 0L),
            etaMillis = preferences.getLong(KEY_ETA, -1L).takeIf { it >= 0L },
            errorMessage = preferences.getString(KEY_ERROR, null),
        )
    }

    companion object {
        private const val PREFERENCES = "biodex_model_download"
        private const val KEY_STAGE = "stage"
        private const val KEY_DOWNLOADED = "downloaded"
        private const val KEY_TOTAL = "total"
        private const val KEY_SPEED = "speed"
        private const val KEY_ETA = "eta"
        private const val KEY_ERROR = "error"

        @Volatile
        private var instance: ModelDownloadStateStore? = null

        fun get(context: Context): ModelDownloadStateStore =
            instance ?: synchronized(this) {
                instance ?: ModelDownloadStateStore(context).also { instance = it }
            }
    }
}

object ModelDownloadCoordinator {
    private const val UNIQUE_WORK_NAME = "biodex-model-download"
    private const val PREFERENCES = "biodex_model_download"
    private const val KEY_MOBILE_DATA_ALLOWED = "mobile_data_allowed"

    fun enqueue(context: Context, replace: Boolean = false) {
        val appContext = context.applicationContext
        val store = ModelDownloadStateStore.get(appContext)
        store.update(store.state.value.copy(stage = ModelDownloadStage.QUEUED, errorMessage = null))
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(UNIQUE_WORK_NAME)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        ModelDownloadStateStore.get(appContext).update(
            ModelDownloadState(stage = ModelDownloadStage.CANCELLED, errorMessage = "Download cancelled."),
        )
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun allowMobileData(context: Context) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MOBILE_DATA_ALLOWED, true).apply()
    }

    fun isMobileDataAllowed(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(KEY_MOBILE_DATA_ALLOWED, false)

    fun isMetered(context: Context): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivity.isActiveNetworkMetered
    }

    fun isNetworkConnected(context: Context): Boolean {
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivity.activeNetwork ?: return false
        val capabilities = connectivity.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
