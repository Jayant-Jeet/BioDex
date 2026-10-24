package com.biodex.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CardSortTest {
    private val species = mapOf(
        "fern" to Species("fern", "Bird's nest fern", "Asplenium nidus", "Uncommon"),
        "basil" to Species("basil", "Sweet basil", "Ocimum basilicum", "Common"),
        "monstera" to Species("monstera", "Swiss cheese", "Monstera deliciosa", "Rare"),
    )

    @Test
    fun newestAndOldestSortsAreStableForEqualCaptureTimes() {
        val cards = listOf(
            card("fern-1", "fern", 10L),
            card("basil", "basil", 10L),
            card("monstera", "monstera", 20L),
        )

        assertEquals(
            listOf("monstera", "fern-1", "basil"),
            sortedCards(cards, species, CardSortOption.NEWEST).map { it.id },
        )
        assertEquals(
            listOf("fern-1", "basil", "monstera"),
            sortedCards(cards, species, CardSortOption.OLDEST).map { it.id },
        )
    }

    @Test
    fun nameSortsIgnoreCaseAndKeepStableOrderForEqualNames() {
        val cards = listOf(
            card("fern-1", "fern", 3L),
            card("basil", "basil", 2L),
            card("fern-2", "fern", 1L),
        )

        assertEquals(
            listOf("fern-1", "fern-2", "basil"),
            sortedCards(cards, species, CardSortOption.NAME_ASCENDING).map { it.id },
        )
        assertEquals(
            listOf("basil", "fern-1", "fern-2"),
            sortedCards(cards, species, CardSortOption.NAME_DESCENDING).map { it.id },
        )
    }

    @Test
    fun emptyListStaysEmpty() {
        assertEquals(emptyList<CollectionCard>(), sortedCards(emptyList(), species, CardSortOption.NEWEST))
    }

    @Test
    fun unknownSpeciesNamesSortConsistently() {
        val cards = listOf(card("known", "fern", 1L), card("unknown", "missing", 2L))

        assertEquals(
            listOf("unknown", "known"),
            sortedCards(cards, species, CardSortOption.NAME_ASCENDING).map { it.id },
        )
        assertEquals(
            listOf("known", "unknown"),
            sortedCards(cards, species, CardSortOption.NAME_DESCENDING).map { it.id },
        )
    }

    private fun card(id: String, speciesId: String, capturedAt: Long) = CollectionCard(
        id = id,
        speciesId = speciesId,
        similarity = null,
        quality = 80,
        capturedAt = capturedAt,
        photoPath = "",
        cardPath = "",
    )
}
