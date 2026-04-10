package com.eaglepoint.storefront.ui.reauth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.eaglepoint.storefront.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.koin.androidx.viewmodel.ext.android.viewModel

class ReAuthDialogFragment : DialogFragment() {

    private val viewModel: ReAuthViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_reauth, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val passwordLayout = view.findViewById<TextInputLayout>(R.id.reauth_password_layout)
        val passwordInput = view.findViewById<TextInputEditText>(R.id.reauth_password_input)
        val confirmButton = view.findViewById<MaterialButton>(R.id.reauth_confirm_button)
        val cancelButton = view.findViewById<MaterialButton>(R.id.reauth_cancel_button)

        val userId = arguments?.getString(ARG_USER_ID) ?: run {
            dismiss()
            return
        }

        confirmButton.setOnClickListener {
            val password = passwordInput.text?.toString()?.toCharArray() ?: charArrayOf()
            if (password.isEmpty()) {
                passwordLayout.error = getString(R.string.error_password_required)
                return@setOnClickListener
            }
            passwordLayout.error = null
            viewModel.reAuthenticate(userId, password)
        }

        cancelButton.setOnClickListener {
            setFragmentResult(REQUEST_KEY, bundleOf(RESULT_AUTHENTICATED to false))
            dismiss()
        }

        viewModel.reAuthResult.observe(viewLifecycleOwner) { success ->
            if (!success) {
                passwordLayout.error = getString(R.string.error_reauth_failed)
                setFragmentResult(REQUEST_KEY, bundleOf(RESULT_AUTHENTICATED to false))
            } else {
                // Securely pass password via companion holder for immediate consumption
                val pw = passwordInput.text?.toString()?.toCharArray() ?: charArrayOf()
                lastAuthenticatedPassword = pw
                setFragmentResult(REQUEST_KEY, bundleOf(RESULT_AUTHENTICATED to true))
                dismiss()
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            confirmButton.isEnabled = !loading
        }
    }

    companion object {
        const val REQUEST_KEY = "reauth_request"
        const val RESULT_AUTHENTICATED = "authenticated"
        private const val ARG_USER_ID = "user_id"

        /**
         * Transient holder for the re-auth password, consumed immediately after successful auth.
         * Cleared after consumption to avoid lingering in memory.
         */
        @Volatile
        var lastAuthenticatedPassword: CharArray? = null
            private set

        fun consumePassword(): CharArray? {
            val pw = lastAuthenticatedPassword
            lastAuthenticatedPassword = null
            return pw
        }

        fun newInstance(userId: String): ReAuthDialogFragment {
            return ReAuthDialogFragment().apply {
                arguments = bundleOf(ARG_USER_ID to userId)
            }
        }
    }
}
