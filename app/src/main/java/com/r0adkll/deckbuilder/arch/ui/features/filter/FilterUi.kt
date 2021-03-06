package com.r0adkll.deckbuilder.arch.ui.features.filter


import com.r0adkll.deckbuilder.arch.domain.Rarity
import com.r0adkll.deckbuilder.arch.domain.features.cards.model.Expansion
import com.r0adkll.deckbuilder.arch.domain.features.cards.model.Filter
import com.r0adkll.deckbuilder.arch.domain.features.cards.model.SearchField
import com.r0adkll.deckbuilder.arch.ui.components.renderers.StateRenderer
import com.r0adkll.deckbuilder.arch.ui.features.filter.FilterUi.State.Change.*
import com.r0adkll.deckbuilder.arch.ui.features.filter.adapter.Item
import io.pokemontcg.model.SubType
import io.pokemontcg.model.SuperType
import io.pokemontcg.model.Type
import io.reactivex.Observable
import paperparcel.PaperParcel
import paperparcel.PaperParcelable


interface FilterUi : StateRenderer<FilterUi.State> {

    val state: State


    interface Intentions {

        fun fieldChanges(): Observable<SearchField>
        fun typeClicks(): Observable<Pair<String, Type>>
        fun attributeClicks(): Observable<FilterAttribute>
        fun optionClicks(): Observable<Pair<String, Any>>
        fun viewMoreClicks(): Observable<Unit>
        fun valueRangeChanges(): Observable<Pair<String, Item.ValueRange.Value>>
        fun clearFilter(): Observable<Unit>
    }


    interface Actions {

        fun setItems(items: List<Item>)
        fun setIsEmpty(isEmpty: Boolean)
    }


    enum class ExpansionVisibility(val next: () -> ExpansionVisibility) {
        STANDARD({ EXPANDED }),
        EXPANDED({ UNLIMITED }),
        UNLIMITED({ UNLIMITED })
    }


    sealed class FilterAttribute : PaperParcelable {

        @PaperParcel
        data class SuperTypeAttribute(val superType: SuperType) : FilterAttribute() {
            companion object {
                @JvmField val CREATOR = PaperParcelFilterUi_FilterAttribute_SuperTypeAttribute.CREATOR
            }
        }


        @PaperParcel
        data class SubTypeAttribute(val subType: SubType) : FilterAttribute() {
            companion object {
                @JvmField val CREATOR = PaperParcelFilterUi_FilterAttribute_SubTypeAttribute.CREATOR
            }
        }


        @PaperParcel
        data class ContainsAttribute(val attribute: String) : FilterAttribute() {
            companion object {
                @JvmField val CREATOR = PaperParcelFilterUi_FilterAttribute_ContainsAttribute.CREATOR
            }
        }
    }


    @PaperParcel
    data class FilterState(
            val category: SuperType,
            val spec: FilterSpec,
            val filter: Filter,
            val visibility: ExpansionVisibility
    ) : PaperParcelable {

        fun applySpecification(): List<Item> = spec.apply(filter)


        override fun toString(): String {
            return "FilterState(category=$category, spec=$spec, filter=$filter, visibility=$visibility)"
        }

        companion object {
            @JvmField val CREATOR = PaperParcelFilterUi_FilterState.CREATOR

            fun createDefault(superType: SuperType): FilterState {
                return FilterState(superType, FilterSpec.create(superType, emptyList(), ExpansionVisibility.STANDARD),
                        Filter.DEFAULT, ExpansionVisibility.STANDARD)
            }
        }
    }


