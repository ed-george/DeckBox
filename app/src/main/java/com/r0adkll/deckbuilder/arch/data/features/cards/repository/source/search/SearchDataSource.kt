package com.r0adkll.deckbuilder.arch.data.features.cards.repository.source.search


import com.r0adkll.deckbuilder.arch.domain.features.cards.model.Filter
import com.r0adkll.deckbuilder.arch.domain.features.cards.model.PokemonCard
import io.pokemontcg.model.SuperType
import io.reactivex.Observable


interface SearchDataSource {

    fun search(type: SuperType?, query: String, filter: Filter? = null): Observable<List<PokemonCard>>
    fun find(ids: List<String>): Observable<List<PokemonCard>>
}