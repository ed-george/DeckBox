package com.r0adkll.deckbuilder.arch.ui.features.carddetail


import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.app.ActivityOptionsCompat
import android.support.v7.widget.LinearLayoutManager
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.view.Menu
import android.view.MenuItem
import android.view.View
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.evernote.android.state.State
import com.ftinc.kit.kotlin.extensions.color
import com.ftinc.kit.kotlin.extensions.gone
import com.ftinc.kit.kotlin.extensions.setVisible
import com.ftinc.kit.kotlin.extensions.visible
import com.jakewharton.rxrelay2.PublishRelay
import com.jakewharton.rxrelay2.Relay
import com.r0adkll.deckbuilder.GlideApp
import com.r0adkll.deckbuilder.R
import com.r0adkll.deckbuilder.arch.domain.features.cards.model.PokemonCard
import com.r0adkll.deckbuilder.arch.ui.components.BaseActivity
import com.r0adkll.deckbuilder.arch.ui.features.carddetail.adapter.PokemonCardsRecyclerAdapter
import com.r0adkll.deckbuilder.arch.ui.features.carddetail.di.CardDetailModule
import com.r0adkll.deckbuilder.arch.ui.widgets.PokemonCardView
import com.r0adkll.deckbuilder.internal.analytics.Analytics
import com.r0adkll.deckbuilder.internal.analytics.Event
import com.r0adkll.deckbuilder.internal.di.AppComponent
import com.r0adkll.deckbuilder.util.bindLong
import com.r0adkll.deckbuilder.util.bindOptionalParcelable
import com.sothree.slidinguppanel.SlidingUpPanelLayout
import io.reactivex.Observable
import kotlinx.android.synthetic.main.activity_card_detail.*
import javax.inject.Inject


class CardDetailActivity : BaseActivity(), CardDetailUi, CardDetailUi.Intentions, CardDetailUi.Actions {

    private val card: PokemonCard? by bindOptionalParcelable(EXTRA_CARD)
    private val sessionId: Long by bindLong(EXTRA_SESSION_ID)

    private val addCardClicks: Relay<Unit> = PublishRelay.create()
    private val removeCardClicks: Relay<Unit> = PublishRelay.create()

    @State override var state: CardDetailUi.State = CardDetailUi.State.DEFAULT

    @Inject lateinit var renderer: CardDetailRenderer
    @Inject lateinit var presenter: CardDetailPresenter

