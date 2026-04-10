package com.eaglepoint.storefront.ui.backup

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.security.SessionManager
import com.eaglepoint.storefront.ui.reauth.ReAuthDialogFragment
import com.google.android.material.button.MaterialButton
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class BackupRestoreActivity : AppCompatActivity() {

    private val viewModel: BackupRestoreViewModel by viewModel()
    private val sessionManager: SessionManager by inject()

    private var pendingAction: PendingAction? = null

    private val createBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            pendingAction = PendingAction.Backup(uri)
            showReAuthDialog()
        }
    }

    private val openRestoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val metadataUri = android.net.Uri.parse("$uri.meta.json")
            pendingAction = PendingAction.Restore(uri, metadataUri)

            // First verify, then show confirmation
            viewModel.verifyBackup(uri, metadataUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_backup_restore)

        val backupButton = findViewById<MaterialButton>(R.id.create_backup_button)
        val restoreButton = findViewById<MaterialButton>(R.id.restore_backup_button)
        val progressBar = findViewById<View>(R.id.backup_progress_bar)

        backupButton.setOnClickListener {
            createBackupLauncher.launch("storefront_backup.db")
        }

        restoreButton.setOnClickListener {
            openRestoreLauncher.launch(arrayOf("application/octet-stream"))
        }

        setupReAuthResultListener()
        setupRestoreConfirmResultListener()
        observeState(progressBar)
    }

    private fun setupReAuthResultListener() {
        supportFragmentManager.setFragmentResultListener(
            ReAuthDialogFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            val authenticated = bundle.getBoolean(ReAuthDialogFragment.RESULT_AUTHENTICATED, false)
            if (!authenticated) {
                Toast.makeText(this, getString(R.string.reauth_required), Toast.LENGTH_SHORT).show()
                pendingAction = null
                return@setFragmentResultListener
            }

            val password = ReAuthDialogFragment.consumePassword()
            if (password == null || password.isEmpty()) {
                Toast.makeText(this, getString(R.string.reauth_required), Toast.LENGTH_SHORT).show()
                pendingAction = null
                return@setFragmentResultListener
            }

            val userId = sessionManager.requireUserId()

            when (val action = pendingAction) {
                is PendingAction.Backup -> {
                    viewModel.createBackup(userId, password, action.uri)
                    pendingAction = null
                }
                is PendingAction.Restore -> {
                    viewModel.restoreBackup(userId, password, action.sourceUri, action.metadataUri)
                    pendingAction = null
                }
                null -> {
                    password.fill('\u0000')
                }
            }
        }
    }

    private fun setupRestoreConfirmResultListener() {
        supportFragmentManager.setFragmentResultListener(
            RestoreConfirmDialogFragment.REQUEST_KEY,
            this
        ) { _, bundle ->
            val confirmed = bundle.getBoolean(RestoreConfirmDialogFragment.RESULT_CONFIRMED, false)
            if (!confirmed) {
                Toast.makeText(this, getString(R.string.restore_cancelled), Toast.LENGTH_SHORT).show()
                pendingAction = null
                return@setFragmentResultListener
            }

            // Restore confirmed; now require re-auth before executing
            val action = pendingAction
            if (action is PendingAction.Restore) {
                showReAuthDialog()
            }
        }
    }

    private fun observeState(progressBar: View) {
        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        viewModel.backupResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(
                    this,
                    getString(R.string.backup_success),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    this,
                    getString(R.string.backup_failed, error.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        viewModel.verifyResult.observe(this) { result ->
            result.onSuccess { metadata ->
                showRestoreConfirmDialog(metadata)
            }.onFailure { error ->
                Toast.makeText(
                    this,
                    getString(R.string.verify_failed, error.message),
                    Toast.LENGTH_LONG
                ).show()
                pendingAction = null
            }
        }

        viewModel.restoreResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(
                    this,
                    getString(R.string.restore_success),
                    Toast.LENGTH_LONG
                ).show()
                // Restart app after restore
                val intent = packageManager.getLaunchIntentForPackage(packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finishAffinity()
            }.onFailure { error ->
                Toast.makeText(
                    this,
                    getString(R.string.restore_failed, error.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showReAuthDialog() {
        val userId = sessionManager.currentUserId ?: return
        val dialog = ReAuthDialogFragment.newInstance(userId)
        dialog.show(supportFragmentManager, "reauth")
    }

    private fun showRestoreConfirmDialog(metadata: com.eaglepoint.storefront.domain.model.BackupMetadata) {
        val dialog = RestoreConfirmDialogFragment.newInstance(
            dbVersion = metadata.dbVersion,
            timestamp = metadata.timestamp,
            checksum = metadata.checksum,
            appVersion = metadata.appVersion
        )
        dialog.show(supportFragmentManager, "restore_confirm")
    }

    private sealed class PendingAction {
        data class Backup(val uri: android.net.Uri) : PendingAction()
        data class Restore(val sourceUri: android.net.Uri, val metadataUri: android.net.Uri) : PendingAction()
    }
}
