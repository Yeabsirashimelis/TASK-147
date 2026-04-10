package com.eaglepoint.storefront.ui.ingestion.log

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.IngestionJobRun
import com.eaglepoint.storefront.domain.model.IngestionStatus
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IngestionJobRunAdapter : ListAdapter<IngestionJobRun, IngestionJobRunAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ingestion_job_run, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val statusChip: Chip = itemView.findViewById(R.id.job_status)
        private val startedAtText: TextView = itemView.findViewById(R.id.job_started_at)
        private val durationText: TextView = itemView.findViewById(R.id.job_duration)
        private val parsedText: TextView = itemView.findViewById(R.id.job_items_parsed)
        private val storedText: TextView = itemView.findViewById(R.id.job_items_stored)
        private val ruleVersionText: TextView = itemView.findViewById(R.id.job_rule_version)
        private val failureText: TextView = itemView.findViewById(R.id.job_failure_reason)
        private val attemptText: TextView = itemView.findViewById(R.id.job_attempt)

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        fun bind(run: IngestionJobRun) {
            statusChip.text = run.status.name
            statusChip.setChipBackgroundColorResource(
                when (run.status) {
                    IngestionStatus.SUCCESS -> R.color.status_success
                    IngestionStatus.FAILURE -> R.color.status_failure
                    IngestionStatus.RUNNING -> R.color.status_running
                    IngestionStatus.RETRYING -> R.color.status_running
                    IngestionStatus.PARTIAL_FAILURE -> R.color.status_warning
                    IngestionStatus.PENDING -> R.color.status_pending
                }
            )

            startedAtText.text = dateFormat.format(Date(run.startedAt))

            val duration = if (run.completedAt != null) {
                val durationMs = run.completedAt - run.startedAt
                "${durationMs / 1000}s"
            } else {
                "..."
            }
            durationText.text = duration

            parsedText.text = "Parsed: ${run.itemsParsed}"
            storedText.text = "Stored: ${run.itemsStored}"
            ruleVersionText.text = "Rule v${run.ruleVersion}"
            attemptText.text = "Attempt ${run.attemptNumber}"

            if (run.failureReason != null) {
                failureText.text = run.failureReason
                failureText.visibility = View.VISIBLE
            } else {
                failureText.visibility = View.GONE
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<IngestionJobRun>() {
            override fun areItemsTheSame(oldItem: IngestionJobRun, newItem: IngestionJobRun): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: IngestionJobRun, newItem: IngestionJobRun): Boolean {
                return oldItem == newItem
            }
        }
    }
}
