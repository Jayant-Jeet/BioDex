package com.biodex.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.hypot

@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1500)
        onDone()
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                Modifier.padding(top = 7.dp, bottom = 66.dp), family = SerifItalic, italic = true,
            )
            Box(
                Modifier
                    .width(132.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color(0x1FFFFFFF)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.5f)
                        .height(3.dp)
                        .offset(x = (132 * bar).dp)
                        .background(BColor.Amber, CircleShape),
                )
            }
            BText(
                "GROWING YOUR COLLECTION…", 9f, Color(0xFF7FA087),
                Modifier.padding(top = 14.dp), weight = 600, spacing = 0.9f, align = TextAlign.Center,
            )
        }
    }
}

@Composable
fun Wordmark(size: Float, color: Color, modifier: Modifier = Modifier, spacing: Float = -0.5f) {
    androidx.compose.foundation.layout.Row(modifier) {
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

private fun Modifier.drawBehindCompat(block: androidx.compose.ui.graphics.drawscope.DrawScope.(androidx.compose.ui.geometry.Size) -> Unit) =
    this.drawBehind { block(size) }