    private lateinit var variantsAdapter: PokemonCardsRecyclerAdapter
    private lateinit var evolvesAdapter: PokemonCardsRecyclerAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_detail)

        // Odd state hack to pass in passed values
        state = state.copy(card = card)

        // Only set the deck if it hasn't been set yet
        if (sessionId != -1L) {
            state = state.copy(sessionId = sessionId)
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        appbar?.setNavigationOnClickListener { supportFinishAfterTransition() }

        bindCard()

        variantsAdapter = PokemonCardsRecyclerAdapter(this)
        variantsAdapter.setOnViewItemClickListener { view, _ ->
            Analytics.event(Event.SelectContent.PokemonCard((view as PokemonCardView).card?.id ?: "unknown"))
            CardDetailActivity.show(this, view, sessionId)
        }
        variantsRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        variantsRecycler.adapter = variantsAdapter

        evolvesAdapter = PokemonCardsRecyclerAdapter(this)
        evolvesAdapter.setOnViewItemClickListener { view, _ ->
            Analytics.event(Event.SelectContent.PokemonCard((view as PokemonCardView).card?.id ?: "unknown"))
            CardDetailActivity.show(this, view, sessionId)
        }
        evolvesRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        evolvesRecycler.adapter = evolvesAdapter

        actionClose?.setOnClickListener { finish() }
        slidingLayout?.addPanelSlideListener(object : SlidingUpPanelLayout.PanelSlideListener {
            override fun onPanelSlide(panel: View?, slideOffset: Float) {
                val rotation = 180f * slideOffset
                panelArrow?.rotation = rotation
            }

            override fun onPanelStateChanged(panel: View?, previousState: SlidingUpPanelLayout.PanelState?, newState: SlidingUpPanelLayout.PanelState?) {
            }
        })

        renderer.start()
        presenter.start()
    }


    override fun onDestroy() {
        presenter.stop()
        renderer.stop()
        super.onDestroy()
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return if (slidingLayout != null) {
            menuInflater.inflate(R.menu.activity_card_detail, menu)
            true
        } else {
            false
        }
    }


    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        if (slidingLayout != null) {
            val actionAdd = menu.findItem(R.id.action_add)
            val actionRemove = menu.findItem(R.id.action_remove)

            if (state.sessionId != null) {
                actionAdd.isVisible = true
                actionRemove.isVisible = state.hasCopies
            } else {
                actionAdd.isVisible = false
                actionRemove.isVisible = false
            }

            return true
        }
        return super.onPrepareOptionsMenu(menu)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.action_add -> {
                Analytics.event(Event.SelectContent.Action("detail_add_card", card?.name))
                addCardClicks.accept(Unit)
                true
            }
            R.id.action_remove -> {
                Analytics.event(Event.SelectContent.Action("detail_remove_card", card?.name))
                removeCardClicks.accept(Unit)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }


    override fun setupComponent(component: AppComponent) {
        component.plus(CardDetailModule(this))
                .inject(this)
    }


    override fun render(state: CardDetailUi.State) {
        this.state = state
        renderer.render(state)
    }


    override fun addCardClicks(): Observable<Unit> {
        return addCardClicks
    }


    override fun removeCardClicks(): Observable<Unit> {
        return removeCardClicks
    }


    override fun showCopies(count: Int?) {
        supportActionBar?.title = count?.let {
            resources.getQuantityString(R.plurals.card_detail_copies, count, count)
        } ?: " "
        invalidateOptionsMenu()
    }


    override fun showStandardValidation(isValid: Boolean) {
        formatStandard.setVisible(isValid)
    }


    override fun showExpandedValidation(isValid: Boolean) {
        formatExpanded.setVisible(isValid)
    }


    override fun showVariants(cards: List<PokemonCard>) {
        variantsAdapter.setCards(cards)
        variantsHeader.setVisible(cards.isNotEmpty())
        variantsRecycler.setVisible(cards.isNotEmpty())
    }


    override fun showEvolvesFrom(cards: List<PokemonCard>) {
        evolvesAdapter.setCards(cards)
        evolvesHeader.setVisible(cards.isNotEmpty())
        evolvesRecycler.setVisible(cards.isNotEmpty())
    }


    private fun bindCard() {
        card?.let { card ->
            val number = "#${card.number}"
            val name = " ${card.name}"
            val spannable = SpannableString("$number$name")
            val color = if (slidingLayout == null) color(R.color.black56) else color(R.color.white70)
            spannable.setSpan(ForegroundColorSpan(color), 0, number.length, 0)

            val prismIndex = name.indexOf("◇")
            if (prismIndex != -1) {
                val startIndex = number.length + prismIndex
                spannable.setSpan(ImageSpan(this@CardDetailActivity, R.drawable.ic_prism_star), startIndex, startIndex + 1, 0)
            }

            cardTitle.text = spannable
            cardSubtitle.text = card.expansion?.name ?: "Unknown Expansion"

            emptyView.visible()
            emptyView.setLoading(true)
            var request = GlideApp.with(this)
                    .load(card.imageUrlHiRes)
                    .transition(withCrossFade())
                    .listener(object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                            emptyView.setEmptyMessage(R.string.image_loading_error)
                            return false
                        }

                        override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                            emptyView.gone()
                            return false
                        }
                    })

            if (slidingLayout == null) {
                request = request.placeholder(R.drawable.pokemon_card_back)
            }

            request.into(image ?: tabletImage)

            GlideApp.with(this)
                    .load(card.expansion?.symbolUrl)
                    .transition(withCrossFade())
                    .into(expansionSymbol)
        }
    }


    companion object {
        const val EXTRA_CARD = "CardDetailActivity.Card"
        const val EXTRA_SESSION_ID = "CardDetailActivity.SessionId"


        fun createIntent(context: Context,
                         card: PokemonCard,
                         sessionId: Long? = null): Intent {
            val intent = Intent(context, CardDetailActivity::class.java)
            intent.putExtra(EXTRA_CARD, card)
            sessionId?.let { intent.putExtra(EXTRA_SESSION_ID, it) }
            return intent
        }


        fun show(context: Activity,
                 view: PokemonCardView,
                 sessionId: Long? = null) {
            if (view.card != null) {
                val options = ActivityOptionsCompat.makeSceneTransitionAnimation(context, view, "cardImage")
                val intent = createIntent(context, view.card!!, sessionId)
                context.startActivity(intent, options.toBundle())
            }
        }
    }
}