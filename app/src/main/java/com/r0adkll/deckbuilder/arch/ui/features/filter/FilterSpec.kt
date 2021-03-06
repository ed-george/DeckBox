package com.r0adkll.deckbuilder.arch.ui.features.filter


import android.support.annotation.StringRes
import com.r0adkll.deckbuilder.R
import com.r0adkll.deckbuilder.arch.domain.Rarity
import com.r0adkll.deckbuilder.arch.domain.features.cards.model.Expansion
import com.r0adkll.deckbuilder.arch.domain.features.cards.model.Filter
import com.r0adkll.deckbuilder.arch.domain.features.cards.model.SearchField
import com.r0adkll.deckbuilder.arch.ui.features.filter.FilterSpec.Spec.AttributeSpec
import com.r0adkll.deckbuilder.arch.ui.features.filter.FilterUi.ExpansionVisibility.*
import com.r0adkll.deckbuilder.arch.ui.features.filter.FilterUi.FilterAttribute
import com.r0adkll.deckbuilder.arch.ui.features.filter.FilterUi.FilterAttribute.*
import com.r0adkll.deckbuilder.arch.ui.features.filter.adapter.Item
import com.r0adkll.deckbuilder.arch.ui.features.filter.adapter.Item.Option.*
import com.r0adkll.deckbuilder.arch.ui.features.filter.adapter.Item.ValueRange.Modifier.*
import com.r0adkll.deckbuilder.arch.ui.features.filter.adapter.Item.ValueRange.Value
import io.pokemontcg.model.SubType.*
import io.pokemontcg.model.SuperType
import io.pokemontcg.model.Type
import paperparcel.PaperParcel
import paperparcel.PaperParcelable
import timber.log.Timber


@PaperParcel
data class FilterSpec(val specs: List<Spec>) : PaperParcelable {

    fun apply(filter: Filter): List<Item> = specs.flatMap { it.apply(filter) }


    sealed class Spec : PaperParcelable {

        /**
         * Take a filter and apply the spec to it to generate a list of UI Items
         * to be renderered in the RecyclerView
         */
        abstract fun apply(filter: Filter): List<Item>


        override fun toString(): String {
            return javaClass.simpleName

        }


        @PaperParcel
        class FieldSpec : Spec() {

            override fun apply(filter: Filter): List<Item> = listOf(
                    Item.Header(R.string.filter_header_search_field),
                    Item.Field(filter.field)
            )

            companion object {
                @JvmField val CREATOR = PaperParcelFilterSpec_Spec_FieldSpec.CREATOR
            }
        }


        @PaperParcel
        class TypeSpec(val key: String, @StringRes val title: Int) : Spec() {

            override fun apply(filter: Filter): List<Item> = listOf(
                    Item.Header(title),
                    Item.Type(key, getSelected(filter))
            )


            private fun getSelected(filter: Filter): List<Type> = when(key) {
                "type" -> filter.types
                "weaknesses" -> filter.weaknesses
                "resistances" -> filter.resistances
                else -> emptyList()
            }

            companion object {
                @JvmField val CREATOR = PaperParcelFilterSpec_Spec_TypeSpec.CREATOR
            }
        }


        @PaperParcel
        class AttributeSpec(val attributes: List<FilterAttribute>) : Spec() {

            override fun apply(filter: Filter): List<Item> = listOf(
                    Item.Header(R.string.filter_header_attributes),
                    Item.Attribute(attributes, getFilteredAttributes(filter))
            )


            private fun getFilteredAttributes(filter: Filter): List<FilterAttribute> {
                val attrs = ArrayList<FilterAttribute>()
                attrs += filter.subTypes.map { SubTypeAttribute(it) }
                attrs += filter.contains.map { ContainsAttribute(it) }
                filter.superType?.let { attrs += SuperTypeAttribute(it) }
                return attrs
            }


            companion object {
                @JvmField val CREATOR = PaperParcelFilterSpec_Spec_AttributeSpec.CREATOR
            }
        }


