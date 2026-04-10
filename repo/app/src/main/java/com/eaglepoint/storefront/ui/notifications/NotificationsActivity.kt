package com.eaglepoint.storefront.ui.notifications

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.google.android.material.button.MaterialButton
import org.koin.androidx.viewmodel.ext.android.viewModel

class NotificationsActivity : AppCompatActivity() {

    private val viewModel: NotificationsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        val recipientId = intent.getStringExtra(EXTRA_RECIPIENT_ID) ?: run { finish(); return }

        val adapter = NotificationAdapter { notification ->
            viewModel.markRead(notification.id)
            Toast.makeText(this, notification.content, Toast.LENGTH_LONG).show()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.notifications_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val markAllButton = findViewById<MaterialButton>(R.id.mark_all_read_button)
        markAllButton.setOnClickListener { viewModel.markAllRead() }

        viewModel.setRecipient(recipientId)

        viewModel.notifications.observe(this) { notifications ->
            adapter.submitList(notifications)
            val emptyView = findViewById<View>(R.id.notifications_empty)
            emptyView.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.unreadCount.observe(this) { count ->
            markAllButton.visibility = if (count > 0) View.VISIBLE else View.GONE
        }
    }

    companion object {
        const val EXTRA_RECIPIENT_ID = "extra_recipient_id"
    }
}
