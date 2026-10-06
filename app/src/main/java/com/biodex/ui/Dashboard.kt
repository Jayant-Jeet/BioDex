package com.biodex.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biodex.data.CollectionCard
import com.biodex.data.Progress
import com.biodex.data.Rarity
import com.biodex.data.Species
import com.biodex.data.TOTAL_PLANTS
import com.biodex.data.XP_PER_LEVEL
import com.biodex.util.PhotoBitmap
import java.text.NumberFormat
import java.util.Locale

val CardTones = listOf(Color(0xFFEDF5E2), Color(0xFFE6F3EE), Color(0xFFF8EDCE), Color(0xFFF5E9E0))

fun toneOf(species: Species) = CardTones[Math.floorMod(species.scientificName.hashCode(), 4)]

fun cardXp(card: CollectionCard, species: Species) =
    if (card.xp > 0) card.xp else Rarity.of(species).xp

private fun fmt(n: Int) = NumberFormat.getIntegerInstance(Locale.US).format(n)

@Composable
fun Dashboard(
    cards: List<CollectionCard>,
    speciesById: Map<String, Species>,
    progress: Progress,
    today: Long,
    onOpen: (CollectionCard, Int) -> Unit,
    onScan: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var ascending by remember { mutableStateOf(false) }
    var sortKey by remember { mutableStateOf(SortKey.NUMBER) }

    val numbered = remember(cards) {
        cards.sortedBy { it.capturedAt }.mapIndexed { index, card -> card to index + 1 }
    }
    val visible = remember(numbered, query, ascending, sortKey) {
        val q = query.trim().lowercase()
        numbered
            .filter { (card, _) ->
                val s = speciesById[card.speciesId]
                q.isEmpty() || (s != null && (s.name.lowercase().contains(q) || s.scientificName.lowercase().contains(q)))
            }
            .sortedWith(
                when (sortKey) {
                    SortKey.NUMBER, SortKey.DATE -> compareBy { (card, number) -> if (sortKey == SortKey.DATE) card.capturedAt else number.toLong() }
                    SortKey.NAME -> compareBy { (card, _) -> speciesById[card.speciesId]?.name?.lowercase() ?: "" }
                },
            )
            .let { if (ascending) it else it.reversed() }
    }
    val distinct = cards.map { it.speciesId }.toSet().size
    val totalXp = progress.xp

    Box(Modifier.fillMaxSize().background(BColor.Outer), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .background(BColor.Page)
                .drawBehind {
                    drawCircle(Color(0x8CD9E8BC), 130.dp.toPx(), Offset(size.width + 15.dp.toPx(), 194.dp.toPx()))
                },
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(
                        Modifier
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 22.dp),
                    ) {
                        Header(progress.level)
                        Intro(progress.activeStreak(today))
                        LevelCard(progress)
                        ProgressCard(distinct)
                        MissionCard(progress, today, onScan)
                        SearchRow(query, { query = it }, onScan)
                        SectionHead(cards.size, totalXp, ascending, sortKey, { sortKey = sortKey.next() }) { ascending = !ascending }
                        Box(Modifier.height(17.dp))
                    }
                }
                items(visible.chunked(2), key = { row -> row.first().first.id }) { row ->
                    Row(
                        Modifier.padding(horizontal = 22.dp).padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { (card, number) ->
                            val species = speciesById[card.speciesId]
                            if (species != null) {
                                Box(Modifier.weight(1f)) { PlantCard(card, species, number) { onOpen(card, number) } }
                            }
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
                item {
                    if (visible.isEmpty()) {
                        BText(
                            if (cards.isEmpty()) "Nothing here yet — tap Capture plant to start your BioDex." else "No captured plants match your search.",
                            13f, BColor.Muted2,
                            Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 24.dp),
                            align = TextAlign.Center, lineHeight = 19f,
                        )
                    }
                    Box(Modifier.height(110.dp))
                }
            }
            BottomNav(Modifier.align(Alignment.BottomCenter), onScan)
        }
    }
}