        @PaperParcel
        class ExpansionSpec(
                val expansions: List<Expansion>,
                val visibility: FilterUi.ExpansionVisibility
        ) : Spec() {

            override fun apply(filter: Filter): List<Item> {
                val items = ArrayList<Item>()
                items += Item.Header(R.string.filter_header_expansions)
                items += getVisibleExpansions(filter)
                if (visibility != UNLIMITED) {
                    items += Item.ViewMore(when(visibility){
                        STANDARD -> R.string.filter_viewmore_expanded
                        else -> R.string.filter_viewmore_all
                    })
                }
                return items
            }


            private fun getVisibleExpansions(filter: Filter): List<Item> {
                return expansions.filter {
                    when(visibility) {
                        STANDARD -> it.standardLegal
                        EXPANDED -> it.expandedLegal
                        else -> true
                    }
                }.map {
                    ExpansionOption("expansion", it, filter.expansions.contains(it))
                }
            }

            companion object {
                @JvmField val CREATOR = PaperParcelFilterSpec_Spec_ExpansionSpec.CREATOR
            }
        }


        @PaperParcel
        class RaritySpec(val rarities: List<Rarity>) : Spec() {

            override fun apply(filter: Filter): List<Item> = listOf(
                    Item.Header(R.string.filter_header_rarity)
            ).plus(getRarityItems(filter))


            private fun getRarityItems(filter: Filter): List<Item> {
                return rarities.map {
                    RarityOption("rarity", it, filter.rarity.contains(it))
                }
            }

            companion object {
                @JvmField val CREATOR = PaperParcelFilterSpec_Spec_RaritySpec.CREATOR
            }
        }


        @PaperParcel
        class ValueRangeSpec(val key: String,
                             @StringRes val title: Int,
                             val min: Int,
                             val max: Int) : Spec() {

            override fun apply(filter: Filter): List<Item> = listOf(
                    Item.Header(title),
                    Item.ValueRange(key, min, max, getValue(filter))
            )


            private fun getValue(filter: Filter): Value = when(key) {
                "hp" -> parseValue(filter.hp)
                "attackCost" -> parseValue(filter.attackCost)
                "attackDamage" -> parseValue(filter.attackDamage)
                "retreatCost" -> parseValue(filter.retreatCost)
                else -> Value(0, NONE)
            }


            companion object {
                @JvmField val CREATOR = PaperParcelFilterSpec_Spec_ValueRangeSpec.CREATOR

                fun parseValue(value: String?): Value {
                    return value?.let {
                        val modifier = when {
                            value.startsWith("gte", true) -> GREATER_THAN_EQUALS
                            value.startsWith("lte", true) -> LESS_THAN_EQUALS
                            value.startsWith("gt", true) -> GREATER_THAN
                            value.startsWith("lt", true) -> LESS_THAN
                            else -> NONE
                        }
                        val cleanValue = value
                                .replace("gte", "", true)
                                .replace("lte", "", true)
                                .replace("gt", "", true)
                                .replace("lt", "", true)
                                .trim()

                        cleanValue.toIntOrNull()?.let {
                            Value(it, modifier)
                        } ?: Value(0, NONE)
                    } ?: Value(0, NONE)
                }
            }
        }
    }


