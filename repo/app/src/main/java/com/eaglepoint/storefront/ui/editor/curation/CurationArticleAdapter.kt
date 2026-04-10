package com.eaglepoint.storefront.ui.editor.curation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.Article
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CurationArticleAdapter(
    private val onApprove: (Article) -> Unit,
    private val onReject: (Article) -> Unit,
    private val onFeature: (Article) -> Unit,
    private val onEditTags: (Article) -> Unit
) : ListAdapter<Article, CurationArticleAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_curation_article, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.curation_title)
        private val summaryText: TextView = itemView.findViewById(R.id.curation_summary)
        private val sourceText: TextView = itemView.findViewById(R.id.curation_source)
        private val dateText: TextView = itemView.findViewById(R.id.curation_date)
        private val statusChip: Chip = itemView.findViewById(R.id.curation_status)
        private val teamChip: Chip = itemView.findViewById(R.id.curation_team)
        private val approveButton: MaterialButton = itemView.findViewById(R.id.curation_approve)
        private val rejectButton: MaterialButton = itemView.findViewById(R.id.curation_reject)
        private val featureButton: MaterialButton = itemView.findViewById(R.id.curation_feature)
        private val tagsButton: MaterialButton = itemView.findViewById(R.id.curation_edit_tags)

        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())

        fun bind(article: Article) {
            titleText.text = article.title
            summaryText.text = article.summary ?: ""
            sourceText.text = article.author ?: ""
            dateText.text = article.publishedAt?.let { dateFormat.format(Date(it)) } ?: ""
            statusChip.text = article.curationStatus.name

            val teamLeague = listOfNotNull(article.team, article.league, article.topic)
                .joinToString(" / ")
            if (teamLeague.isNotBlank()) {
                teamChip.text = teamLeague
                teamChip.visibility = View.VISIBLE
            } else {
                teamChip.visibility = View.GONE
            }

            approveButton.setOnClickListener { onApprove(article) }
            rejectButton.setOnClickListener { onReject(article) }
            featureButton.setOnClickListener { onFeature(article) }
            featureButton.text = if (article.isFeatured) "Unfeature" else "Feature"
            tagsButton.setOnClickListener { onEditTags(article) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Article>() {
            override fun areItemsTheSame(oldItem: Article, newItem: Article) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Article, newItem: Article) = oldItem == newItem
        }
    }
}