    @PaperParcel
    data class State(
            val category: SuperType,
            val filters: Map<SuperType, FilterState>,
            val expansions: List<Expansion>
    ) : PaperParcelable {

        fun reduce(change: Change): State = when(change) {
            is ExpansionsLoaded -> {
                val newFilters = filters.toMutableMap()
                SuperType.values()
                        .forEach {
                            val filterState = newFilters[it]!!
                            newFilters[it] = filterState
                                    .copy(spec = FilterSpec.create(filterState.category, change.expansions, filterState.visibility))
                        }
                this.copy(expansions = change.expansions, filters = newFilters.toMap())
            }

            is CategoryChanged -> {
                this.copy(category = change.category)
            }

            is FieldChanged -> {
                val newFilters = filters.toMutableMap()
                newFilters[category] = newFilters[category]!!
                        .copy(filter = FilterReducer.reduceField(change.field, newFilters[category]!!.filter))
                this.copy(filters = newFilters.toMap())
            }
            is TypeSelected -> {
                val newFilters = filters.toMutableMap()
                newFilters[category] = newFilters[category]!!
                        .copy(filter = FilterReducer.reduceType(change.key, change.type, newFilters[category]!!.filter))
                this.copy(filters = newFilters.toMap())
            }

            is AttributeSelected -> {
                val newFilters = filters.toMutableMap()
                newFilters[category] = newFilters[category]!!
                        .copy(filter = FilterReducer.reduceAttribute(change.attribute, newFilters[category]!!.filter))
                this.copy(filters = newFilters.toMap())
            }

            is ExpansionSelected -> {
                val newFilters = filters.toMutableMap()
                newFilters[category] = newFilters[category]!!
                        .copy(filter = FilterReducer.reduceExpansion(change.expansion, newFilters[category]!!.filter))
                this.copy(filters = newFilters.toMap())
            }

            is RaritySelected -> {
                val newFilters = filters.toMutableMap()
                newFilters[category] = newFilters[category]!!
                        .copy(filter = FilterReducer.reduceRarity(change.rarity, newFilters[category]!!.filter))
                this.copy(filters = newFilters.toMap())
            }

            is ValueRangeChanged -> {
                val newFilters = filters.toMutableMap()
                newFilters[category] = newFilters[category]!!
                        .copy(filter = FilterReducer.reduceValueRange(change.key, change.value, newFilters[category]!!.filter))
                this.copy(filters = newFilters.toMap())
            }

            ViewMoreSelected -> {
                val newFilters = filters.toMutableMap()
                val filterState = newFilters[category]!!
                newFilters[category] = filterState
                        .copy(visibility = filterState.visibility.next(),
                                spec = FilterSpec.create(filterState.category, expansions, filterState.visibility.next()))
                this.copy(filters = newFilters.toMap())
            }

            ClearFilter -> {
                val newFilters = filters.toMutableMap()
                newFilters[category] = newFilters[category]!!.copy(filter = Filter.DEFAULT)
                this.copy(filters = newFilters.toMap())
            }
        }


        override fun toString(): String {
            return "State(category=$category, filters=$filters, expansions=${expansions.size})"
        }


        sealed class Change(val logText: String) {
            class ExpansionsLoaded(val expansions: List<Expansion>) : Change("network -> expansions loaded")
            class CategoryChanged(val category: SuperType) : Change("user -> category changed to $category")

            class FieldChanged(val field: SearchField) : Change("user -> $field was selected")
            class TypeSelected(val key: String, val type: Type) : Change("user -> $type was selected")
            class AttributeSelected(val attribute: FilterAttribute) : Change("user -> $attribute was selected")
            class ExpansionSelected(val expansion: Expansion) : Change("user -> $expansion was selected")
            class RaritySelected(val rarity: Rarity) : Change("user -> $rarity was selected")
            class ValueRangeChanged(val key: String, val value: String) : Change("user -> $key change was changed to $value")
            object ViewMoreSelected : Change("user -> view more expansions selected")
            object ClearFilter : Change("user -> clear filter")
        }


        companion object {
            @JvmField val CREATOR = PaperParcelFilterUi_State.CREATOR

            val DEFAULT by lazy {
                State(SuperType.POKEMON, mapOf(
                        SuperType.POKEMON to FilterState.createDefault(SuperType.POKEMON),
                        SuperType.TRAINER to FilterState.createDefault(SuperType.TRAINER),
                        SuperType.ENERGY to FilterState.createDefault(SuperType.ENERGY),
                        SuperType.UNKNOWN to FilterState.createDefault(SuperType.UNKNOWN)
                ), emptyList())
            }
        }
    }
}