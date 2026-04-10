package com.eaglepoint.storefront.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.Article
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArticleAdapter(
    private val imageLoader: ImageLoader,
    private val onArticleClick: (Article) -> Unit,
    private val onSaveClick: (Article) -> Unit
) : ListAdapter<Article, ArticleAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_article, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.article_thumbnail)
        private val titleText: TextView = itemView.findViewById(R.id.article_title)
        private val summaryText: TextView = itemView.findViewById(R.id.article_summary)
        private val sourceText: TextView = itemView.findViewById(R.id.article_source)
        private val dateText: TextView = itemView.findViewById(R.id.article_date)
        private val teamText: TextView = itemView.findViewById(R.id.article_team)
        private val saveButton: ImageButton = itemView.findViewById(R.id.article_save_button)

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        fun bind(article: Article) {
            titleText.text = article.title
            summaryText.text = article.summary ?: ""
            summaryText.visibility = if (article.summary != null) View.VISIBLE else View.GONE
            sourceText.text = article.author ?: ""
            dateText.text = article.publishedAt?.let { dateFormat.format(Date(it)) } ?: ""

            if (article.team != null || article.league != null) {
                teamText.text = listOfNotNull(article.team, article.league).joinToString(" - ")
                teamText.visibility = View.VISIBLE
            } else {
                teamText.visibility = View.GONE
            }

            saveButton.setImageResource(
                if (article.isSavedOffline) R.drawable.ic_bookmark_filled
                else R.drawable.ic_bookmark_outline
            )
            saveButton.contentDescription = if (article.isSavedOffline) "Unsave" else "Save for offline"

            // Load thumbnail with downsampling
            imageLoader.loadInto(
                article.imageUrl, thumbnail,
                TARGET_THUMBNAIL_WIDTH, TARGET_THUMBNAIL_HEIGHT
            )

            itemView.setOnClickListener { onArticleClick(article) }
            saveButton.setOnClickListener { onSaveClick(article) }
        }

        fun recycle() {
            imageLoader.cancel(thumbnail)
        }
    }

    companion object {
        private const val TARGET_THUMBNAIL_WIDTH = 160
        private const val TARGET_THUMBNAIL_HEIGHT = 120

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Article>() {
            override fun areItemsTheSame(oldItem: Article, newItem: Article): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Article, newItem: Article): Boolean {
                return oldItem == newItem
            }
        }
    }
}
