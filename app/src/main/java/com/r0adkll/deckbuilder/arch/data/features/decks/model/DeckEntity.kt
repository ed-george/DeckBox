package com.r0adkll.deckbuilder.arch.data.features.decks.model


class DeckEntity(
        val name: String = "",
        val description: String = "",
        val image: String? = null,
        val cards: List<PokemonCardEntity> = emptyList(),
        val timestamp: Long = 0L
)