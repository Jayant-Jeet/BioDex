package com.biodex.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.biodex.data.CollectionCard
import com.biodex.data.Rarity
import com.biodex.data.Species
import com.biodex.util.PhotoBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.hypot

fun formatCapturedAt(millis: Long): String =
    SimpleDateFormat("MMMM d, yyyy, h:mm a", Locale.US).format(Date(millis))
        .replace("AM", "am").replace("PM", "pm")

private val Gold = Color(0xFFF6CF4A)

/** Specimen card screen. [actions] are shown beneath the card (save/retake/share on reveal, share when browsing). */
@Composable
fun SpecimenScreen(
    card: CollectionCard,
    species: Species,
    number: Int,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    val rarity = Rarity.of(species)
    val photo = remember(card.photoPath) {
        runCatching { PhotoBitmap.decode(card.photoPath, sampleSize = 2).asImageBitmap() }.getOrNull()
    }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(cssGradient(155f, 0f to Color(0xFF140C32), 0.58f to Color(0xFF342064), 1f to Color(0xFF201044)))
            .drawBehind {
                val far = hypot(size.width, size.height)
                drawRect(Brush.radialGradient(listOf(Color(0x59B07BFF), Color.Transparent), Offset(size.width * 0.2f, size.height * 0.1f), far * 0.45f))
                drawRect(Brush.radialGradient(listOf(Color(0x40FFB84D), Color.Transparent), Offset(size.width * 0.85f, size.height * 0.8f), far * 0.4f))
                val step = 38.dp.toPx()
                var y = 12.dp.toPx()
                var row = 0
                while (y < size.height) {
                    var x = if (row % 2 == 0) 14.dp.toPx() else 33.dp.toPx()
                    while (x < size.width) {
                        val k = ((x * 7 + y * 13).toInt() % 5)
                        drawCircle(Color(0x70FFFFFF), (0.6f + k * 0.25f).dp.toPx(), Offset(x + k * 3, y + k * 2))
                        x += step
                    }
                    y += step * 0.9f
                    row++
                }
            },
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(start = 14.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.pressable(onBack), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(19.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.androidRotate()) { BIcon("chevron", 19f, Color.White) }
                    }
                    BText("BioDex", 12f, Color.White, weight = 600)
                }
                BText("SPECIMEN #%04d".format(number), 9f, Color(0xFFE9DBA6), weight = 700, spacing = 1.2f)
            }

            Box(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .background(
                        cssGradient(115f, 0f to Color(0xFFFFE276), 0.48f to Color(0xFFDC9224), 0.75f to Color(0xFFFFDF64), 1f to Color(0xFFB87319)),
                        RoundedCornerShape(28.dp),
                    )
                    .border(2.dp, Color(0xFFFFDC5B), RoundedCornerShape(28.dp))
                    .padding(7.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(cssGradient(160f, 0f to Color(0xFF23114C), 0.5f to Color(0xFF3B1B70), 1f to Color(0xFF25114F)), RoundedCornerShape(21.dp))
                        .border(2.dp, Color(0xD1FFE064), RoundedCornerShape(21.dp))
                        .padding(13.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 87.dp)
                            .border(1.5.dp, Color(0xFFF3CF55), RoundedCornerShape(18.dp))
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LeafBadge()
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val nameSize = (screenWidth.value * 0.08f).coerceIn(25f, 39f).let { if (species.name.length > 14) it * 0.72f else it }
                            BText(species.name, nameSize, Color.White, family = Serif, align = TextAlign.Center, lineHeight = nameSize * 1.05f, maxLines = 2)
                            BText(species.scientificName, 16f, Color(0xFFD6C7EC), Modifier.padding(top = 2.dp), family = SerifItalic, italic = true, align = TextAlign.Center, maxLines = 2)
                        }
                        LeafBadge()
                    }

                    Box(
                        Modifier
                            .padding(14.dp)
                            .fillMaxWidth()
                            .height(minOf(screenHeight * 0.43f, 390.dp))
                            .background(Color(0xFF1B0D3F), RoundedCornerShape(22.dp))
                            .border(5.dp, Gold, RoundedCornerShape(22.dp))
                            .padding(5.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .drawBehind {
                                drawRoundRect(Color(0xA3FFF5B5), Offset.Zero, size, CornerRadius(17.dp.toPx()), Stroke(2.dp.toPx()))
                            },
                    ) {
                        if (photo != null) {
                            Image(photo, species.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BIcon("leaf", 64f, Color(0xFFD6C7EC)) }
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 81.dp)
                            .background(Color(0x9C0D0625), RoundedCornerShape(17.dp))
                            .padding(horizontal = 17.dp, vertical = 16.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        BText(
                            card.shortDetail?.takeIf { it.isNotBlank() } ?: species.detail ?: "A plant recorded in your BioDex.",
                            13f, Color(0xFFF8F4FB), lineHeight = 18.5f,
                        )
                    }

                    Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(1.55f)
                                .background(cssGradient(135f, 0f to Color(0xFFFFE16A), 1f to Color(0xFFE69C26)), RoundedCornerShape(14.dp))
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            BText("${rarity.letter} ★ ${rarity.label}", 15f, Color(0xFF271242), weight = 700)
                        }
                        Row(
                            Modifier.weight(0.7f).padding(start = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BText("Quality", 11f, Color(0xFFD7C9E8))
                            BText("${card.quality}", 23f, Color.White, family = Serif)
                        }
                    }

                    Column(
                        Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth()
                            .background(Color(0xB30C0523), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        BText("Captured on ${formatCapturedAt(card.capturedAt)}", 10f, Color(0xFFEEE7F6), lineHeight = 14f)
                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box(Modifier.padding(top = 4.dp).size(3.dp).background(Color(0xFFE9B83D), CircleShape))
                            BText(card.location ?: "Location not recorded", 10f, Color(0xFFEEE7F6), lineHeight = 14f)
                        }
                    }
                }
            }

            BText(
                "Your field discovery is safely stored in the BioDex.", 10f, Color(0xFFA995C9),
                Modifier.padding(top = 16.dp, bottom = 14.dp), align = TextAlign.Center,
            )
            Box(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 24.dp)) { actions() }
        }
    }
}

private fun Modifier.androidRotate(): Modifier = this.rotate(180f)

@Composable
private fun LeafBadge() {
    Box(Modifier.size(40.dp).background(Color(0xFF30B26E), CircleShape), contentAlignment = Alignment.Center) {
        BIcon("leaf", 25f, Color.White)
    }
}

@Composable
fun SpecimenButton(label: String, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .background(if (filled) BColor.Orange else Color(0x1FFFFFFF), RoundedCornerShape(16.dp))
            .border(1.dp, if (filled) BColor.Orange else Color(0x38FFFFFF), RoundedCornerShape(16.dp))
            .pressable(onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) { BText(label, 13f, if (filled) Color.White else Color(0xFFEEE7F6), weight = 700) }
}

