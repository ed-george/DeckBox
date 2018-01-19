package com.r0adkll.deckbuilder.arch.data.features.cards.repository


import com.r0adkll.deckbuilder.arch.data.features.cards.repository.source.CardDataSource
import com.r0adkll.deckbuilder.arch.data.mappings.CardMapper
import com.r0adkll.deckbuilder.arch.data.mappings.FilterMapper
import com.r0adkll.deckbuilder.arch.domain.features.cards.model.Expansion
import com.r0adkll.deckbuilder.arch.domain.features.cards.model.Filter
import com.r0adkll.deckbuilder.arch.domain.features.cards.model.PokemonCard
import com.r0adkll.deckbuilder.arch.domain.features.cards.model.SearchField
import com.r0adkll.deckbuilder.arch.domain.features.cards.repository.CardRepository
import com.r0adkll.deckbuilder.util.Schedulers
import io.pokemontcg.Pokemon
import io.pokemontcg.model.Card
import io.pokemontcg.model.SuperType
import io.pokemontcg.requests.CardQueryBuilder
import io.reactivex.Observable
import io.reactivex.functions.BiFunction
import javax.inject.Inject


class DefaultCardRepository @Inject constructor(
        val api: Pokemon,
        val dataSource: CardDataSource,
        val schedulers: Schedulers
) : CardRepository {

    override fun getExpansions(): Observable<List<Expansion>> {
        return dataSource.getExpansions()
    }


    override fun search(type: SuperType?, text: String, filter: Filter?): Observable<List<PokemonCard>> {
        return Observable.zip(getExpansions(), getSearchRequest(type, text, filter), BiFunction { expansions, cards ->
            cards.map { CardMapper.to(it, expansions) }
        })
    }


    override fun searchIds(ids: List<String>): Observable<List<PokemonCard>> {
        return Observable.zip(getExpansions(), getSearchRequest(ids), BiFunction { expansions, cards ->
            cards.map { CardMapper.to(it, expansions) }
        })
    }


    private fun getSearchRequest(ids: List<String>): Observable<List<Card>> {
        return api.card()
                .where {
                    id = ids.joinToString("|")
                }
                .observeAll()
                .subscribeOn(schedulers.network)
    }


    private fun getSearchRequest(type: SuperType?, query: String, filter: Filter?): Observable<List<Card>> {
        val request = filter?.let {
            FilterMapper.to(it)
        } ?: CardQueryBuilder()

        if (type != null && type != SuperType.UNKNOWN) {
            request.supertype = type.displayName
        }

        if (query.isNotBlank()) {

            // Special case to account for 'N' cards
            val adjustedQuery = if (query.trim().equals("N", true)) {
                "\"${query.trim()}\""
            } else {
                query
            }

            // Set search field accordingly
            when(filter?.field ?: SearchField.NAME) {
                SearchField.NAME -> request.name = adjustedQuery
                SearchField.TEXT -> request.text = adjustedQuery
                SearchField.ABILITY_NAME -> request.abilityName = adjustedQuery
                SearchField.ABILITY_TEXT -> request.abilityText = adjustedQuery
                SearchField.ATTACK_NAME -> request.attackName = adjustedQuery
                SearchField.ATTACK_TEXT -> request.attackText = adjustedQuery
            }
        }

        return api.card()
                .where(request)
                .observeAll()
                .subscribeOn(schedulers.network)
    }
}