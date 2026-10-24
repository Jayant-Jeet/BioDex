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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import com.biodex.data.CardSortOption
import com.biodex.data.CardSortStore
import com.biodex.data.CollectionCard
import com.biodex.data.Progress
import com.biodex.data.Rarity
import com.biodex.data.Species
import com.biodex.data.TOTAL_PLANTS
import com.biodex.data.XP_PER_LEVEL
import com.biodex.data.sortedCards
import com.biodex.util.PhotoBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    val sortStore = remember(context) { CardSortStore(context) }
    var sortOption by remember(sortStore) { mutableStateOf(sortStore.load()) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    val cardSnapshot = cards.toList()
    val rows by produceState(
        initialValue = emptyList<List<Pair<CollectionCard, Int>>>(),
        cardSnapshot,
        query,
        sortOption,
        speciesById,
    ) {
        value = withContext(Dispatchers.Default) {
            val numbered = cardSnapshot
                .sortedWith(compareBy<CollectionCard> { it.capturedAt }.thenBy { it.id })
                .mapIndexed { index, card -> card to index + 1 }
            val q = query.trim().lowercase()
            val filtered = numbered.filter { (card, _) ->
                val item = speciesById[card.speciesId]
                q.isEmpty() || (item != null &&
                    (item.name.lowercase().contains(q) || item.scientificName.lowercase().contains(q)))
            }
            val numberedById = filtered.associate { it.first.id to it }
            sortedCards(filtered.map { it.first }, speciesById, sortOption)
                .mapNotNull { numberedById[it.id] }
                .chunked(2)
        }
    }

    Box(Modifier.fillMaxSize().background(BColor.Outer), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .widthIn(max = BioDexTheme.Size.DashboardMaxWidth)
                .fillMaxSize()
                .background(BColor.Page)
                .drawBehind {
                    drawCircle(Color(0xBFC7DDAE), 175.dp.toPx(), Offset(size.width + 10.dp.toPx(), 4.dp.toPx()))
                    val spacing = 22.dp.toPx()
                    var y = 8.dp.toPx()
                    while (y < size.height) {
                        var x = 8.dp.toPx()
                        while (x < size.width) {
                            drawCircle(Color(0x18315942), 1.dp.toPx(), Offset(x, y))
                            x += spacing
                        }
                        y += spacing
                    }
                },
        ) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Column(
                        Modifier
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = BioDexTheme.Size.PageHorizontalPadding),
                    ) {
                        FigmaHeader(progress.levelName)
                        FigmaIntro()
                        ExplorerCard(progress)
                        FieldStats(progress.activeStreak(today), cards.size, progress.xp)
                        CollectionHeader(
                            cards = cards,
                            sortOption = sortOption,
                            menuExpanded = sortMenuExpanded,
                            onMenuExpanded = { sortMenuExpanded = it },
                            onSort = { selected ->
                                try {
                                    sortStore.save(selected)
                                    sortOption = selected
                                } catch (exception: IllegalStateException) {
                                    android.widget.Toast.makeText(
                                        context,
                                        exception.localizedMessage ?: "Could not save the sort preference.",
                                        android.widget.Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
                        )
                        FigmaSearchRow(query) { query = it }
                        Box(Modifier.height(BioDexTheme.Size.CardGridGap))
                    }
                }
                items(rows, key = { row -> row.first().first.id }) { row ->
                    Row(
                        Modifier.padding(horizontal = BioDexTheme.Size.PageHorizontalPadding)
                            .padding(bottom = BioDexTheme.Size.CardGridGap),
                        horizontalArrangement = Arrangement.spacedBy(BioDexTheme.Size.CardGridGap),
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
                    if (rows.isEmpty()) {
                        BText(
                            if (cards.isEmpty()) "Your field log is waiting for its first discovery." else "No captured plants match your search.",
                            13f, BioDexTheme.Color.TextMuted,
                            Modifier.fillMaxWidth().padding(horizontal = 30.dp, vertical = 24.dp),
                            align = TextAlign.Center, lineHeight = 19f,
                        )
                    }
                    Box(Modifier.height(106.dp))
                }
            }
            CaptureFloatingActionButton(Modifier.align(Alignment.BottomEnd), onScan)
        }
    }
}

