package com.eaglepoint.storefront.ui.audit

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.ui.backup.BackupRestoreActivity
import com.eaglepoint.storefront.ui.reauth.ReAuthDialogFragment
import com.google.android.material.button.MaterialButton
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.io.File

class AuditLogActivity : AppCompatActivity() {

    private val viewModel: AuditLogViewModel by viewModel()
    private val adapter = AuditLogAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_audit_log)

        val recyclerView = findViewById<RecyclerView>(R.id.audit_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val backupButton = findViewById<MaterialButton>(R.id.backup_restore_button)
        backupButton.setOnClickListener {
            startActivity(Intent(this, BackupRestoreActivity::class.java))
        }

        // Export button — triggers re-auth dialog then exports
        findViewById<MaterialButton>(R.id.export_audit_button)?.setOnClickListener {
            showReAuthForExport()
        }

        viewModel.auditEvents.observe(this) { events ->
            adapter.submitList(events)
        }

        viewModel.exportResult.observe(this) { result ->
            result?.onSuccess { file ->
                Toast.makeText(this, "Audit exported to ${file.name}", Toast.LENGTH_LONG).show()
            }?.onFailure { error ->
                Toast.makeText(this, "Export failed: ${error.message}", Toast.LENGTH_LONG).show()
            }
        }

        viewModel.loadPage(0)
    }

    private fun showReAuthForExport() {
        supportFragmentManager.setFragmentResultListener(
            ReAuthDialogFragment.REQUEST_KEY, this
        ) { _, bundle ->
            val authenticated = bundle.getBoolean(ReAuthDialogFragment.RESULT_AUTHENTICATED, false)
            if (authenticated) {
                val password = ReAuthDialogFragment.consumePassword()
                if (password != null) {
                    val exportFile = File(filesDir, "audit_export_${System.currentTimeMillis()}.csv")
                    viewModel.exportAuditLog(exportFile, password)
                }
            }
        }
        val dialog = ReAuthDialogFragment.newInstance(
            com.eaglepoint.storefront.security.SessionManager(this).requireUserId()
        )
        dialog.show(supportFragmentManager, "reauth_export")
    }
}
