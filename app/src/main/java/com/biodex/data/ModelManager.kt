package com.biodex.data

import android.content.Context
import java.io.File
import java.io.IOException
import java.security.NoSuchAlgorithmException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ModelManager {
    const val MODEL_FILE_NAME = "bioclip_2_5_vith14_image_fp16.onnx"
    const val DEFAULT_MODEL_URL =
        "https://github.com/Jayant-Jeet/BioDex/raw/main/releases/download/$MODEL_FILE_NAME"
    const val EXPECTED_MODEL_SIZE_BYTES = 1_264_483_431L
    const val EXPECTED_MODEL_SHA256 =
        "b97083f340e214810080a16f53ee89c2b2fdac556629babebbc84e141b42c194"

    private const val VERIFICATION_PREFERENCES = "biodex_model_verification"
    private const val KEY_SHA256 = "sha256"
    private const val KEY_SIZE = "size"
    private const val KEY_MODIFIED = "modified"

    fun getModelFile(context: Context): File {
        val modelDir = File(context.applicationContext.filesDir, "bioclip")
        if (!modelDir.exists() && !modelDir.mkdirs()) {
            throw IllegalStateException("Could not create the BioCLIP model directory.")
        }
        return File(modelDir, MODEL_FILE_NAME)
    }

    fun getPartialModelFile(context: Context): File =
        File(getModelFile(context).parentFile, "$MODEL_FILE_NAME.part")

    suspend fun hasVerifiedModel(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            context.assets.openFd("bioclip/$MODEL_FILE_NAME").use { asset ->
                if (asset.length == EXPECTED_MODEL_SIZE_BYTES) return@withContext true
            }
        } catch (_: java.io.FileNotFoundException) {
            // The model is downloaded on demand in release builds.
        }

        val modelFile = getModelFile(context)
        if (!modelFile.isFile || modelFile.length() != EXPECTED_MODEL_SIZE_BYTES) {
            if (modelFile.exists() && !modelFile.delete()) {
                android.util.Log.w("BioDexModel", "Could not remove a model file with an invalid size.")
            }
            clearVerificationMarker(context)
            return@withContext false
        }

        val preferences = context.applicationContext.getSharedPreferences(
            VERIFICATION_PREFERENCES,
            Context.MODE_PRIVATE,
        )
        val verifiedMarkerMatches =
            preferences.getString(KEY_SHA256, null) == EXPECTED_MODEL_SHA256 &&
                preferences.getLong(KEY_SIZE, -1L) == modelFile.length() &&
                preferences.getLong(KEY_MODIFIED, -1L) == modelFile.lastModified()
        if (verifiedMarkerMatches) return@withContext true

        val actualSha256 = try {
            ResumableModelDownloader.sha256(modelFile)
        } catch (exception: IOException) {
            android.util.Log.e("BioDexModel", "Could not verify the downloaded model.", exception)
            return@withContext false
        } catch (exception: NoSuchAlgorithmException) {
            throw IllegalStateException("SHA-256 is unavailable on this device.", exception)
        }
        if (!actualSha256.equals(EXPECTED_MODEL_SHA256, ignoreCase = true)) {
            android.util.Log.e("BioDexModel", "The saved model failed SHA-256 validation and will be downloaded again.")
            if (!modelFile.delete()) android.util.Log.w("BioDexModel", "Could not remove the invalid model file.")
            clearVerificationMarker(context)
            return@withContext false
        }
        markVerified(context, modelFile)
        true
    }

    fun markVerified(context: Context, modelFile: File = getModelFile(context)) {
        check(modelFile.isFile && modelFile.length() == EXPECTED_MODEL_SIZE_BYTES) {
            "Cannot mark a model verified when its size is invalid."
        }
        val saved = context.applicationContext.getSharedPreferences(
            VERIFICATION_PREFERENCES,
            Context.MODE_PRIVATE,
        ).edit()
            .putString(KEY_SHA256, EXPECTED_MODEL_SHA256)
            .putLong(KEY_SIZE, modelFile.length())
            .putLong(KEY_MODIFIED, modelFile.lastModified())
            .commit()
        check(saved) { "Could not persist the verified model marker." }
    }

    private fun clearVerificationMarker(context: Context) {
        context.applicationContext.getSharedPreferences(
            VERIFICATION_PREFERENCES,
            Context.MODE_PRIVATE,
        ).edit().clear().apply()
    }
}
