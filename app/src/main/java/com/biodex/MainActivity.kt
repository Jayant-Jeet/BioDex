package com.biodex

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.biodex.data.BioClipClassifier
import com.biodex.data.CardStore
import com.biodex.data.CollectionCard
import com.biodex.data.Identification
import com.biodex.data.NoConfidentMatchException
import com.biodex.data.PlaceFinder
import com.biodex.data.Progress
import com.biodex.data.ProgressStore
import com.biodex.data.Species
import com.biodex.data.SpeciesCatalog
import com.biodex.ui.BColor
import com.biodex.ui.BText
import com.biodex.ui.CaptureScreen
import com.biodex.ui.CardArtwork
import com.biodex.ui.Dashboard
import com.biodex.ui.SpecimenButton
import com.biodex.ui.SpecimenScreen
import com.biodex.ui.SplashScreen
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val classifierDelegate = lazy { BioClipClassifier(applicationContext) }
    private val classifier by classifierDelegate

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val starterSpecies = SpeciesCatalog.load(this)
        val modelSpecies = SpeciesCatalog.loadBioClip(this)
        val collectionSpecies = (starterSpecies + modelSpecies).distinctBy { it.id }
        setContent {
            MaterialTheme { BioDexApp(collectionSpecies, modelSpecies, classifier) }
        }
    }

    override fun onDestroy() {
        if (classifierDelegate.isInitialized()) classifier.close()
        super.onDestroy()
    }
}

private enum class Destination { SPLASH, HOME, CAPTURE, CLASSIFYING, REVEAL }

private data class PendingFind(
    val identification: Identification,
    val photoPath: String,
    val capturedAt: Long,
    val card: CollectionCard? = null,
    val location: String? = null,
) {
    fun toCard() = card ?: CollectionCard(
        id = UUID.nameUUIDFromBytes("$photoPath-$capturedAt".toByteArray()).toString(),
        speciesId = identification.species.id,
        similarity = identification.similarity,
        quality = identification.quality,
        capturedAt = capturedAt,
        photoPath = photoPath,
        cardPath = "",
        shortDetail = identification.species.detail,
        location = location,
    )
}

