package com.eaglepoint.storefront.ui.saved

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.ui.article.ArticleDetailActivity
import com.eaglepoint.storefront.ui.home.ArticleAdapter
import com.eaglepoint.storefront.ui.home.ImageLoader
import org.koin.androidx.viewmodel.ext.android.viewModel

class SavedArticlesActivity : AppCompatActivity() {

    private val viewModel: SavedArticlesViewModel by viewModel()
    private lateinit var imageLoader: ImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_articles)

        imageLoader = ImageLoader()

        val adapter = ArticleAdapter(
            imageLoader = imageLoader,
            onArticleClick = { article ->
                val intent = Intent(this, ArticleDetailActivity::class.java)
                intent.putExtra(ArticleDetailActivity.EXTRA_ARTICLE_ID, article.id)
                startActivity(intent)
            },
            onSaveClick = { article ->
                viewModel.unsaveArticle(article.id)
            }
        )

        val recyclerView = findViewById<RecyclerView>(R.id.saved_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)

        viewModel.savedArticles.observe(this) { articles ->
            adapter.submitList(articles)
            val emptyView = findViewById<View>(R.id.saved_empty_state)
            emptyView.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageLoader.clearCache()
    }
}