@Composable
private fun FigmaHeader(rank: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(36.dp).rotate(-3f)
                    .background(
                        cssGradient(145f, 0f to BioDexTheme.Color.Forest, 1f to BioDexTheme.Color.ForestDeep),
                        RoundedCornerShape(12.dp, 12.dp, 12.dp, 4.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) { BIcon("leaf", 21f, Color(0xFFF8F5E9)) }
            Wordmark(25f, BioDexTheme.Color.Ink)
        }
        Row(
            Modifier.background(Color(0xC2FFFFFF), CircleShape)
                .border(1.dp, Color(0x1F224E33), CircleShape)
                .padding(start = 5.dp, top = 5.dp, bottom = 5.dp, end = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(29.dp)
                    .background(cssGradient(145f, 0f to Color(0xFFFFD16D), 1f to Color(0xFFDD8C2E)), CircleShape),
                contentAlignment = Alignment.Center,
            ) { BIcon("trophy", 16f, Color(0xFF4B310F)) }
            Column {
                BText("FIELD RANK", 7f, BColor.Muted, weight = 700, spacing = 0.7f)
                BText(rank, 10f, BioDexTheme.Color.Forest, weight = 700, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FigmaIntro() {
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 13.dp, bottom = 17.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier.size(7.dp)
                    .background(BioDexTheme.Color.Amber, CircleShape)
                    .border(2.dp, Color(0xFFF6EFE0), CircleShape),
            )
            BText("FIELD LOG · EXPEDITION 14", 9f, Color(0xFF66806C), weight = 700, spacing = 1.5f)
        }
        BText(
            "Ready to explore?", 42f, BioDexTheme.Color.Ink,
            Modifier.padding(top = 7.dp), family = Serif, lineHeight = 44f, spacing = -1.2f, maxLines = 1,
        )
        BText(
            "The wild is full of discoveries. Scan a plant to add its card to your BioDex.",
            12f, BioDexTheme.Color.TextMuted,
            Modifier.padding(top = 9.dp), lineHeight = 17f,
        )
    }
}

@Composable
private fun ExplorerCard(progress: Progress) {
    val progressFraction = (progress.levelXp / XP_PER_LEVEL.toFloat()).coerceIn(0f, 1f)
    val cardShape = RoundedCornerShape(26.dp)
    Box(
        Modifier.fillMaxWidth()
            .shadow(
                18.dp,
                cardShape,
                ambientColor = Color(0x3B143925),
                spotColor = Color(0x3B143925),
            )
            .clip(cardShape)
            .drawBehind {
                drawRect(cssGradient(145f, 0f to Color(0xFF1B4932), 0.76f to Color(0xFF0B2B1C)))
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0x21E19D3B), Color.Transparent),
                        center = Offset(size.width * 0.08f, size.height * 1.05f),
                        radius = size.width * 0.32f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color(0x3BC1DE91), Color.Transparent),
                        center = Offset(size.width * 0.91f, size.height * 0.05f),
                        radius = size.width * 0.28f,
                    ),
                )
            }
            .border(1.dp, Color(0x4DEFC266), cardShape),
    ) {
        Column(Modifier.padding(start = 20.dp, top = 21.dp, end = 20.dp, bottom = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BIcon("compass", 14f, Color(0xFFE8B65E))
                        BText("EXPLORER LEVEL", 8f, Color(0xFFE8B65E), weight = 700, spacing = 1.3f)
                    }
                    BText(progress.levelName, 30f, Color(0xFFF6F4E9), Modifier.padding(top = 7.dp), family = Serif, lineHeight = 30f)
                    androidx.compose.material3.Text(
                        text = buildAnnotatedString {
                            append("Keep discovering to unlock ")
                            withStyle(SpanStyle(color = Color(0xFFF1D393), fontWeight = FontWeight.SemiBold)) {
                                append(progress.nextLevelName)
                            }
                            append(".")
                        },
                        modifier = Modifier.padding(top = 8.dp).widthIn(max = 240.dp),
                        style = TextStyle(
                            color = Color(0xFFB7C8BB),
                            fontFamily = Sans,
                            fontSize = 9.sp,
                            lineHeight = 13.sp,
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                        ),
                    )
                }
                LevelOrbit(progress.level, progressFraction)
            }
            Column(Modifier.fillMaxWidth().padding(top = 9.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x17FFFFFF)))
                Row(
                    Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            Modifier.size(5.dp)
                                .background(Color(0xFFAACA8E), CircleShape)
                                .border(1.dp, Color(0x8CAACA8E), CircleShape),
                        )
                        BText("NEXT · ${progress.nextLevelName.uppercase()}", 7f, Color(0xFF89A191), weight = 700, spacing = 1f)
                    }
                    Row {
                        BText("${fmt(progress.levelXp)} ", 9f, Color(0xFFF4D28D), weight = 700)
                        BText("/ ${fmt(XP_PER_LEVEL)} XP", 9f, Color(0xFF8FA496))
                    }
                }
                Box(
                    Modifier.fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(Color(0x1AFFFFFF))
                        .border(1.dp, Color(0x0AFFFFFF), CircleShape)
                        .padding(1.dp),
                ) {
                    Box(Modifier.fillMaxWidth(progressFraction).fillMaxHeight()) {
                        Box(
                            Modifier.fillMaxSize()
                                .clip(CircleShape)
                                .background(cssGradient(90f, 0f to Color(0xFFE68B34), 1f to Color(0xFFFFD16A))),
                        )
                        if (progressFraction > 0f) {
                            Box(
                                Modifier.align(Alignment.CenterEnd)
                                    .offset(x = 4.dp)
                                    .size(10.dp)
                                    .border(2.dp, Color(0xFF234B35), CircleShape)
                                    .background(Color(0xFFFFD676), CircleShape),
                            )
                        }
                    }
                }
            }
        }
        Box(
            Modifier.align(Alignment.TopStart)
                .padding(start = 20.dp)
                .width(72.dp)
                .height(3.dp)
                .background(
                    cssGradient(90f, 0f to Color(0xFFE29135), 1f to Color(0xFFF5CF75)),
                    RoundedCornerShape(bottomStart = 99.dp, bottomEnd = 99.dp),
                ),
        )
    }
}