@Composable
private fun Header(level: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(38.dp).rotate(-3f).background(BColor.Forest, RoundedCornerShape(13.dp, 13.dp, 13.dp, 5.dp)),
                contentAlignment = Alignment.Center,
            ) { BIcon("leaf", 21f, Color(0xFFF8F5E9)) }
            Wordmark(27f, BColor.Ink)
        }
        Row(
            Modifier
                .background(Color(0xB8FFFFFF), CircleShape)
                .border(1.dp, Color(0xFFDCE6D5), CircleShape)
                .padding(start = 6.dp, top = 5.dp, bottom = 5.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(Modifier.size(30.dp).background(Color(0xFFE39438), CircleShape), contentAlignment = Alignment.Center) {
                BIcon("trophy", 16f, Color.White)
            }
            Column {
                BText("FIELD RANK", 7f, BColor.Muted, weight = 700, spacing = 0.7f)
                BText("%02d".format(level), 17f, BColor.Forest, family = Serif, lineHeight = 18f)
            }
        }
    }
}

@Composable
private fun Intro(streak: Int) {
    Column(Modifier.padding(top = 19.dp, bottom = 20.dp, start = 0.dp, end = 0.dp).padding(top = 4.dp - 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BIcon("spark", 13f, BColor.Orange2)
            BText(
                "EXPLORER STREAK · $streak ${if (streak == 1) "DAY" else "DAYS"}",
                11f, BColor.Orange2, weight = 700, spacing = 1.7f,
            )
        }
        BText(
            "Your discoveries,\nleaf by leaf.", 44f, BColor.Ink,
            Modifier.padding(top = 11.dp), family = Serif, spacing = -1.5f, lineHeight = 43f,
        )
        BText(
            "Capture a plant in the wild and keep its specimen card in your personal BioDex.",
            14f, BColor.Muted2, Modifier.padding(top = 15.dp), lineHeight = 21.7f,
        )
    }
}

@Composable
private fun LevelCard(progress: Progress) {
    Row(
        Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .background(Color(0xD6FFFFFF), RoundedCornerShape(19.dp))
            .border(1.dp, BColor.Border, RoundedCornerShape(19.dp))
            .padding(horizontal = 15.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(48.dp)
                .rotate(-3f)
                .background(Color(0xFF345F41), RoundedCornerShape(15.dp))
                .border(2.dp, Color(0xFFEFB650), RoundedCornerShape(15.dp))
                .padding(3.dp)
                .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.align(Alignment.Center).offset(y = (-2).dp)) { BIcon("trophy", 30f, Color(0x73F8E8BD)) }
            BText("${progress.level}", 20f, Color.White, family = Serif)
        }
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                BText("Level ${progress.level} · ${progress.levelName}", 12f, Color(0xFF294F38), weight = 700)
                BText("${fmt(progress.levelXp)} / ${fmt(XP_PER_LEVEL)} XP", 8f, BColor.Muted)
            }
            Box(Modifier.padding(vertical = 7.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0xFFE6EBE2))) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.levelXp / XP_PER_LEVEL.toFloat())
                        .height(6.dp)
                        .background(cssGradient(90f, 0f to Color(0xFFE58C37), 1f to Color(0xFFF3BB56)), CircleShape),
                )
            }
            Row {
                BText("${fmt(progress.xpToNext)} XP until ", 8f, BColor.Muted2)
                BText(progress.nextLevelName, 8f, Color(0xFF294F38), weight = 700)
            }
        }
    }
}

@Composable
private fun ProgressCard(count: Int) {
    val percent = count * 100f / TOTAL_PLANTS
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(23.dp), ambientColor = Color(0x66285B3C), spotColor = Color(0x66285B3C))
            .background(BColor.Forest, RoundedCornerShape(23.dp))
            .padding(start = 18.dp, end = 18.dp, top = 19.dp, bottom = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                BText("COLLECTION PROGRESS", 9f, Color(0xFFB6D3A8), weight = 700, spacing = 1.4f)
                Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.Bottom) {
                    BText(fmt(count), 28f, Color(0xFFF8F5E9), family = Serif, lineHeight = 30f)
                    BText(" / ${fmt(TOTAL_PLANTS)} plants", 13f, Color(0xFFB6C9BB), Modifier.padding(bottom = 3.dp))
                }
            }
            Box(
                Modifier.size(47.dp).background(Color(0x12FFFFFF), CircleShape).border(1.dp, Color(0x24FFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                BText(
                    if (percent >= 10) "${percent.toInt()}%" else "%.1f%%".format(percent),
                    11f, Color(0xFFF3B568), weight = 700,
                )
            }
        }
        Box(Modifier.padding(vertical = 15.dp).padding(bottom = 0.dp).fillMaxWidth().height(6.dp).clip(CircleShape).background(Color(0x1FFFFFFF))) {
            Box(
                Modifier
                    .fillMaxWidth((percent / 100f).coerceAtLeast(0f))
                    .widthIn(min = 5.dp)
                    .height(6.dp)
                    .background(BColor.Amber, CircleShape),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BText("$count ${if (count == 1) "plant" else "plants"} discovered", 10f, Color(0xFFB9CABE))
            BText("${fmt(TOTAL_PLANTS - count)} left to find", 10f, Color(0xFFF4D49E), weight = 600)
        }
    }
}

