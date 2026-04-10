package com.eaglepoint.storefront.ui.cart

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.CartLineItem
import com.google.android.material.button.MaterialButton

class CartLineItemAdapter(
    private val onQuantityChange: (CartLineItem, Int) -> Unit,
    private val onRemove: (CartLineItem) -> Unit
) : ListAdapter<CartLineItem, CartLineItemAdapter.ViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cart_line, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.cart_item_name)
        private val skuText: TextView = itemView.findViewById(R.id.cart_item_sku)
        private val priceText: TextView = itemView.findViewById(R.id.cart_item_price)
        private val quantityText: TextView = itemView.findViewById(R.id.cart_item_quantity)
        private val lineTotalText: TextView = itemView.findViewById(R.id.cart_item_line_total)
        private val decreaseButton: MaterialButton = itemView.findViewById(R.id.cart_decrease_button)
        private val increaseButton: MaterialButton = itemView.findViewById(R.id.cart_increase_button)
        private val removeButton: ImageButton = itemView.findViewById(R.id.cart_remove_button)

        fun bind(item: CartLineItem) {
            nameText.text = item.name
            skuText.text = item.sku
            priceText.text = "$${String.format("%.2f", item.effectivePrice)}"
            quantityText.text = item.quantity.toString()
            lineTotalText.text = "$${String.format("%.2f", item.lineTotal)}"

            decreaseButton.setOnClickListener {
                onQuantityChange(item, item.quantity - 1)
            }
            increaseButton.setOnClickListener {
                onQuantityChange(item, item.quantity + 1)
            }
            removeButton.setOnClickListener { onRemove(item) }

            decreaseButton.isEnabled = item.quantity > 1
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<CartLineItem>() {
            override fun areItemsTheSame(oldItem: CartLineItem, newItem: CartLineItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: CartLineItem, newItem: CartLineItem) =
                oldItem == newItem
        }
    }
}
