package com.eaglepoint.storefront.ui.backup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.eaglepoint.storefront.R
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RestoreConfirmDialogFragment : DialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_restore_confirm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dbVersion = arguments?.getInt(ARG_DB_VERSION, 0) ?: 0
        val timestamp = arguments?.getLong(ARG_TIMESTAMP, 0L) ?: 0L
        val checksum = arguments?.getString(ARG_CHECKSUM) ?: ""
        val appVersion = arguments?.getString(ARG_APP_VERSION) ?: ""

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val infoText = view.findViewById<TextView>(R.id.restore_info_text)
        infoText.text = buildString {
            appendLine("Database version: $dbVersion")
            appendLine("Backup date: ${dateFormat.format(Date(timestamp))}")
            appendLine("Checksum: ${checksum.take(16)}...")
            appendLine("App version: $appVersion")
            appendLine()
            appendLine("WARNING: This will replace all current data.")
        }

        val confirmButton = view.findViewById<MaterialButton>(R.id.restore_confirm_button)
        val cancelButton = view.findViewById<MaterialButton>(R.id.restore_cancel_button)

        confirmButton.setOnClickListener {
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CONFIRMED to true))
            dismiss()
        }

        cancelButton.setOnClickListener {
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_CONFIRMED to false))
            dismiss()
        }
    }

    companion object {
        const val REQUEST_KEY = "restore_confirm_request"
        const val RESULT_CONFIRMED = "confirmed"
        private const val ARG_DB_VERSION = "db_version"
        private const val ARG_TIMESTAMP = "timestamp"
        private const val ARG_CHECKSUM = "checksum"
        private const val ARG_APP_VERSION = "app_version"

        fun newInstance(
            dbVersion: Int,
            timestamp: Long,
            checksum: String,
            appVersion: String
        ): RestoreConfirmDialogFragment {
            return RestoreConfirmDialogFragment().apply {
                arguments = bundleOf(
                    ARG_DB_VERSION to dbVersion,
                    ARG_TIMESTAMP to timestamp,
                    ARG_CHECKSUM to checksum,
                    ARG_APP_VERSION to appVersion
                )
            }
        }
    }
}