@Composable
private fun MissionCard(progress: Progress, today: Long, onGo: () -> Unit) {
    val (mission, state) = progress.missionFor(today)
    val (done, completed) = state
    Row(
        Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .background(cssGradient(135f, 0f to Color(0xFFFFF2D3), 1f to Color(0xFFF8E4B5)), RoundedCornerShape(21.dp))
            .border(1.dp, Color(0xFFEAD5AD), RoundedCornerShape(21.dp))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(56.dp).rotate(-4f).background(Color(0xFFD67B32), RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) { BIcon("leaf", 30f, Color(0xFFFDF8E8)) }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 9.dp).size(4.dp).background(Color(0xFFFFDC72), CircleShape))
            Box(Modifier.align(Alignment.BottomStart).padding(bottom = 9.dp, start = 8.dp).size(4.dp).background(Color(0xFFFFDC72), CircleShape))
            Box(Modifier.align(Alignment.TopStart).padding(top = 18.dp, start = 7.dp).size(2.dp).background(Color(0xFFFFDC72), CircleShape))
        }
        Column(Modifier.weight(1f)) {
            BText(
                if (completed) "FIELD MISSION COMPLETE" else "TODAY'S FIELD MISSION",
                7f, Color(0xFFB5672D), weight = 700, spacing = 1f,
            )
            BText(mission.title, 14f, Color(0xFF4C422D), Modifier.padding(top = 3.dp), family = Serif, lineHeight = 16f)
            BText(
                if (mission.target > 1 && !completed) "${mission.subtitle} · $done/${mission.target}" else mission.subtitle,
                8f, Color(0xFF8B7450), Modifier.padding(top = 3.dp), lineHeight = 11f,
            )
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BIcon("spark", 12f, Color(0xFFA85E28))
                BText(
                    if (completed) "Earned +${mission.rewardXp} XP · ${mission.badge}" else "+${mission.rewardXp} XP · ${mission.badge}",
                    8f, Color(0xFFA85E28), weight = 700,
                )
            }
        }
        Box(
            Modifier.size(34.dp).background(if (completed) Color(0xFF3D7967) else Color(0xFFD67B32), CircleShape).pressable(onGo),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) BIcon("spark", 15f, Color.White) else BIcon("chevron", 16f, Color.White, strokeWidth = 2.2f)
        }
    }
}

@Composable
private fun SearchRow(query: String, onQuery: (String) -> Unit, onScan: () -> Unit) {
    Row(
        Modifier.padding(top = 17.dp, bottom = 27.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(51.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
                .border(1.dp, Color(0xFFE2E8DD), RoundedCornerShape(16.dp))
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BIcon("search", 19f, Color(0xFF4C6D58))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) BText("Search captured plants", 13f, BColor.Muted)
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = Sans, fontSize = 13.sp, color = BColor.Ink),
                    cursorBrush = SolidColor(BColor.Forest),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Box(
            Modifier
                .size(51.dp)
                .shadow(10.dp, RoundedCornerShape(16.dp), ambientColor = Color(0x66E58C37), spotColor = Color(0x66E58C37))
                .background(BColor.Orange, RoundedCornerShape(16.dp))
                .pressable(onScan),
            contentAlignment = Alignment.Center,
        ) { BIcon("camera", 21f, Color.White) }
    }
}

private enum class SortKey(val label: String) {
    NUMBER("#"), DATE("Date"), NAME("Name");
    fun next() = entries[(ordinal + 1) % entries.size]
}

@Composable
private fun SectionHead(count: Int, xp: Int, ascending: Boolean, sortKey: SortKey, onKey: () -> Unit, onSort: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Column {
            BText("Captured plants", 27f, BColor.Ink, family = Serif, lineHeight = 30f)
            BText(
                "$count ${if (count == 1) "discovery" else "discoveries"} · ${fmt(xp)} XP earned",
                11f, Color(0xFF87948A), Modifier.padding(top = 4.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE0E7DC), RoundedCornerShape(12.dp))
                    .pressable(onKey)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) { BText(sortKey.label, 11f, Color(0xFF56705E), weight = 600) }
            Row(
                Modifier
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE0E7DC), RoundedCornerShape(12.dp))
                    .pressable(onSort)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BText(if (ascending) "Asc" else "Desc", 11f, Color(0xFF56705E), weight = 600)
                Box(Modifier.rotate(if (ascending) -90f else 90f)) { BIcon("chevron", 13f, Color(0xFF56705E)) }
            }
        }
    }
}

