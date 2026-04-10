package com.eaglepoint.storefront.ui.cart

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.security.SessionManager
import com.eaglepoint.storefront.ui.checkout.CheckoutActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.koin.androidx.viewmodel.ext.android.viewModel

class CartActivity : AppCompatActivity() {

    private val viewModel: CartViewModel by viewModel()
    private val sessionManager: SessionManager by org.koin.android.ext.android.inject()
    private lateinit var adapter: CartLineItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cart)

        setupRecyclerView()
        setupCouponInput()
        setupCheckoutButton()
        observeState()

        val userId = sessionManager.currentUserId
        val cartId = intent.getStringExtra(EXTRA_CART_ID)
        if (cartId != null) {
            viewModel.loadCart(cartId)
        } else {
            viewModel.loadOrCreateCart(userId)
        }
    }

    private fun setupRecyclerView() {
        adapter = CartLineItemAdapter(
            onQuantityChange = { item, newQty ->
                viewModel.updateQuantity(item.id, item.catalogItemId, newQty)
            },
            onRemove = { item -> viewModel.removeItem(item.id) }
        )
        val recyclerView = findViewById<RecyclerView>(R.id.cart_items_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupCouponInput() {
        val couponInput = findViewById<TextInputEditText>(R.id.coupon_input)
        val applyButton = findViewById<MaterialButton>(R.id.apply_coupon_button)
        val removeButton = findViewById<MaterialButton>(R.id.remove_coupon_button)

        applyButton.setOnClickListener {
            val code = couponInput.text?.toString()?.trim() ?: ""
            if (code.isNotBlank()) viewModel.applyCoupon(code)
        }
        removeButton.setOnClickListener { viewModel.removeCoupon() }
    }

    private fun setupCheckoutButton() {
        val checkoutButton = findViewById<MaterialButton>(R.id.checkout_button)
        checkoutButton.setOnClickListener {
            val cart = viewModel.cart.value ?: return@setOnClickListener
            val intent = Intent(this, CheckoutActivity::class.java)
            intent.putExtra(CheckoutActivity.EXTRA_CART_ID, cart.id)
            startActivity(intent)
        }
    }

    private fun observeState() {
        viewModel.cart.observe(this) { cart ->
            if (cart != null) {
                adapter.submitList(cart.items)
                val emptyView = findViewById<View>(R.id.cart_empty_state)
                emptyView.visibility = if (cart.items.isEmpty()) View.VISIBLE else View.GONE
                val removeButton = findViewById<MaterialButton>(R.id.remove_coupon_button)
                removeButton.visibility = if (cart.couponId != null) View.VISIBLE else View.GONE
            }
        }

        viewModel.pricing.observe(this) { pricing ->
            findViewById<TextView>(R.id.cart_subtotal).text = "$${String.format("%.2f", pricing.subtotal)}"
            findViewById<TextView>(R.id.cart_discount).text = "-$${String.format("%.2f", pricing.discountAmount + pricing.couponDiscount)}"
            findViewById<TextView>(R.id.cart_tax).text = "$${String.format("%.2f", pricing.taxAmount)}"
            findViewById<TextView>(R.id.cart_total).text = "$${String.format("%.2f", pricing.total)}"

            val discountRow = findViewById<View>(R.id.discount_row)
            discountRow.visibility = if (pricing.discountAmount + pricing.couponDiscount > 0) View.VISIBLE else View.GONE
        }

        viewModel.error.observe(this) { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        const val EXTRA_CART_ID = "extra_cart_id"
    }
}
