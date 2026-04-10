package com.eaglepoint.storefront.ui.receipt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.OrderLineItem
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiptDetailActivity : AppCompatActivity() {

    private val viewModel: ReceiptListViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_detail)

        val orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: run {
            finish()
            return
        }

        val orderIdText = findViewById<TextView>(R.id.receipt_order_id)
        val dateText = findViewById<TextView>(R.id.receipt_date)
        val subtotalText = findViewById<TextView>(R.id.receipt_subtotal)
        val discountText = findViewById<TextView>(R.id.receipt_discount)
        val taxText = findViewById<TextView>(R.id.receipt_tax)
        val totalText = findViewById<TextView>(R.id.receipt_total)
        val itemsRecycler = findViewById<RecyclerView>(R.id.receipt_items_recycler)

        val adapter = ReceiptLineItemAdapter()
        itemsRecycler.layoutManager = LinearLayoutManager(this)
        itemsRecycler.adapter = adapter

        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())

        viewModel.orderDetail.observe(this) { order ->
            if (order == null) {
                finish()
                return@observe
            }
            orderIdText.text = "Order #${order.id.take(8)}"
            dateText.text = dateFormat.format(Date(order.createdAt))
            subtotalText.text = "$${String.format("%.2f", order.subtotal)}"
            val totalDiscount = order.discountAmount + order.couponDiscount
            discountText.text = "-$${String.format("%.2f", totalDiscount)}"
            discountText.visibility = if (totalDiscount > 0) View.VISIBLE else View.GONE
            taxText.text = "$${String.format("%.2f", order.taxAmount)}"
            totalText.text = "$${String.format("%.2f", order.total)}"
            adapter.submitList(order.lineItems)
        }

        viewModel.loadOrderDetail(orderId)
    }

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
    }
}

class ReceiptLineItemAdapter : RecyclerView.Adapter<ReceiptLineItemAdapter.ViewHolder>() {

    private var items = listOf<OrderLineItem>()

    fun submitList(newItems: List<OrderLineItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_receipt_line, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val name: TextView = view.findViewById(R.id.line_item_name)
        private val detail: TextView = view.findViewById(R.id.line_item_detail)
        private val total: TextView = view.findViewById(R.id.line_item_total)

        fun bind(item: OrderLineItem) {
            name.text = item.name
            detail.text = "${item.quantity} × $${String.format("%.2f", item.unitPrice)}"
            total.text = "$${String.format("%.2f", item.lineTotal)}"
        }
    }
}
