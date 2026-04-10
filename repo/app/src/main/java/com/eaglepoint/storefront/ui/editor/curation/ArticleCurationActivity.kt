package com.eaglepoint.storefront.ui.editor.curation

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import org.koin.androidx.viewmodel.ext.android.viewModel

class ArticleCurationActivity : AppCompatActivity() {

    private val viewModel: ArticleCurationViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_article_curation)

        val adapter = CurationArticleAdapter(
            onApprove = { viewModel.approve(it.id) },
            onReject = { showRejectDialog(it.id) },
            onFeature = { viewModel.setFeatured(it.id, !it.isFeatured) },
            onEditTags = { showTagsDialog(it) }
        )

        val recyclerView = findViewById<RecyclerView>(R.id.curation_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        viewModel.getPendingArticles().observe(this) { articles ->
            adapter.submitList(articles)
            val emptyView = findViewById<View>(R.id.curation_empty)
            emptyView.visibility = if (articles.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            if (error != null) Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        }
    }

    private fun showRejectDialog(articleId: String) {
        val input = EditText(this).apply { hint = getString(R.string.reject_reason_hint) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.reject_article))
            .setView(input)
            .setPositiveButton(getString(R.string.reject)) { _, _ ->
                viewModel.reject(articleId, input.text.toString().ifBlank { null })
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showTagsDialog(article: com.eaglepoint.storefront.domain.model.Article) {
        val view = layoutInflater.inflate(R.layout.dialog_edit_tags, null)
        val teamInput = view.findViewById<EditText>(R.id.tag_team_input)
        val leagueInput = view.findViewById<EditText>(R.id.tag_league_input)
        val topicInput = view.findViewById<EditText>(R.id.tag_topic_input)

        teamInput.setText(article.team ?: "")
        leagueInput.setText(article.league ?: "")
        topicInput.setText(article.topic ?: "")

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.edit_tags))
            .setView(view)
            .setPositiveButton(getString(R.string.save_tags)) { _, _ ->
                viewModel.updateTags(
                    article.id,
                    teamInput.text.toString().ifBlank { null },
                    leagueInput.text.toString().ifBlank { null },
                    topicInput.text.toString().ifBlank { null }
                )
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}
