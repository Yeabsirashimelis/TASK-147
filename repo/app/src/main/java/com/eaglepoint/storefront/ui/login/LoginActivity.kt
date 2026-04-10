package com.eaglepoint.storefront.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.AuthResult
import com.eaglepoint.storefront.ui.audit.AuditLogActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.koin.androidx.viewmodel.ext.android.viewModel

class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModel()

    private lateinit var usernameLayout: TextInputLayout
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var passwordInput: TextInputEditText
    private lateinit var actionButton: MaterialButton
    private lateinit var progressBar: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        usernameLayout = findViewById(R.id.username_layout)
        usernameInput = findViewById(R.id.username_input)
        passwordLayout = findViewById(R.id.password_layout)
        passwordInput = findViewById(R.id.password_input)
        actionButton = findViewById(R.id.action_button)
        progressBar = findViewById(R.id.progress_bar)

        observeState()
        viewModel.checkFirstLaunch()
    }

    private fun observeState() {
        viewModel.isFirstLaunch.observe(this) { isFirst ->
            actionButton.text = if (isFirst) {
                getString(R.string.create_account)
            } else {
                getString(R.string.login)
            }
            actionButton.setOnClickListener {
                val username = usernameInput.text?.toString()?.trim() ?: ""
                val password = passwordInput.text?.toString()?.toCharArray() ?: charArrayOf()

                if (username.isEmpty()) {
                    usernameLayout.error = getString(R.string.error_username_required)
                    return@setOnClickListener
                }
                if (password.isEmpty()) {
                    passwordLayout.error = getString(R.string.error_password_required)
                    return@setOnClickListener
                }

                usernameLayout.error = null
                passwordLayout.error = null

                if (isFirst) {
                    viewModel.createFirstUser(username, password)
                } else {
                    viewModel.login(username, password)
                }
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            actionButton.isEnabled = !loading
        }

        viewModel.loginState.observe(this) { result ->
            when (result) {
                is AuthResult.Success -> {
                    startActivity(Intent(this, com.eaglepoint.storefront.ui.home.HomeActivity::class.java))
                    finish()
                }
                is AuthResult.Failure -> {
                    passwordLayout.error = result.reason
                }
                is AuthResult.AccountLocked -> {
                    Toast.makeText(this, getString(R.string.account_locked), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
