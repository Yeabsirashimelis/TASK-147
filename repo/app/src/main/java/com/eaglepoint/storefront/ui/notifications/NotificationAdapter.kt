package com.eaglepoint.storefront.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.Notification
import com.eaglepoint.storefront.domain.model.NotificationStatus
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationAdapter(
    private val onNotificationClick: (Notification) -> Unit
) : ListAdapter<Notification, NotificationAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.notification_title)
        private val contentText: TextView = itemView.findViewById(R.id.notification_content)
        private val timestampText: TextView = itemView.findViewById(R.id.notification_timestamp)
        private val statusChip: Chip = itemView.findViewById(R.id.notification_status)
        private val eventChip: Chip = itemView.findViewById(R.id.notification_event_type)
        private val unreadIndicator: View = itemView.findViewById(R.id.notification_unread_indicator)

        private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())

        fun bind(notification: Notification) {
            titleText.text = notification.title
            contentText.text = notification.content
            timestampText.text = dateFormat.format(Date(notification.createdAt))
            eventChip.text = notification.eventType.name.replace("_", " ")
            unreadIndicator.visibility = if (!notification.isRead) View.VISIBLE else View.GONE

            when (notification.status) {
                NotificationStatus.DELIVERED -> {
                    statusChip.text = "Delivered"
                    statusChip.setChipBackgroundColorResource(R.color.status_success)
                }
                NotificationStatus.PENDING -> {
                    statusChip.text = "Pending"
                    statusChip.setChipBackgroundColorResource(R.color.status_pending)
                }
                NotificationStatus.FAILED -> {
                    statusChip.text = "Retry ${notification.retryCount}/${notification.maxRetries}"
                    statusChip.setChipBackgroundColorResource(R.color.status_warning)
                }
                NotificationStatus.EXHAUSTED -> {
                    statusChip.text = "Failed"
                    statusChip.setChipBackgroundColorResource(R.color.status_failure)
                }
            }

            itemView.setOnClickListener { onNotificationClick(notification) }

            itemView.alpha = if (notification.isRead) 0.7f else 1.0f
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Notification>() {
            override fun areItemsTheSame(oldItem: Notification, newItem: Notification) =
                oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Notification, newItem: Notification) =
                oldItem == newItem
        }
    }
}
