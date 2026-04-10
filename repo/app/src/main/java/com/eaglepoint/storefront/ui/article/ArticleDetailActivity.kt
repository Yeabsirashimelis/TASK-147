package com.eaglepoint.storefront.ui.article

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.ui.home.ImageLoader
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArticleDetailActivity : AppCompatActivity() {

    private val viewModel: ArticleDetailViewModel by viewModel()
    private lateinit var imageLoader: ImageLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_detail)

        imageLoader = ImageLoader()
        val articleId = intent.getStringExtra(EXTRA_ARTICLE_ID) ?: run {
            finish()
            return
        }

        setupSaveButton()
        observeState()
        viewModel.loadArticle(articleId)
    }

    private fun setupSaveButton() {
        val saveButton = findViewById<ImageButton>(R.id.detail_save_button)
        saveButton.setOnClickListener { viewModel.toggleSave() }
    }

    private fun observeState() {
        val dateFormat = SimpleDateFormat("MMMM dd, yyyy 'at' HH:mm", Locale.getDefault())

        viewModel.article.observe(this) { article ->
            if (article == null) return@observe

            findViewById<TextView>(R.id.detail_title).text = article.title
            findViewById<TextView>(R.id.detail_author).text = article.author ?: ""
            findViewById<TextView>(R.id.detail_date).text =
                article.publishedAt?.let { dateFormat.format(Date(it)) } ?: ""

            val contentView = findViewById<TextView>(R.id.detail_content)
            contentView.text = article.content ?: article.summary ?: ""

            val teamLeague = listOfNotNull(article.team, article.league).joinToString(" - ")
            val teamView = findViewById<TextView>(R.id.detail_team_league)
            if (teamLeague.isNotBlank()) {
                teamView.text = teamLeague
                teamView.visibility = View.VISIBLE
            } else {
                teamView.visibility = View.GONE
            }

            val heroImage = findViewById<ImageView>(R.id.detail_hero_image)
            if (article.imageUrl != null) {
                imageLoader.loadInto(article.imageUrl, heroImage, HERO_WIDTH, HERO_HEIGHT)
                heroImage.visibility = View.VISIBLE
            } else {
                heroImage.visibility = View.GONE
            }

            val saveButton = findViewById<ImageButton>(R.id.detail_save_button)
            saveButton.setImageResource(
                if (article.isSavedOffline) R.drawable.ic_bookmark_filled
                else R.drawable.ic_bookmark_outline
            )
        }

        viewModel.isLoading.observe(this) { loading ->
            val progressBar = findViewById<View>(R.id.detail_progress)
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        imageLoader.clearCache()
    }

    companion object {
        const val EXTRA_ARTICLE_ID = "extra_article_id"
        private const val HERO_WIDTH = 800
        private const val HERO_HEIGHT = 450
    }
}
