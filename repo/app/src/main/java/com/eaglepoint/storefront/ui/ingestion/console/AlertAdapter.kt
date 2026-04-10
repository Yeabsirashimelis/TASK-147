package com.eaglepoint.storefront.ui.ingestion.console

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.IngestionAlert
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AlertAdapter(
    private val onAcknowledgeClick: (IngestionAlert) -> Unit
) : ListAdapter<IngestionAlert, AlertAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_ingestion_alert, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageText: TextView = itemView.findViewById(R.id.alert_message)
        private val timestampText: TextView = itemView.findViewById(R.id.alert_timestamp)
        private val failureCountText: TextView = itemView.findViewById(R.id.alert_failure_count)
        private val acknowledgeButton: MaterialButton = itemView.findViewById(R.id.alert_acknowledge_button)

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun bind(alert: IngestionAlert) {
            messageText.text = alert.message
            timestampText.text = dateFormat.format(Date(alert.createdAt))
            failureCountText.text = "${alert.failureCount} failures"
            acknowledgeButton.setOnClickListener { onAcknowledgeClick(alert) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<IngestionAlert>() {
            override fun areItemsTheSame(oldItem: IngestionAlert, newItem: IngestionAlert): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: IngestionAlert, newItem: IngestionAlert): Boolean {
                return oldItem == newItem
            }
        }
    }
}
