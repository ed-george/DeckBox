package com.r0adkll.deckbuilder.arch.ui.features.deckbuilder.adapter


import android.content.Context
import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import com.jakewharton.rxrelay2.Relay
import com.r0adkll.deckbuilder.R
import com.r0adkll.deckbuilder.arch.ui.components.ListRecyclerAdapter
import com.r0adkll.deckbuilder.arch.ui.features.deckbuilder.EditCardIntentions
import com.r0adkll.deckbuilder.arch.ui.features.search.adapter.PokemonCardViewHolder
import com.r0adkll.deckbuilder.arch.ui.widgets.PokemonCardView


class PokemonBuilderRecyclerAdapter(
        context: Context,
        private val editCardIntentions: EditCardIntentions,
        private val pokemonCardClicks: Relay<PokemonCardView>
) : ListRecyclerAdapter<PokemonItem, RecyclerView.ViewHolder>(context) {

    var isEditing: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            R.layout.item_evolution_chain -> {
                EvolutionChainViewHolder.create(inflater, parent, editCardIntentions, pokemonCardClicks)
            }
            else -> {
                PokemonCardViewHolder.create(inflater, parent, false,
                        addCardClicks = editCardIntentions.addCardClicks,
                        removeCardClicks = editCardIntentions.removeCardClicks)
            }
        }
    }


    override fun onBindViewHolder(vh: RecyclerView.ViewHolder, i: Int) {
        val item = items[i]
        when(vh) {
            is EvolutionChainViewHolder -> {
                val evolutionChain = item as PokemonItem.Evolution
                vh.bind(evolutionChain.evolutionChain, isEditing)
            }
            is PokemonCardViewHolder -> {
                val single = (item as PokemonItem.Single).card
                vh.bind(single.card, single.count, isEditMode = isEditing)
                vh.itemView.setOnClickListener {
                    val card = it.findViewById<PokemonCardView>(R.id.card)
                    pokemonCardClicks.accept(card)
                }
                vh.itemView.setOnLongClickListener { v ->
                    val c = v.findViewById<PokemonCardView>(R.id.card)
                    c.startDrag(true)
                    true
                }
            }
        }
    }


    override fun getItemId(position: Int): Long {
        if (position != RecyclerView.NO_POSITION) {
            val item = items[position]
            return when(item) {
                is PokemonItem.Evolution -> item.evolutionChain.hashCode().toLong()
                is PokemonItem.Single -> item.card.card.hashCode().toLong()
            }
        }
        return super.getItemId(position)
    }


    override fun getItemViewType(position: Int): Int {
        return items[position].viewType
    }


    fun setPokemon(pokemon: List<PokemonItem>) {
        val diff = calculateDiff(pokemon, items)
        items = ArrayList(pokemon)
        diff.diff.dispatchUpdatesTo(getListUpdateCallback())
    }
}