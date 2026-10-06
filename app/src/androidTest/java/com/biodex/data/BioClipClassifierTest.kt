package com.biodex.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BioClipClassifierTest {
    @Test
    fun officialBioClipModelClassifiesPlantImageOffline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photo = createPlantImage(context)
        val taxonomy = SpeciesCatalog.loadBioClip(context)
        val classifier = BioClipClassifier(context)

        try {
            val result = classifier.identify(photo.absolutePath, taxonomy)
            assertEquals(4271, taxonomy.size)
            assertTrue(result.species in taxonomy)
            assertTrue("Cosine similarity must be finite", result.similarity.isFinite())
            assertTrue(result.similarity in -1f..1f)
            assertTrue(result.quality in 10..100)
        } finally {
            classifier.close()
            assertTrue("Test image should be deleted", photo.delete())
        }
    }

    private fun createPlantImage(context: Context): File {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(224, 230, 205))
            val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(49, 116, 52)
            }
            canvas.drawOval(60f, 32f, 172f, 202f, leafPaint)
            canvas.drawOval(150f, 64f, 276f, 216f, leafPaint)
            val veinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(182, 197, 100)
                strokeWidth = 4f
            }
            canvas.drawLine(72f, 184f, 157f, 54f, veinPaint)
            canvas.drawLine(166f, 202f, 264f, 78f, veinPaint)

            val file = File(context.cacheDir, "bioclip-test-${UUID.randomUUID()}.jpg")
            check(file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }) {
                "Could not write BioCLIP test image."
            }
            return file
        } finally {
            bitmap.recycle()
        }
    }
}
