package com.biodex.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import com.biodex.R
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

object BColor {
    val Page = Color(0xFFF7F8F2)
    val Outer = Color(0xFFEEF4E9)
    val Ink = Color(0xFF173C2A)
    val Forest = Color(0xFF285B3C)
    val Orange = Color(0xFFE58C37)
    val Orange2 = Color(0xFFD47C2B)
    val Amber = Color(0xFFEAA552)
    val Muted = Color(0xFF89978D)
    val Muted2 = Color(0xFF6D7F72)
    val Border = Color(0xFFE1E7DC)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Sans = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(
            R.font.dm_sans,
            FontWeight(w),
            variationSettings = FontVariation.Settings(FontVariation.weight(w)),
        )
    },
)
val Serif = FontFamily(Font(R.font.dm_serif_regular))
val SerifItalic = FontFamily(Font(R.font.dm_serif_italic, style = FontStyle.Italic))

/** Text sized in CSS px (1:1 with sp so that user font scale does not break the pixel-perfect layout). */
@Composable
fun BText(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: Int = 400,
    family: FontFamily = Sans,
    spacing: Float = 0f,
    lineHeight: Float? = null,
    align: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    italic: Boolean = false,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = align,
        style = TextStyle(
            fontFamily = family,
            fontSize = size.sp,
            fontWeight = FontWeight(weight),
            fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
            letterSpacing = spacing.sp,
            lineHeight = (lineHeight ?: (size * 1.25f)).sp,
            platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None,
            ),
        ),
    )
}

/** CSS-style linear-gradient(angle, stops) that adapts to the size of the drawn area. */
fun cssGradient(angle: Float, vararg stops: Pair<Float, Color>): Brush = object : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val a = Math.toRadians(angle.toDouble())
        val dx = sin(a).toFloat()
        val dy = -cos(a).toFloat()
        val length = abs(size.width * dx) + abs(size.height * dy)
        val center = Offset(size.width / 2, size.height / 2)
        val half = Offset(dx * length / 2, dy * length / 2)
        return LinearGradientShader(
            center - half,
            center + half,
            stops.map { it.second },
            stops.map { it.first },
        )
    }
}

fun Modifier.pressable(onClick: () -> Unit): Modifier = clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = onClick,
)

fun Modifier.tilt(degrees: Float) = rotate(degrees)

fun Modifier.hairline(color: Color, width: Dp, radius: Dp) = border(width, color, RoundedCornerShape(radius))

private val iconPaths: Map<String, List<String>> = mapOf(
    "camera" to listOf(
        "M4 7.8h3.2l1.4-2.3h6.8l1.4 2.3H20a2 2 0 0 1 2 2V19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9.8a2 2 0 0 1 2-2Z",
        circle(12f, 14f, 4f),
    ),
    "chevron" to listOf("m9 18 6-6-6-6"),
    "close" to listOf("m6 6 12 12", "M18 6 6 18"),
    "collection" to listOf("M4.5 5.5h15v14h-15z", "M8 2.5v3M16 2.5v3M4.5 9h15"),
    "compass" to listOf(circle(12f, 12f, 9f), "m15.5 8.5-2 5-5 2 2-5 5-2Z"),
    "flash" to listOf("m13 2-8 12h7l-1 8 8-12h-7l1-8Z"),
    "leaf" to listOf(
        "M19.5 4.5C11 4.4 5 7.8 5 13.5c0 3.3 2.4 5.7 5.5 5.7 6.4 0 9-7.5 9-14.7Z",
        "M4.5 21c2.5-6.5 6-9.5 11-12",
    ),
    "search" to listOf(circle(10.5f, 10.5f, 6.5f), "m15.5 15.5 5 5"),
    "spark" to listOf(
        "M12 2.5c.7 5.3 3.2 7.8 8.5 8.5-5.3.7-7.8 3.2-8.5 8.5-.7-5.3-3.2-7.8-8.5-8.5 5.3-.7 7.8-3.2 8.5-8.5Z",
        "M19 3v4M21 5h-4",
    ),
    "trophy" to listOf(
        "M8 4h8v5a4 4 0 0 1-8 0V4ZM12 13v4M8.5 21h7M10 17h4",
        "M8 6H4v1a4 4 0 0 0 4 4M16 6h4v1a4 4 0 0 1-4 4",
    ),
)

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0Z"

/** Port of the design's 24x24 line icon set (stroke 1.8, round caps). */
@Composable
fun BIcon(name: String, size: Float, tint: Color, modifier: Modifier = Modifier, strokeWidth: Float = 1.8f) {
    val paths: List<Path> = remember(name) {
        (iconPaths[name] ?: emptyList()).map { PathParser().parsePathString(it).toPath() }
    }
    Canvas(modifier.size(size.dp)) {
        val scale = this.size.width / 24f
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            paths.forEach {
                drawPath(
                    it,
                    tint,
                    style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

