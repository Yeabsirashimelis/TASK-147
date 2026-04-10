package com.eaglepoint.storefront.ui.checkout

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.ui.receipt.ReceiptDetailActivity
import com.google.android.material.button.MaterialButton
import org.koin.androidx.viewmodel.ext.android.viewModel

class CheckoutActivity : AppCompatActivity() {

    private val viewModel: CheckoutViewModel by viewModel()
    private var cartId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        cartId = intent.getStringExtra(EXTRA_CART_ID) ?: run { finish(); return }

        setupConfirmButton()
        observeState()
        viewModel.loadCheckout(cartId)
    }

    private fun setupConfirmButton() {
        val confirmButton = findViewById<MaterialButton>(R.id.confirm_checkout_button)
        confirmButton.setOnClickListener {
            viewModel.completeCheckout(cartId, STATE_CODE)
        }
    }

    private fun observeState() {
        viewModel.pricing.observe(this) { pricing ->
            findViewById<TextView>(R.id.checkout_subtotal).text = "$${String.format("%.2f", pricing.subtotal)}"
            findViewById<TextView>(R.id.checkout_discount).text = "-$${String.format("%.2f", pricing.discountAmount + pricing.couponDiscount)}"
            findViewById<TextView>(R.id.checkout_tax).text = "$${String.format("%.2f", pricing.taxAmount)} (${String.format("%.1f", pricing.taxRate * 100)}%)"
            findViewById<TextView>(R.id.checkout_total).text = "$${String.format("%.2f", pricing.total)}"

            val appliedRulesText = if (pricing.appliedRules.isNotEmpty()) {
                pricing.appliedRules.joinToString(", ")
            } else ""
            findViewById<TextView>(R.id.checkout_applied_rules).text = appliedRulesText
        }

        viewModel.cart.observe(this) { cart ->
            if (cart != null) {
                findViewById<TextView>(R.id.checkout_item_count).text =
                    getString(R.string.checkout_items, cart.items.sumOf { it.quantity })
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            val progressBar = findViewById<View>(R.id.checkout_progress)
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.priceLockExpired.observe(this) { expired ->
            if (expired) showPriceReconfirmDialog()
        }

        viewModel.checkoutResult.observe(this) { result ->
            if (result == null) return@observe
            if (result.success) {
                Toast.makeText(this, getString(R.string.checkout_success, result.orderId), Toast.LENGTH_LONG).show()
                // Navigate to receipt
                val receiptIntent = Intent(this, ReceiptDetailActivity::class.java)
                receiptIntent.putExtra(ReceiptDetailActivity.EXTRA_ORDER_ID, result.orderId)
                startActivity(receiptIntent)
                finish()
            } else if (!result.requiresPriceReconfirm) {
                Toast.makeText(this, result.error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showPriceReconfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.price_changed_title))
            .setMessage(getString(R.string.price_changed_message))
            .setPositiveButton(getString(R.string.confirm_new_prices)) { _, _ ->
                viewModel.completeCheckout(cartId, STATE_CODE, confirmedPriceChange = true)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    companion object {
        const val EXTRA_CART_ID = "extra_cart_id"
        private const val STATE_CODE = "CA"
    }
}
