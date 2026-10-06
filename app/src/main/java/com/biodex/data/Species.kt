package com.biodex.data

import android.content.Context
import org.json.JSONArray

data class Species(
    val id: String,
    val name: String,
    val scientificName: String,
    val rarity: String,
    val detail: String? = null,
)

object SpeciesCatalog {
    fun load(context: Context): List<Species> {
        val json = context.assets.open("species.json").bufferedReader().use { it.readText() }
        val items = JSONArray(json)
        return List(items.length()) { index ->
            val item = items.getJSONObject(index)
            Species(
                id = item.getString("id"),
                name = item.getString("name"),
                scientificName = item.getString("scientificName"),
                rarity = item.getString("rarity"),
            )
        }
    }

    fun loadBioClip(context: Context): List<Species> {
        val json = context.assets.open("bioclip/taxa_labels.json")
            .bufferedReader()
            .use { it.readText() }
        val items = JSONArray(json)
        val notes = loadNotes(context)
        val starterSpecies = load(context).associateBy { it.scientificName }
        val slugPattern = Regex("[^a-z0-9]+")

        return List(items.length()) { index ->
            val item = items.getJSONObject(index)
            val scientificName = item.getString("scientific")
            val note = notes[scientificName]
            val commonName = item.optString("common").takeIf { it.isNotBlank() && it != "null" }
                ?: note?.first?.takeIf { it.isNotBlank() }
            val knownSpecies = starterSpecies[scientificName]
            Species(
                id = knownSpecies?.id ?: scientificName
                    .lowercase()
                    .replace(slugPattern, "-")
                    .trim('-'),
                name = commonName ?: knownSpecies?.name ?: scientificName,
                scientificName = scientificName,
                rarity = knownSpecies?.rarity ?: "Field find",
                detail = note?.second,
            )
        }
    }

    // Pairs of (common name, detail) precomputed offline with Gemma 4 by tools/generate_notes.py.
    private fun loadNotes(context: Context): Map<String, Pair<String, String>> = try {
        val root = org.json.JSONObject(
            context.assets.open("bioclip/species_notes.json").bufferedReader().use { it.readText() },
        )
        root.keys().asSequence().associateWith {
            val entry = root.getJSONObject(it)
            entry.optString("common") to entry.optString("detail")
        }
    } catch (exception: java.io.IOException) {
        emptyMap()
    }
}

data class CollectionCard(
    val id: String,
    val speciesId: String,
    val similarity: Float?,
    val quality: Int,
    val capturedAt: Long,
    val photoPath: String,
    val cardPath: String,
    val shortDetail: String? = null,
    val location: String? = null,
    val xp: Int = 0,
)

