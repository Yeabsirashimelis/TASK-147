package com.eaglepoint.storefront.ui.quality

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.ValidationError
import com.google.android.material.chip.Chip

class ValidationErrorAdapter : ListAdapter<ValidationError, ValidationErrorAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_validation_error, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ruleChip: Chip = itemView.findViewById(R.id.error_rule)
        private val messageText: TextView = itemView.findViewById(R.id.error_message)
        private val entityText: TextView = itemView.findViewById(R.id.error_entity)
        private val fieldText: TextView = itemView.findViewById(R.id.error_field)
        private val actualValueText: TextView = itemView.findViewById(R.id.error_actual_value)

        fun bind(error: ValidationError) {
            ruleChip.text = error.ruleName
            messageText.text = error.message
            entityText.text = "${error.entityType}:${error.entityId.take(8)}..."
            fieldText.text = error.fieldName

            if (error.actualValue != null) {
                actualValueText.text = "Value: ${error.actualValue}"
                actualValueText.visibility = View.VISIBLE
            } else {
                actualValueText.visibility = View.GONE
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ValidationError>() {
            override fun areItemsTheSame(oldItem: ValidationError, newItem: ValidationError) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ValidationError, newItem: ValidationError) =
                oldItem == newItem
        }
    }
}
