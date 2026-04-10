package com.eaglepoint.storefront.ui.ingestion.editor

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.FeedType
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.koin.androidx.viewmodel.ext.android.viewModel

class SourceRuleEditorActivity : AppCompatActivity() {

    private val viewModel: SourceRuleEditorViewModel by viewModel()

    private var existingId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_source_rule_editor)

        existingId = intent.getStringExtra(EXTRA_SOURCE_RULE_ID)

        setupFeedTypeDropdown()
        setupSaveButton()
        observeState()

        if (existingId != null) {
            viewModel.loadSourceRule(existingId!!)
        }
    }

    private fun setupFeedTypeDropdown() {
        val feedTypeDropdown = findViewById<AutoCompleteTextView>(R.id.feed_type_dropdown)
        val feedTypes = FeedType.entries.map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, feedTypes)
        feedTypeDropdown.setAdapter(adapter)
        feedTypeDropdown.setText(FeedType.RSS.name, false)
    }

    private fun setupSaveButton() {
        val saveButton = findViewById<MaterialButton>(R.id.save_source_button)
        saveButton.setOnClickListener {
            val name = findViewById<TextInputEditText>(R.id.source_name_input).text?.toString() ?: ""
            val url = findViewById<TextInputEditText>(R.id.source_url_input).text?.toString() ?: ""
            val feedTypeText = findViewById<AutoCompleteTextView>(R.id.feed_type_dropdown).text?.toString() ?: ""
            val parseSelector = findViewById<TextInputEditText>(R.id.parse_selector_input).text?.toString()
            val allowedDomains = findViewById<TextInputEditText>(R.id.allowed_domains_input).text?.toString() ?: ""
            val blockedDomains = findViewById<TextInputEditText>(R.id.blocked_domains_input).text?.toString() ?: ""
            val allowedKeywords = findViewById<TextInputEditText>(R.id.allowed_keywords_input).text?.toString() ?: ""
            val blockedKeywords = findViewById<TextInputEditText>(R.id.blocked_keywords_input).text?.toString() ?: ""
            val intervalStr = findViewById<TextInputEditText>(R.id.interval_input).text?.toString() ?: "6"
            val delayStr = findViewById<TextInputEditText>(R.id.delay_input).text?.toString() ?: "2000"

            // Basic validation
            if (name.isBlank()) {
                findViewById<TextInputLayout>(R.id.source_name_layout).error = getString(R.string.error_name_required)
                return@setOnClickListener
            }
            if (url.isBlank()) {
                findViewById<TextInputLayout>(R.id.source_url_layout).error = getString(R.string.error_url_required)
                return@setOnClickListener
            }

            val feedType = try {
                FeedType.valueOf(feedTypeText)
            } catch (e: Exception) {
                FeedType.RSS
            }

            viewModel.save(
                existingId = existingId,
                name = name,
                url = url,
                feedType = feedType,
                parseSelector = parseSelector,
                allowedDomains = allowedDomains,
                blockedDomains = blockedDomains,
                allowedKeywords = allowedKeywords,
                blockedKeywords = blockedKeywords,
                intervalHours = intervalStr.toIntOrNull() ?: 6,
                requestDelayMs = delayStr.toLongOrNull() ?: 2000L
            )
        }
    }

    private fun observeState() {
        viewModel.sourceRule.observe(this) { rule ->
            if (rule != null) {
                findViewById<TextInputEditText>(R.id.source_name_input).setText(rule.name)
                findViewById<TextInputEditText>(R.id.source_url_input).setText(rule.url)
                findViewById<AutoCompleteTextView>(R.id.feed_type_dropdown).setText(rule.feedType.name, false)
                findViewById<TextInputEditText>(R.id.parse_selector_input).setText(rule.parseSelector ?: "")
                findViewById<TextInputEditText>(R.id.allowed_domains_input).setText(rule.allowedDomains.joinToString(", "))
                findViewById<TextInputEditText>(R.id.blocked_domains_input).setText(rule.blockedDomains.joinToString(", "))
                findViewById<TextInputEditText>(R.id.allowed_keywords_input).setText(rule.allowedKeywords.joinToString(", "))
                findViewById<TextInputEditText>(R.id.blocked_keywords_input).setText(rule.blockedKeywords.joinToString(", "))
                findViewById<TextInputEditText>(R.id.interval_input).setText(rule.intervalHours.toString())
                findViewById<TextInputEditText>(R.id.delay_input).setText(rule.requestDelayMs.toString())
            }
        }

        viewModel.saveResult.observe(this) { result ->
            result.onSuccess {
                Toast.makeText(this, getString(R.string.source_saved), Toast.LENGTH_SHORT).show()
                finish()
            }.onFailure { error ->
                Toast.makeText(this, getString(R.string.source_save_failed, error.message), Toast.LENGTH_LONG).show()
            }
        }

        viewModel.isLoading.observe(this) { loading ->
            val progressBar = findViewById<View>(R.id.editor_progress)
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    companion object {
        const val EXTRA_SOURCE_RULE_ID = "extra_source_rule_id"
        const val EXTRA_USER_ID = "extra_user_id"
    }
}