@Composable
private fun LevelOrbit(level: Int, fraction: Float) {
    Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.requiredSize(82.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val stroke = 5.dp.toPx()
            val radius = 32.dp.toPx()
            drawCircle(Color(0x0AFFFFFF), radius, center)
            drawCircle(Color(0x14FFFFFF), 30.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            drawCircle(
                Color(0x1FEFC165),
                40.dp.toPx(),
                center,
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx()))),
            )
            drawCircle(Color(0x1FFFFFFF), radius, center, style = Stroke(stroke))
            drawArc(
                Color(0xFFEFB54F), -90f, 360f * fraction.coerceIn(0f, 1f), false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            BText("LVL", 7f, Color(0xFF9DB2A3), weight = 700, spacing = 1f)
            BText("%02d".format(level), 24f, Color(0xFFFFF8E8), family = Serif, lineHeight = 25f)
        }
    }
}

@Composable
private fun FieldStats(streak: Int, cards: Int, xp: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldStat(Modifier.weight(1f), "spark", "$streak ${if (streak == 1) "day" else "days"}", "Explorer streak", Color(0xFFFFEAD2), Color(0xFFB8682D))
        FieldStat(Modifier.weight(1f), "leaf", "$cards", "Cards found", Color(0xFFDCEBDD), Color(0xFF34714D))
        FieldStat(Modifier.weight(1f), "trophy", fmt(xp), "Total XP", Color(0xFFF5E9C6), Color(0xFF806023))
    }
}