@Composable
private fun BioDexApp(species: List<Species>, modelSpecies: List<Species>, classifier: BioClipClassifier) {
    val context = LocalContext.current
    val store = remember { CardStore(context) }
    val progressStore = remember { ProgressStore(context) }
    val scope = rememberCoroutineScope()
    val cards = remember { mutableStateListOf<CollectionCard>() }
    val speciesById = remember(species) { species.associateBy { it.id } }
    var progress by remember { mutableStateOf(progressStore.load()) }
    var destination by remember { mutableStateOf(Destination.SPLASH) }
    var pending by remember { mutableStateOf<PendingFind?>(null) }
    var saved by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Pair<CollectionCard, Int>?>(null) }
    var askedForLocation by remember { mutableStateOf(false) }
    var banner by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        destination = Destination.CAPTURE
    }
    val startScan = {
        if (!PlaceFinder.hasPermission(context) && !askedForLocation) {
            askedForLocation = true
            permissionLauncher.launch(PlaceFinder.PERMISSION)
        } else {
            destination = Destination.CAPTURE
        }
    }
    val toast = { text: String -> Toast.makeText(context, text, Toast.LENGTH_LONG).show() }

    LaunchedEffect(Unit) {
        try {
            cards.addAll(store.load())
        } catch (exception: Exception) {
            toast("Could not read the local collection: ${exception.localizedMessage}")
        }
    }
    LaunchedEffect(banner) {
        if (banner != null) {
            kotlinx.coroutines.delay(3500)
            banner = null
        }
    }

    BackHandler(enabled = viewing != null || destination != Destination.HOME && destination != Destination.SPLASH) {
        when {
            viewing != null -> viewing = null
            else -> destination = Destination.HOME
        }
    }

    Box(Modifier.fillMaxSize().background(BColor.Outer)) {
        val viewed = viewing
        when {
            destination == Destination.SPLASH -> SplashScreen { destination = Destination.HOME }
            destination == Destination.CAPTURE -> CaptureScreen(
                onPhotoCaptured = { path ->
                    destination = Destination.CLASSIFYING
                    scope.launch {
                        try {
                            val identification = withContext(Dispatchers.IO) { classifier.identify(path, modelSpecies) }
                            val find = PendingFind(identification, path, System.currentTimeMillis())
                            pending = find
                            saved = false
                            destination = Destination.REVEAL
                            val place = withContext(Dispatchers.IO) { PlaceFinder.currentPlace(context) }
                            if (pending?.photoPath == find.photoPath) pending = pending?.copy(location = place)
                        } catch (exception: NoConfidentMatchException) {
                            toast(
                                "No confident plant match (similarity ${exception.similarity.asScore()}). " +
                                    "This may not be a plant, or the species isn't in the 4,271-plant list. " +
                                    "Try a closer, well-lit shot of one plant.",
                            )
                            destination = Destination.HOME
                        } catch (exception: Exception) {
                            toast("BioCLIP could not identify this photo: ${exception.localizedMessage ?: "unknown model error"}")
                            destination = Destination.HOME
                        }
                    }
                },
                onBack = { destination = Destination.HOME },
            )
            destination == Destination.CLASSIFYING -> ClassifyingScreen()
            destination == Destination.REVEAL -> pending?.let { find ->
                val card = find.toCard()
                val item = find.identification.species
                val isNew = cards.none { it.speciesId == item.id }
                SpecimenScreen(
                    card = card,
                    species = item,
                    number = if (saved) cards.size.coerceAtLeast(1) else cards.size + 1,
                    onBack = { destination = Destination.HOME },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!saved) {
                            val xp = ProgressStore.discoveryXp(item, isNew)
                            SpecimenButton(
                                if (isNew) "Save to BioDex  +$xp XP" else "Save duplicate  +$xp XP",
                                true, Modifier.fillMaxWidth(),
                            ) {
                                try {
                                    val (updated, award) = progressStore.record(item, isNew, find.capturedAt)
                                    val base = card.copy(xp = award.xp + award.bonusXp)
                                    val file = CardArtwork.render(context, base, item)
                                    val done = base.copy(cardPath = file.absolutePath)
                                    store.save(done)
                                    cards.removeAll { it.id == done.id }
                                    cards.add(0, done)
                                    pending = find.copy(card = done)
                                    progress = updated
                                    saved = true
                                    banner = buildString {
                                        append("+${award.xp} XP")
                                        if (award.bonusXp > 0) append("  ·  Mission “${award.missionTitle}” +${award.bonusXp} XP")
                                        if (award.levelUp) append("  ·  Level up!")
                                    }
                                } catch (exception: Exception) {
                                    toast("Could not save your card: ${exception.localizedMessage}")
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SpecimenButton("Share", false, Modifier.weight(1f)) {
                                shareCard(context, card, item, toast)
                            }
                            SpecimenButton(if (saved) "Done" else "Retake", false, Modifier.weight(1f)) {
                                destination = if (saved) Destination.HOME else Destination.CAPTURE
                            }
                        }
                    }
                }
            } ?: run { destination = Destination.HOME }
            else -> Dashboard(
                cards = cards,
                speciesById = speciesById,
                progress = progress,
                today = ProgressStore.dayOf(System.currentTimeMillis()),
                onOpen = { card, number -> viewing = card to number },
                onScan = startScan,
            )
        }
        if (viewed != null && destination == Destination.HOME) {
            val item = speciesById[viewed.first.speciesId]
            if (item != null) {
                SpecimenScreen(viewed.first, item, viewed.second, onBack = { viewing = null }) {
                    SpecimenButton("Share card", true, Modifier.fillMaxWidth()) {
                        shareCard(context, viewed.first, item, toast)
                    }
                }
            }
        }
        banner?.let {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                    .background(BColor.Forest, RoundedCornerShape(99.dp))
                    .padding(horizontal = 18.dp, vertical = 11.dp),
            ) { BText(it, 12f, Color.White, weight = 700) }
        }
    }
}

private fun shareCard(context: Context, card: CollectionCard, species: Species, report: (String) -> Unit) {
    try {
        val cardFile = if (card.cardPath.isNotBlank() && File(card.cardPath).exists()) {
            File(card.cardPath)
        } else {
            CardArtwork.render(context, card, species)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", cardFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "My BioDex field find: ${species.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share your field card"))
    } catch (exception: Exception) {
        report("Could not share card: ${exception.localizedMessage ?: "unknown error"}")
    }
}

@Composable
private fun ClassifyingScreen() {
    Column(
        Modifier.fillMaxSize().background(Color(0xFF183522)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Color(0xFFEFAD5B))
        BText("Checking the plant field guide…", 20f, Color.White, Modifier.padding(top = 20.dp), family = com.biodex.ui.Serif)
        BText("BioCLIP runs on this device. The photo stays local.", 12f, Color(0xB3FFFFFF), Modifier.padding(top = 8.dp))
    }
}

private fun Float.asScore(): String = String.format(java.util.Locale.ROOT, "%.3f", this)