@Composable
fun PlantCard(card: CollectionCard, species: Species, number: Int, onClick: () -> Unit) {
    val rarity = Rarity.of(species)
    val tone = toneOf(species)
    val shape = RoundedCornerShape(19.dp)
    val bitmap = remember(card.photoPath) {
        runCatching { PhotoBitmap.decode(card.photoPath, sampleSize = 4).asImageBitmap() }.getOrNull()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(cssGradient(155f, 0f to tone, 0.65f to Color(0xFFFBFCF8), 1f to Color(0xFFFBFCF8)), shape)
            .border(1.dp, Color(0xFFE2B344), shape)
            .drawBehind {
                drawRoundRect(Color.White, Offset(1.dp.toPx(), 1.dp.toPx()), Size(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()), CornerRadius(18.dp.toPx()), Stroke(2.dp.toPx()))
            }
            .pressable(onClick)
            .padding(start = 12.dp, end = 11.dp, top = 12.dp, bottom = 13.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            BText("#%04d".format(number), 9f, Color(0xFF7B9180), weight = 700, spacing = 0.8f, modifier = Modifier.padding(top = 3.dp))
            RarityBadge(rarity)
        }
        Box(Modifier.fillMaxWidth().height(101.dp).padding(top = 6.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(
                    bitmap, null,
                    Modifier.size(94.dp).clip(RoundedCornerShape(19.dp)).border(3.dp, Color(0xC7FFFFFF), RoundedCornerShape(19.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                BIcon("leaf", 44f, Color(0xFF285B3C))
            }
        }
        BText(species.name, 12f, Color(0xFF294D37), Modifier.padding(top = 8.dp), weight = 700, maxLines = 1)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BText(species.scientificName, 9f, Color(0xFF93A098), Modifier.weight(1f), family = SerifItalic, italic = true, maxLines = 1)
            BText("+${cardXp(card, species)} XP", 8f, BColor.Orange2, Modifier.padding(start = 4.dp), weight = 700)
        }
    }
}

@Composable
fun RarityBadge(rarity: Rarity) {
    val (fg, bg, stroke) = when (rarity) {
        Rarity.Common -> Triple(Color(0xFF57755E), Color(0xBFFFFFFF), Color.Transparent)
        Rarity.Uncommon -> Triple(Color(0xFF3D7967), Color(0xE6DDF2E7), Color.Transparent)
        Rarity.Rare -> Triple(Color(0xFFB46626), Color(0xE6FFEFC7), Color(0x42D67F2A))
    }
    Row(
        Modifier.background(bg, CircleShape).border(1.dp, stroke, CircleShape).padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        BIcon("spark", 10f, fg)
        BText(rarity.label.uppercase(), 7f, fg, weight = 700, spacing = 0.4f)
    }
}

@Composable
private fun BottomNav(modifier: Modifier, onScan: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val ring by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(2400), RepeatMode.Restart), label = "ring")
    Column(modifier.fillMaxWidth().background(Color(0xF0FFFFFC))) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFE6EBE3)))
        Row(
            Modifier.windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)).fillMaxWidth().height(96.dp),
        ) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                BIcon("collection", 23f, BColor.Forest)
                BText("Captured cards", 9f, BColor.Forest, Modifier.padding(top = 4.dp), weight = 600)
            }
            Row(
                Modifier.weight(1f).fillMaxHeight().pressable(onScan),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(50.dp)) {
                        val grow = 9.dp.toPx() * ring
                        drawCircle(Color(0xFFE58C37).copy(alpha = 0.25f * (1 - ring)), 25.dp.toPx() + grow)
                    }
                    Box(
                        Modifier
                            .size(50.dp)
                            .shadow(10.dp, CircleShape, ambientColor = Color(0x4DC6691F), spotColor = Color(0x4DC6691F))
                            .background(BColor.Orange, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { BIcon("camera", 23f, Color(0xFFFFF8EC)) }
                }
                BText("Capture plant", 11f, BColor.Forest, weight = 700)
            }
        }
    }
}

