package com.eaglepoint.storefront.ui.audit

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.AuditEvent
import com.eaglepoint.storefront.security.SensitiveFieldMasker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AuditLogAdapter : ListAdapter<AuditEvent, AuditLogAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_audit_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val timestampText: TextView = itemView.findViewById(R.id.audit_timestamp)
        private val actionText: TextView = itemView.findViewById(R.id.audit_action)
        private val targetText: TextView = itemView.findViewById(R.id.audit_target)
        private val detailText: TextView = itemView.findViewById(R.id.audit_detail)
        private val userIdText: TextView = itemView.findViewById(R.id.audit_user_id)

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        fun bind(event: AuditEvent) {
            timestampText.text = dateFormat.format(Date(event.timestamp))
            actionText.text = event.action.name
            targetText.text = event.target ?: ""
            detailText.text = event.detail ?: ""
            userIdText.text = SensitiveFieldMasker.maskId(event.userId)
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AuditEvent>() {
            override fun areItemsTheSame(oldItem: AuditEvent, newItem: AuditEvent): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: AuditEvent, newItem: AuditEvent): Boolean {
                return oldItem == newItem
            }
        }
    }
}
