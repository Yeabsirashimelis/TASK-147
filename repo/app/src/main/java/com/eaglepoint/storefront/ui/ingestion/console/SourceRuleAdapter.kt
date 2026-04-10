package com.eaglepoint.storefront.ui.ingestion.console

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.SourceRule
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip

class SourceRuleAdapter(
    private val onEditClick: (SourceRule) -> Unit,
    private val onRunClick: (SourceRule) -> Unit,
    private val onLogsClick: (SourceRule) -> Unit
) : ListAdapter<SourceRule, SourceRuleAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_source_rule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.source_name)
        private val urlText: TextView = itemView.findViewById(R.id.source_url)
        private val feedTypeChip: Chip = itemView.findViewById(R.id.source_feed_type)
        private val statusChip: Chip = itemView.findViewById(R.id.source_status)
        private val versionText: TextView = itemView.findViewById(R.id.source_version)
        private val intervalText: TextView = itemView.findViewById(R.id.source_interval)
        private val editButton: MaterialButton = itemView.findViewById(R.id.source_edit_button)
        private val runButton: MaterialButton = itemView.findViewById(R.id.source_run_button)
        private val logsButton: MaterialButton = itemView.findViewById(R.id.source_logs_button)

        fun bind(rule: SourceRule) {
            nameText.text = rule.name
            urlText.text = rule.url
            feedTypeChip.text = rule.feedType.name
            statusChip.text = if (rule.isActive) "Active" else "Inactive"
            versionText.text = "v${rule.ruleVersion}"
            intervalText.text = "Every ${rule.intervalHours}h"

            editButton.setOnClickListener { onEditClick(rule) }
            runButton.setOnClickListener { onRunClick(rule) }
            runButton.isEnabled = rule.isActive
            logsButton.setOnClickListener { onLogsClick(rule) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SourceRule>() {
            override fun areItemsTheSame(oldItem: SourceRule, newItem: SourceRule): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: SourceRule, newItem: SourceRule): Boolean {
                return oldItem == newItem
            }
        }
    }
}
