package com.eaglepoint.storefront.ui.quality

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.BatchValidationStatus
import com.eaglepoint.storefront.domain.model.DataBatchVersion
import com.google.android.material.chip.Chip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatchQualityAdapter(
    private val onBatchClick: (DataBatchVersion) -> Unit
) : ListAdapter<DataBatchVersion, BatchQualityAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_batch_quality, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val batchNameText: TextView = itemView.findViewById(R.id.batch_name)
        private val versionText: TextView = itemView.findViewById(R.id.batch_version)
        private val statusChip: Chip = itemView.findViewById(R.id.batch_status)
        private val itemCountText: TextView = itemView.findViewById(R.id.batch_item_count)
        private val errorCountText: TextView = itemView.findViewById(R.id.batch_error_count)
        private val errorRateText: TextView = itemView.findViewById(R.id.batch_error_rate)
        private val timestampText: TextView = itemView.findViewById(R.id.batch_timestamp)

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        fun bind(batch: DataBatchVersion) {
            batchNameText.text = batch.batchName
            versionText.text = "v${batch.version}"
            itemCountText.text = "${batch.itemsCount} items"
            errorCountText.text = "${batch.errorCount} errors"
            errorRateText.text = "%.1f%%".format(batch.errorRate * 100)
            timestampText.text = dateFormat.format(Date(batch.createdAt))

            when (batch.validationStatus) {
                BatchValidationStatus.PASSED -> {
                    statusChip.text = "PASSED"
                    statusChip.setChipBackgroundColorResource(R.color.status_success)
                }
                BatchValidationStatus.FAILED -> {
                    statusChip.text = "FAILED"
                    statusChip.setChipBackgroundColorResource(R.color.status_failure)
                }
                BatchValidationStatus.PENDING -> {
                    statusChip.text = "PENDING"
                    statusChip.setChipBackgroundColorResource(R.color.status_pending)
                }
                null -> {
                    statusChip.text = "N/A"
                    statusChip.setChipBackgroundColorResource(R.color.status_pending)
                }
            }

            itemView.setOnClickListener { onBatchClick(batch) }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DataBatchVersion>() {
            override fun areItemsTheSame(oldItem: DataBatchVersion, newItem: DataBatchVersion) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: DataBatchVersion, newItem: DataBatchVersion) =
                oldItem == newItem
        }
    }
}