@Composable
private fun FieldStat(
    modifier: Modifier,
    icon: String,
    value: String,
    label: String,
    background: Color,
    tint: Color,
) {
    Row(
        modifier.heightIn(min = 52.dp)
            .background(Color(0xD1FFFFFF), RoundedCornerShape(17.dp))
            .border(1.dp, BioDexTheme.Color.Border, RoundedCornerShape(17.dp))
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(29.dp).background(background, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
            BIcon(icon, 17f, tint)
        }
        Column(Modifier.weight(1f)) {
            BText(value, 11f, Color(0xFF284D38), weight = 700, maxLines = 1)
            BText(label, 7f, Color(0xFF8B988F), maxLines = 1)
        }
    }
}

@Composable
private fun CollectionHeader(
    cards: List<CollectionCard>,
    sortOption: CardSortOption,
    menuExpanded: Boolean,
    onMenuExpanded: (Boolean) -> Unit,
    onSort: (CardSortOption) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 27.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            BText("Your BioDex", 28f, Color(0xFF173B29), family = Serif, lineHeight = 31f)
            BText("${cards.size} captured specimen cards", 9f, Color(0xFF86958B), Modifier.padding(top = 4.dp), spacing = 1f)
        }
        Box {
            Row(
                Modifier.background(Color(0xBFFFFFFF), RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFDCE4D9), RoundedCornerShape(12.dp))
                    .pressable { onMenuExpanded(true) }
                    .padding(start = 11.dp, end = 9.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BText(sortOption.label, 9f, Color(0xFF55715E), weight = 700, maxLines = 1)
                Box(Modifier.rotate(90f)) { BIcon("chevron", 14f, Color(0xFF55715E)) }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuExpanded(false) }) {
                CardSortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { BText(option.label, 12f, BioDexTheme.Color.Ink) },
                        onClick = {
                            onSort(option)
                            onMenuExpanded(false)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FigmaSearchRow(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier.padding(vertical = 14.dp).fillMaxWidth().height(45.dp)
            .background(Color(0xBFFFFFFF), RoundedCornerShape(14.dp))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        BIcon("search", 19f, Color(0xFF4C6D58))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) BText("Search your discoveries", 12f, BColor.Muted)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = TextStyle(fontFamily = Sans, fontSize = 12.sp, color = BColor.Ink),
                cursorBrush = SolidColor(BColor.Forest),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DashboardPreview() {
    MaterialTheme {
        Surface { Dashboard(emptyList(), emptyMap(), Progress(), 0L, { _, _ -> }, {}) }
    }
}

@Preview(showBackground = true, widthDp = 390)
@Composable
private fun ExplorerCardPreview() {
    MaterialTheme {
        Surface(Modifier.padding(16.dp)) {
            ExplorerCard(Progress(xp = 2_620))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DashboardCollectionPreview() {
    val basil = Species("basil", "Sweet basil", "Ocimum basilicum", "Common")
    val fern = Species("fern", "Bird's nest fern", "Asplenium nidus", "Uncommon")
    val cards = listOf(
        CollectionCard("preview-basil", basil.id, 0.94f, 86, 1_782_000_000_000, "", ""),
        CollectionCard("preview-fern", fern.id, 0.88f, 72, 1_781_900_000_000, "", ""),
    )
    MaterialTheme {
        Surface {
            Dashboard(
                cards = cards,
                speciesById = listOf(basil, fern).associateBy { it.id },
                progress = Progress(xp = 1_245, streak = 3),
                today = 0L,
                onOpen = { _, _ -> },
                onScan = {},
            )
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
private fun SearchRow(query: String, onQuery: (String) -> Unit) {
    Row(
        Modifier.padding(top = 17.dp, bottom = 27.dp).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
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
    }
}

@Composable
fun PlantCard(card: CollectionCard, species: Species, number: Int, onClick: () -> Unit) {
    val rarity = Rarity.of(species)
    val bitmap = remember(card.photoPath) {
        runCatching { PhotoBitmap.decode(card.photoPath, sampleSize = 4).asImageBitmap() }.getOrNull()
    }
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(9.dp, RoundedCornerShape(BioDexTheme.Size.CardCornerRadius), ambientColor = Color(0x1727431F), spotColor = Color(0x1727431F))
            .background(Color.White, RoundedCornerShape(BioDexTheme.Size.CardCornerRadius))
            .border(1.dp, Color(0x1F1F4B31), RoundedCornerShape(BioDexTheme.Size.CardCornerRadius))
            .pressable(onClick)
            .clip(RoundedCornerShape(BioDexTheme.Size.CardCornerRadius)),
    ) {
        Box(Modifier.fillMaxWidth().height(BioDexTheme.Size.CardImageHeight).background(Color(0xFFDCE8D8))) {
            if (bitmap != null) {
                Image(bitmap, species.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { BIcon("leaf", 52f, Color(0xFF285B3C)) }
            }
            Box(
                Modifier.fillMaxSize().background(
                    cssGradient(
                        180f,
                        0f to Color(0x5C081B10),
                        0.38f to Color.Transparent,
                        1f to Color(0x1408180F),
                    ),
                ),
            )
            Row(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.background(Color(0x730A2315), RoundedCornerShape(7.dp))
                        .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(7.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                ) { BText("#%04d".format(number), 8f, Color(0xFFF4F5EB), weight = 700) }
                FigmaRarityBadge(rarity)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                BText(species.name, 11f, Color(0xFF284D38), weight = 700, maxLines = 1)
                BText(species.scientificName, 8f, Color(0xFF8B988F), Modifier.padding(top = 2.dp), family = SerifItalic, italic = true, maxLines = 1)
                BText(
                    "Captured ${SimpleDateFormat("MMM d", Locale.US).format(Date(card.capturedAt))}",
                    7f, Color(0xFF7C8F82), Modifier.padding(top = 5.dp), weight = 600, spacing = 0.2f,
                )
            }
            Box(
                Modifier.background(Color(0xFFFFF0D7), RoundedCornerShape(7.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp),
            ) { BText("+${cardXp(card, species)} XP", 7f, Color(0xFFA85C26), weight = 700) }
        }
    }
}

@Composable
private fun FigmaRarityBadge(rarity: Rarity) {
    val (fg, bg, stroke) = when (rarity) {
        Rarity.Common -> Triple(Color(0xFFF7F7EB), Color(0xB90B2717), Color(0x38FFFFFF))
        Rarity.Uncommon -> Triple(Color(0xFFD7FFE7), Color(0xB9145539), Color(0x38FFFFFF))
        Rarity.Rare -> Triple(Color(0xFFFFE398), Color(0xB8583008), Color(0x8CFFD56F))
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
private fun CaptureFloatingActionButton(modifier: Modifier = Modifier, onScan: () -> Unit) {
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
            .padding(end = 20.dp, bottom = 22.dp)
            .height(68.dp)
            .shadow(15.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x40122E1C), spotColor = Color(0x40122E1C))
            .background(
                cssGradient(145f, 0f to Color(0xFF214F36), 1f to Color(0xFF0E3120)),
                RoundedCornerShape(22.dp),
            )
            .border(1.dp, Color(0x38FFFFFF), RoundedCornerShape(22.dp))
            .pressable(onScan),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.padding(start = 7.dp).size(52.dp)
                .background(cssGradient(145f, 0f to Color(0xFFFFD477), 1f to Color(0xFFDF8A2D)), RoundedCornerShape(17.dp))
                .border(1.dp, Color(0x7CFFEFc2), RoundedCornerShape(17.dp)),
            contentAlignment = Alignment.Center,
        ) { BIcon("camera", 23f, Color(0xFF3D2A0D)) }
        Column(Modifier.padding(start = 11.dp, end = 10.dp)) {
            BText("New discovery", 8f, Color(0xFFDBE4D8))
            BText("Scan a plant", 12f, Color(0xFFFFFAF0), weight = 700)
        }
        Box(Modifier.padding(end = 12.dp)) { BIcon("chevron", 16f, Color.White) }
    }
}
