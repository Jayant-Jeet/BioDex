package com.biodex.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.biodex.data.CollectionCard
import com.biodex.data.Species
import com.biodex.util.PhotoBitmap
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

object CardArtwork {
    private const val W = 720
    private const val H = 1000
    private val gold = Color.rgb(255, 214, 102)

    fun render(context: Context, card: CollectionCard, species: Species): File {
        val image = draw(card, species)
        val directory = File(context.filesDir, "cards")
        check(directory.exists() || directory.mkdirs()) { "Could not create the card export folder." }
        val destination = File(directory, "${card.id}.png")
        FileOutputStream(destination).use { output ->
            check(image.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Could not encode the card image."
            }
        }
        image.recycle()
        return destination
    }

    fun draw(card: CollectionCard, species: Species): Bitmap {
        val image = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        drawBackground(canvas, paint, card.id)
        drawHeader(canvas, paint, species)
        drawArt(canvas, paint, card.photoPath)
        drawDetail(canvas, card.shortDetail ?: species.detail ?: fallbackDetail(species))
        drawRarity(canvas, paint, species.rarity, card.quality)
        drawFooter(canvas, paint, card)
        return image
    }

    private fun drawBackground(canvas: Canvas, paint: Paint, seed: String) {
        paint.shader = LinearGradient(0f, 0f, W.toFloat(), H.toFloat(),
            intArrayOf(Color.rgb(28, 14, 66), Color.rgb(88, 44, 150), Color.rgb(36, 22, 92)),
            null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(0f, 0f, W.toFloat(), H.toFloat()), 40f, 40f, paint)
        paint.shader = null
        val random = Random(seed.hashCode().toLong())
        repeat(70) {
            paint.color = Color.argb(60 + random.nextInt(160), 255, 255, 255)
            canvas.drawCircle(random.nextFloat() * W, random.nextFloat() * H, 1f + random.nextFloat() * 2.5f, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        paint.color = gold
        canvas.drawRoundRect(RectF(8f, 8f, W - 8f, H - 8f), 34f, 34f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawHeader(canvas: Canvas, paint: Paint, species: Species) {
        val bar = RectF(34f, 34f, W - 34f, 160f)
        paint.color = Color.argb(235, 22, 12, 52)
        canvas.drawRoundRect(bar, 26f, 26f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = gold
        canvas.drawRoundRect(bar, 26f, 26f, paint)
        paint.style = Paint.Style.FILL

        drawLeaf(canvas, paint, 82f, 97f)
        drawLeaf(canvas, paint, W - 82f, 97f)

        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textSize = 46f
            textAlign = Paint.Align.CENTER
        }
        val maxWidth = W - 2 * 120f
        while (title.measureText(species.name) > maxWidth && title.textSize > 24f) title.textSize -= 2f
        canvas.drawText(species.name, W / 2f, 98f, title)
        title.apply {
            color = Color.rgb(214, 200, 255)
            typeface = Typeface.create("sans-serif", Typeface.ITALIC)
            textSize = 26f
        }
        while (title.measureText(species.scientificName) > maxWidth && title.textSize > 16f) title.textSize -= 1f
        canvas.drawText(species.scientificName, W / 2f, 138f, title)
    }

    private fun drawLeaf(canvas: Canvas, paint: Paint, cx: Float, cy: Float) {
        paint.color = Color.rgb(46, 160, 92)
        canvas.drawCircle(cx, cy, 30f, paint)
        paint.color = Color.rgb(170, 240, 190)
        val leaf = Path().apply {
            moveTo(cx - 14f, cy + 14f)
            quadTo(cx - 18f, cy - 14f, cx + 14f, cy - 16f)
            quadTo(cx + 18f, cy + 14f, cx - 14f, cy + 14f)
        }
        canvas.drawPath(leaf, paint)
        paint.color = Color.rgb(46, 160, 92)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        canvas.drawLine(cx - 12f, cy + 12f, cx + 8f, cy - 8f, paint)
        paint.style = Paint.Style.FILL
    }

    private fun drawArt(canvas: Canvas, paint: Paint, photoPath: String) {
        val frame = RectF(34f, 180f, W - 34f, 640f)
        paint.color = gold
        canvas.drawRoundRect(frame, 26f, 26f, paint)
        val target = RectF(frame.left + 6f, frame.top + 6f, frame.right - 6f, frame.bottom - 6f)
        val photo = PhotoBitmap.decode(photoPath, sampleSize = 2)
        try {
            val scale = maxOf(target.width() / photo.width, target.height() / photo.height)
            val width = (target.width() / scale).toInt()
            val height = (target.height() / scale).toInt()
            val source = Rect(
                (photo.width - width) / 2, (photo.height - height) / 2,
                (photo.width + width) / 2, (photo.height + height) / 2,
            )
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(target, 21f, 21f, Path.Direction.CW) })
            canvas.drawBitmap(photo, source, target, paint)
            paint.shader = LinearGradient(0f, target.bottom - 90f, 0f, target.bottom,
                Color.TRANSPARENT, Color.argb(150, 40, 10, 90), Shader.TileMode.CLAMP)
            canvas.drawRect(target.left, target.bottom - 90f, target.right, target.bottom, paint)
            paint.shader = null
            canvas.restore()
        } finally {
            photo.recycle()
        }
    }

    private fun drawDetail(canvas: Canvas, text: String) {
        val box = RectF(34f, 656f, W - 34f, 800f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(200, 22, 12, 52) }
        canvas.drawRoundRect(box, 22f, 22f, paint)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(236, 230, 255)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = 24f
        }
        val width = (box.width() - 40f).toInt()
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(4)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(box.left + 20f, box.top + (box.height() - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawRarity(canvas: Canvas, paint: Paint, rarity: String, quality: Int) {
        val (tier, label) = tierFor(rarity)
        val badge = RectF(34f, 818f, 400f, 888f)
        paint.shader = LinearGradient(badge.left, 0f, badge.right, 0f, Color.rgb(255, 224, 120),
            Color.rgb(224, 150, 40), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(badge, 35f, 35f, paint)
        paint.shader = null
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(50, 24, 90)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        val line = "$tier ★ $label"
        while (text.measureText(line) > badge.width() - 30f && text.textSize > 16f) text.textSize -= 1f
        canvas.drawText(line, badge.centerX(), badge.centerY() + text.textSize * 0.35f, text)
        text.apply { color = Color.rgb(214, 200, 255); textSize = 26f; textAlign = Paint.Align.RIGHT }
        canvas.drawText("Quality $quality", W - 40f, badge.centerY() + 9f, text)
    }

    private fun drawFooter(canvas: Canvas, paint: Paint, card: CollectionCard) {
        val strip = RectF(34f, 904f, W - 34f, 966f)
        paint.color = Color.argb(235, 22, 12, 52)
        canvas.drawRoundRect(strip, 22f, 22f, paint)
        val date = SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault()).format(Date(card.capturedAt))
        val line = "Captured on $date | ${card.location ?: "Location unavailable"}"
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = 22f
        }
        val width = strip.width() - 36f
        while (text.measureText(line) > width && text.textSize > 15f) text.textSize -= 1f
        canvas.drawText(TextUtils.ellipsize(line, text, width, TextUtils.TruncateAt.END).toString(),
            strip.left + 18f, strip.centerY() + text.textSize * 0.35f, text)
    }

    private fun tierFor(rarity: String): Pair<String, String> = when (rarity.lowercase(Locale.ROOT)) {
        "common" -> "C" to "Common"
        "uncommon" -> "B" to "Uncommon"
        "rare" -> "A" to "Rare"
        "extremely rare", "legendary" -> "S1" to "Extremely Rare"
        else -> "F" to rarity
    }
}

private fun fallbackDetail(species: Species) =
    "${species.name} (${species.scientificName}) is a plant spotted in the wild. " +
        "Look closely at its leaves, flowers and habitat."