    companion object {
        @JvmField val CREATOR = PaperParcelFilterSpec.CREATOR

        val DEFAULT by lazy {
            createPokemon(emptyList(), STANDARD)
        }


        fun create(superType: SuperType,
                   expansions: List<Expansion> = emptyList(),
                   visibility: FilterUi.ExpansionVisibility): FilterSpec = when(superType) {
            SuperType.POKEMON -> createPokemon(expansions, visibility)
            SuperType.TRAINER -> createTrainer(expansions, visibility)
            SuperType.ENERGY -> createEnergy(expansions, visibility)
            SuperType.UNKNOWN -> createAll(expansions, visibility)
        }


        fun createAll(expansions: List<Expansion>,
                      visibility: FilterUi.ExpansionVisibility): FilterSpec {
            return FilterSpec(
                    listOf(
                            Spec.FieldSpec(),
                            Spec.TypeSpec("type", R.string.filter_header_type),
                            AttributeSpec(listOf(
                                    SuperTypeAttribute(SuperType.POKEMON),
                                    SuperTypeAttribute(SuperType.TRAINER),
                                    SuperTypeAttribute(SuperType.ENERGY),
                                    SubTypeAttribute(BASIC),
                                    SubTypeAttribute(SPECIAL),
                                    SubTypeAttribute(STAGE_1),
                                    SubTypeAttribute(STAGE_2),
                                    SubTypeAttribute(MEGA),
                                    SubTypeAttribute(EX),
                                    SubTypeAttribute(GX),
                                    SubTypeAttribute(LEVEL_UP),
                                    SubTypeAttribute(BREAK),
                                    SubTypeAttribute(LEGEND),
                                    SubTypeAttribute(RESTORED),
                                    SubTypeAttribute(ITEM),
                                    SubTypeAttribute(STADIUM),
                                    SubTypeAttribute(SUPPORTER),
                                    SubTypeAttribute(STADIUM),
                                    SubTypeAttribute(TECHNICAL_MACHINE),
                                    SubTypeAttribute(POKEMON_TOOL),
                                    SubTypeAttribute(ROCKETS_SECRET_MACHINE),
                                    ContainsAttribute("Ability")
                            )),
                            Spec.ExpansionSpec(expansions, visibility),
                            Spec.RaritySpec(Rarity.values().toList()),
                            Spec.ValueRangeSpec("retreatCost", R.string.filter_header_retreat_cost, 0, 4),
                            Spec.ValueRangeSpec("attackCost", R.string.filter_header_attack_cost, 0, 5),
                            Spec.ValueRangeSpec("attackDamage", R.string.filter_header_attack_damage, 0, 300),
                            Spec.ValueRangeSpec("hp", R.string.filter_header_retreat_cost, 0, 250),
                            Spec.TypeSpec("weaknesses", R.string.filter_header_weaknesses),
                            Spec.TypeSpec("resistances", R.string.filter_header_resistances)
                    )
            )
        }


        fun createPokemon(expansions: List<Expansion>,
                          visibility: FilterUi.ExpansionVisibility): FilterSpec {
            return FilterSpec(
                    listOf(
                            Spec.FieldSpec(),
                            Spec.TypeSpec("type", R.string.filter_header_type),
                            AttributeSpec(listOf(
                                    SubTypeAttribute(BASIC),
                                    SubTypeAttribute(STAGE_1),
                                    SubTypeAttribute(STAGE_2),
                                    SubTypeAttribute(MEGA),
                                    SubTypeAttribute(EX),
                                    SubTypeAttribute(GX),
                                    SubTypeAttribute(LEVEL_UP),
                                    SubTypeAttribute(BREAK),
                                    SubTypeAttribute(LEGEND),
                                    SubTypeAttribute(RESTORED),
                                    ContainsAttribute("Ability")
                            )),
                            Spec.ExpansionSpec(expansions, visibility),
                            Spec.RaritySpec(Rarity.values().toList()),
                            Spec.ValueRangeSpec("retreatCost", R.string.filter_header_retreat_cost, 0, 4),
                            Spec.ValueRangeSpec("attackCost", R.string.filter_header_attack_cost, 0, 5),
                            Spec.ValueRangeSpec("attackDamage", R.string.filter_header_attack_damage, 0, 300),
                            Spec.ValueRangeSpec("hp", R.string.filter_header_hp, 0, 250),
                            Spec.TypeSpec("weaknesses", R.string.filter_header_weaknesses),
                            Spec.TypeSpec("resistances", R.string.filter_header_resistances)
                    )
            )
        }


        fun createTrainer(expansions: List<Expansion>,
                          visibility: FilterUi.ExpansionVisibility): FilterSpec {
            return FilterSpec(
                    listOf(
                            Spec.FieldSpec(),
                            AttributeSpec(listOf(
                                    SubTypeAttribute(ITEM),
                                    SubTypeAttribute(STADIUM),
                                    SubTypeAttribute(SUPPORTER),
                                    SubTypeAttribute(STADIUM),
                                    SubTypeAttribute(TECHNICAL_MACHINE),
                                    SubTypeAttribute(POKEMON_TOOL),
                                    SubTypeAttribute(ROCKETS_SECRET_MACHINE)
                            )),
                            Spec.ExpansionSpec(expansions, visibility),
                            Spec.RaritySpec(Rarity.values().toList())
                    )
            )
        }


        fun createEnergy(expansions: List<Expansion>,
                         visibility: FilterUi.ExpansionVisibility): FilterSpec {
            return FilterSpec(
                    listOf(
                            AttributeSpec(listOf(
                                    SubTypeAttribute(BASIC),
                                    SubTypeAttribute(SPECIAL)
                            )),
                            Spec.ExpansionSpec(expansions, visibility),
                            Spec.RaritySpec(Rarity.values().toList())
                    )
            )
        }
    }

}