package com.eaglepoint.storefront.ui.home

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.ui.article.ArticleDetailActivity
import com.eaglepoint.storefront.ui.saved.SavedArticlesActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import org.koin.androidx.viewmodel.ext.android.viewModel

class HomeActivity : AppCompatActivity() {

    private val viewModel: HomeViewModel by viewModel()
    private lateinit var articleAdapter: ArticleAdapter
    private lateinit var imageLoader: ImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        imageLoader = ImageLoader()
        setupArticleList()
        setupSearch()
        setupFilters()
        setupSavedButton()
        observeState()
    }

    private fun setupArticleList() {
        articleAdapter = ArticleAdapter(
            imageLoader = imageLoader,
            onArticleClick = { article ->
                val intent = Intent(this, ArticleDetailActivity::class.java)
                intent.putExtra(ArticleDetailActivity.EXTRA_ARTICLE_ID, article.id)
                startActivity(intent)
            },
            onSaveClick = { article ->
                viewModel.toggleSaveArticle(article)
            }
        )

        val recyclerView = findViewById<RecyclerView>(R.id.articles_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = articleAdapter
        recyclerView.setHasFixedSize(true)
        recyclerView.setItemViewCacheSize(20)
    }

    private fun setupSearch() {
        val searchInput = findViewById<TextInputEditText>(R.id.search_input)
        searchInput.addTextChangedListener(object : TextWatcher {
            private var searchRunnable: Runnable? = null

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                searchRunnable?.let { searchInput.removeCallbacks(it) }
                searchRunnable = Runnable {
                    viewModel.setKeyword(s?.toString())
                }
                searchInput.postDelayed(searchRunnable, SEARCH_DEBOUNCE_MS)
            }
        })
    }

    private fun setupFilters() {
        val clearFiltersButton = findViewById<MaterialButton>(R.id.clear_filters_button)
        clearFiltersButton.setOnClickListener {
            viewModel.clearFilters()
            findViewById<TextInputEditText>(R.id.search_input).text?.clear()
        }
    }

    private fun setupSavedButton() {
        val savedButton = findViewById<MaterialButton>(R.id.saved_articles_button)
        savedButton.setOnClickListener {
            startActivity(Intent(this, SavedArticlesActivity::class.java))
        }
    }

    private fun observeState() {
        viewModel.articles.observe(this) { articles ->
            articleAdapter.submitList(articles)
            val emptyView = findViewById<View>(R.id.empty_state)
            emptyView.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.teams.observe(this) { teams ->
            val chipGroup = findViewById<ChipGroup>(R.id.team_chips)
            chipGroup.removeAllViews()
            teams.forEach { team ->
                val chip = Chip(this).apply {
                    text = team
                    isCheckable = true
                    setOnCheckedChangeListener { _, isChecked ->
                        viewModel.setTeamFilter(if (isChecked) team else null)
                    }
                }
                chipGroup.addView(chip)
            }
        }

        viewModel.leagues.observe(this) { leagues ->
            val chipGroup = findViewById<ChipGroup>(R.id.league_chips)
            chipGroup.removeAllViews()
            leagues.forEach { league ->
                val chip = Chip(this).apply {
                    text = league
                    isCheckable = true
                    setOnCheckedChangeListener { _, isChecked ->
                        viewModel.setLeagueFilter(if (isChecked) league else null)
                    }
                }
                chipGroup.addView(chip)
            }
        }

        viewModel.savedCount.observe(this) { count ->
            val savedButton = findViewById<MaterialButton>(R.id.saved_articles_button)
            savedButton.text = if (count > 0) {
                getString(R.string.saved_with_count, count)
            } else {
                getString(R.string.saved_articles)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageLoader.clearCache()
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
