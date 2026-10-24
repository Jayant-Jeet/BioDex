package com.biodex.data

import android.content.Context

enum class CardSortOption(val persistedValue: String, val label: String) {
    NEWEST("date-desc", "Newest first"),
    OLDEST("date-asc", "Oldest first"),
    NAME_ASCENDING("name-asc", "Name A–Z"),
    NAME_DESCENDING("name-desc", "Name Z–A");

    companion object {
        fun fromPersisted(value: String?): CardSortOption =
            entries.firstOrNull { it.persistedValue == value } ?: NEWEST
    }
}

class CardSortStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("biodex_collection_settings", Context.MODE_PRIVATE)

    fun load(): CardSortOption =
        CardSortOption.fromPersisted(preferences.getString(KEY_SORT, CardSortOption.NEWEST.persistedValue))

    fun save(option: CardSortOption) {
        check(preferences.edit().putString(KEY_SORT, option.persistedValue).commit()) {
            "Could not save the collection sort preference."
        }
    }

    private companion object {
        const val KEY_SORT = "card_sort"
    }
}

fun sortedCards(
    cards: List<CollectionCard>,
    speciesById: Map<String, Species>,
    option: CardSortOption,
): List<CollectionCard> {
    val comparator = when (option) {
        CardSortOption.NEWEST -> compareByDescending<CollectionCard> { it.capturedAt }
        CardSortOption.OLDEST -> compareBy { it.capturedAt }
        CardSortOption.NAME_ASCENDING -> compareBy(String.CASE_INSENSITIVE_ORDER) {
            speciesById[it.speciesId]?.name.orEmpty()
        }
        CardSortOption.NAME_DESCENDING -> compareByDescending(String.CASE_INSENSITIVE_ORDER) {
            speciesById[it.speciesId]?.name.orEmpty()
        }
    }

    return cards.withIndex()
        .sortedWith(Comparator { left, right ->
            val primary = comparator.compare(left.value, right.value)
            if (primary != 0) primary else left.index.compareTo(right.index)
        })
        .map { it.value }
}
