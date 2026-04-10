package com.eaglepoint.storefront.ui.receipt

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.Order
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReceiptListActivity : AppCompatActivity() {

    private val viewModel: ReceiptListViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_receipt_list)

        val recyclerView = findViewById<RecyclerView>(R.id.receipts_recycler)
        val emptyView = findViewById<View>(R.id.empty_state)

        val adapter = OrderAdapter { order ->
            val intent = Intent(this, ReceiptDetailActivity::class.java)
            intent.putExtra(ReceiptDetailActivity.EXTRA_ORDER_ID, order.id)
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        viewModel.orders.observe(this) { orders ->
            adapter.submitList(orders)
            emptyView.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.loadOrders()
    }
}

class OrderAdapter(
    private val onOrderClick: (Order) -> Unit
) : RecyclerView.Adapter<OrderAdapter.ViewHolder>() {

    private var items = listOf<Order>()

    fun submitList(newItems: List<Order>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val orderId: TextView = view.findViewById(R.id.order_id_text)
        private val orderDate: TextView = view.findViewById(R.id.order_date_text)
        private val orderTotal: TextView = view.findViewById(R.id.order_total_text)

        fun bind(order: Order) {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            orderId.text = "Order #${order.id.take(8)}"
            orderDate.text = dateFormat.format(Date(order.createdAt))
            orderTotal.text = "$${String.format("%.2f", order.total)}"
            itemView.setOnClickListener { onOrderClick(order) }
        }
    }
}
