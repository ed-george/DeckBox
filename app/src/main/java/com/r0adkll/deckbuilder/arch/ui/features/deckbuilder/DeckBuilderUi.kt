package com.r0adkll.deckbuilder.arch.ui.features.deckbuilder


import com.r0adkll.deckbuilder.arch.domain.features.cards.model.PokemonCard
import com.r0adkll.deckbuilder.arch.domain.features.cards.model.StackedPokemonCard
import com.r0adkll.deckbuilder.arch.domain.features.decks.model.Deck
import com.r0adkll.deckbuilder.arch.domain.features.validation.model.Validation
import com.r0adkll.deckbuilder.arch.ui.components.renderers.StateRenderer
import io.pokemontcg.model.SuperType.*
import io.reactivex.Observable
import paperparcel.PaperParcel
import paperparcel.PaperParcelable


interface DeckBuilderUi : StateRenderer<DeckBuilderUi.State>{

    val state: State


    interface Intentions {

        fun saveDeck(): Observable<Unit>
        fun addCards(): Observable<List<PokemonCard>>
        fun removeCard(): Observable<PokemonCard>
        fun editCards(): Observable<List<PokemonCard>>
        fun editDeckClicks(): Observable<Boolean>
        fun editDeckName(): Observable<String>
        fun editDeckDescription(): Observable<String>
    }


    interface Actions {

        fun showBrokenRules(errors: List<Int>)
        fun showIsStandard(isStandard: Boolean)
        fun showIsExpanded(isExpanded: Boolean)
        fun showIsSaving(isSaving: Boolean)
        fun showIsEditing(isEditing: Boolean)
        fun showError(description: String)
        fun showSaveAction(hasChanges: Boolean)
        fun showCardCount(count: Int)
        fun showPokemonCards(cards: List<StackedPokemonCard>)
        fun showTrainerCards(cards: List<StackedPokemonCard>)
        fun showEnergyCards(cards: List<StackedPokemonCard>)
        fun showDeckName(name: String)
        fun showDeckDescription(description: String)
    }


    @PaperParcel
    data class State(
            val isSaving: Boolean,
            val isEditing: Boolean,
            val error: String?,
            val deck: Deck?,
            val pokemonCards: List<PokemonCard>,
            val trainerCards: List<PokemonCard>,
            val energyCards: List<PokemonCard>,
            val name: String?,
            val description: String?,
            val validation: Validation
    ) : PaperParcelable {

        val hasChanged: Boolean
            get() {
                return if (deck == null) {
                    pokemonCards.isNotEmpty() ||
                            trainerCards.isNotEmpty() ||
                            energyCards.isNotEmpty() ||
                            !name.isNullOrBlank() ||
                            !description.isNullOrBlank()
                } else {
                    fun List<PokemonCard>.equal(cards: List<PokemonCard>): Boolean {
                        return this.sortedBy { card -> card.nationalPokedexNumber } ==
                                cards.sortedBy { card -> card.nationalPokedexNumber }
                    }

                    !deck.cards.equal(allCards) ||
                            deck.cards.size != allCards.size ||
                            deck.name != name ||
                            deck.description != description
                }
            }

        val allCards: List<PokemonCard>
            get() = pokemonCards.plus(trainerCards).plus(energyCards)


        fun reduce(change: Change): State = when(change) {
            Change.Saving -> this.copy(isSaving = true, error = null)
            is Change.Editing -> this.copy(isEditing = change.isEditing)
            is Change.Error -> this.copy(error = change.description)
            is Change.AddCards -> {
                val pokemons = change.cards.filter { it.supertype == POKEMON }
                val trainers = change.cards.filter { it.supertype == TRAINER }
                val energies = change.cards.filter { it.supertype == ENERGY }
                this.copy(pokemonCards = pokemonCards.plus(pokemons),
                        trainerCards = trainerCards.plus(trainers),
                        energyCards = energyCards.plus(energies))
            }
            is Change.RemoveCard -> when(change.card.supertype) {
                POKEMON -> this.copy(pokemonCards = pokemonCards.minus(change.card))
                TRAINER -> this.copy(trainerCards = trainerCards.minus(change.card))
                ENERGY -> this.copy(energyCards = energyCards.minus(change.card))
                UNKNOWN -> this
            }
            is Change.EditCards -> this.copy(
                    pokemonCards = change.cards.filter { it.supertype == POKEMON },
                    trainerCards = change.cards.filter { it.supertype == TRAINER },
                    energyCards = change.cards.filter { it.supertype == ENERGY })
            is Change.EditName -> this.copy(name = change.name)
            is Change.EditDescription -> this.copy(description = change.description)
            is Change.DeckUpdated -> this.copy(deck = change.deck, isSaving = false, error = null)
            is Change.Validated -> this.copy(validation = change.validation)
        }

        override fun toString(): String {
            return "State(isSaving=$isSaving, isEditing=$isEditing, error=$error, deck=$deck, pokemonCards=${pokemonCards.size}, trainerCards=${trainerCards.size}, energyCards=${energyCards.size}, name=$name, description=$description, validation=$validation)"
        }


        sealed class Change(val logText: String) {
            object Saving : Change("user -> is saving deck")
            class Editing(val isEditing: Boolean) : Change("user -> is editing: $isEditing")
            class Error(val description: String) : Change("error -> $description")
            class AddCards(val cards: List<PokemonCard>) : Change("user -> added ${cards.size} cards")
            class RemoveCard(val card: PokemonCard) : Change("user -> removing ${card.name}")
            class EditCards(val cards: List<PokemonCard>) : Change("user -> edited all cards: ${cards.size}")
            class EditName(val name: String) : Change("user -> name changed $name")
            class EditDescription(val description: String) : Change ("user -> desc changed $description")
            class DeckUpdated(val deck: Deck) : Change("cache -> Deck changed/updated $deck")
            class Validated(val validation: Validation) : Change("cache -> validated: $validation")
        }

        companion object {
            @JvmField val CREATOR = PaperParcelDeckBuilderUi_State.CREATOR

            val DEFAULT by lazy {
                State(false, false, null, null, emptyList(), emptyList(), emptyList(), null, null, Validation(false, false, emptyList()))
            }
        }
    }
}