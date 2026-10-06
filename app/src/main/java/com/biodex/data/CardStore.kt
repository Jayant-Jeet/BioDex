package com.biodex.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class CardStore(context: Context) {
    private val preferences = context.getSharedPreferences("biodex_collection", Context.MODE_PRIVATE)

    fun load(): List<CollectionCard> {
        val cards = JSONArray(preferences.getString(KEY_CARDS, "[]") ?: "[]")
        return List(cards.length()) { index ->
            val card = cards.getJSONObject(index)
            CollectionCard(
                id = card.getString("id"),
                speciesId = card.getString("speciesId"),
                similarity = if (card.has("similarity")) card.getDouble("similarity").toFloat() else null,
                quality = card.getInt("quality"),
                capturedAt = card.getLong("capturedAt"),
                photoPath = card.getString("photoPath"),
                cardPath = card.getString("cardPath"),
                shortDetail = card.optString("shortDetail").takeIf { it.isNotBlank() },
                location = card.optString("location").takeIf { it.isNotBlank() },
                xp = card.optInt("xp"),
            )
        }.filter { it.cardPath.isNotBlank() }
    }

    fun save(card: CollectionCard) {
        val cards = JSONArray()
        load().filterNot { it.id == card.id }.forEach { cards.put(it.toJson()) }
        cards.put(card.toJson())
        check(preferences.edit().putString(KEY_CARDS, cards.toString()).commit()) {
            "Could not save the collection card."
        }
    }

    private fun CollectionCard.toJson() = JSONObject().apply {
        put("id", id)
        put("speciesId", speciesId)
        similarity?.let { put("similarity", it.toDouble()) }
        put("quality", quality)
        put("capturedAt", capturedAt)
        put("photoPath", photoPath)
        put("cardPath", cardPath)
        shortDetail?.let { put("shortDetail", it) }
        location?.let { put("location", it) }
        put("xp", xp)
    }

    private companion object {
        const val KEY_CARDS = "cards"
    }
}

