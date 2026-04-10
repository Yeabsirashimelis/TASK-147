package com.eaglepoint.storefront.ui.ingestion.console

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.IngestionStatus
import com.eaglepoint.storefront.ui.ingestion.editor.SourceRuleEditorActivity
import com.eaglepoint.storefront.ui.ingestion.log.IngestionLogActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.koin.androidx.viewmodel.ext.android.viewModel

class IngestionConsoleActivity : AppCompatActivity() {

    private val viewModel: IngestionConsoleViewModel by viewModel()

    private lateinit var sourceAdapter: SourceRuleAdapter
    private lateinit var alertAdapter: AlertAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ingestion_console)

        setupSourceList()
        setupAlertList()
        setupButtons()
        observeState()
    }

    private fun setupSourceList() {
        sourceAdapter = SourceRuleAdapter(
            onEditClick = { rule ->
                val intent = Intent(this, SourceRuleEditorActivity::class.java)
                intent.putExtra(SourceRuleEditorActivity.EXTRA_SOURCE_RULE_ID, rule.id)
                startActivity(intent)
            },
            onRunClick = { rule ->
                viewModel.runIngestionForSource(rule.id)
            },
            onLogsClick = { rule ->
                val intent = Intent(this, IngestionLogActivity::class.java)
                intent.putExtra(IngestionLogActivity.EXTRA_SOURCE_RULE_ID, rule.id)
                startActivity(intent)
            }
        )

        val recyclerView = findViewById<RecyclerView>(R.id.source_rules_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = sourceAdapter
    }

    private fun setupAlertList() {
        alertAdapter = AlertAdapter { alert ->
            viewModel.acknowledgeAlert(alert.id)
        }

        val alertRecycler = findViewById<RecyclerView>(R.id.alerts_recycler)
        alertRecycler.layoutManager = LinearLayoutManager(this)
        alertRecycler.adapter = alertAdapter
    }

    private fun setupButtons() {
        val addSourceFab = findViewById<FloatingActionButton>(R.id.add_source_fab)
        addSourceFab.setOnClickListener {
            val intent = Intent(this, SourceRuleEditorActivity::class.java)
            startActivity(intent)
        }

        val scheduleButton = findViewById<MaterialButton>(R.id.schedule_ingestion_button)
        scheduleButton.setOnClickListener {
            viewModel.scheduleIngestion()
            Toast.makeText(this, getString(R.string.ingestion_scheduled), Toast.LENGTH_SHORT).show()
        }

        val viewAllLogsButton = findViewById<MaterialButton>(R.id.view_all_logs_button)
        viewAllLogsButton.setOnClickListener {
            startActivity(Intent(this, IngestionLogActivity::class.java))
        }
    }

    private fun observeState() {
        viewModel.sourceRules.observe(this) { rules ->
            sourceAdapter.submitList(rules)
        }

        viewModel.unacknowledgedAlerts.observe(this) { alerts ->
            alertAdapter.submitList(alerts)
            val alertSection = findViewById<View>(R.id.alert_section)
            alertSection.visibility = if (alerts.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.alertCount.observe(this) { count ->
            val badge = findViewById<View>(R.id.alert_badge_count)
            if (badge is android.widget.TextView) {
                badge.text = count.toString()
                badge.visibility = if (count > 0) View.VISIBLE else View.GONE
            }
        }

        viewModel.runResult.observe(this) { result ->
            result.onSuccess { run ->
                val message = when (run.status) {
                    IngestionStatus.SUCCESS -> getString(R.string.ingestion_success, run.itemsStored)
                    else -> getString(R.string.ingestion_partial, run.failureReason ?: "")
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }.onFailure { error ->
                Toast.makeText(
                    this,
                    getString(R.string.ingestion_error, error.message),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        viewModel.isRunning.observe(this) { running ->
            val progressBar = findViewById<View>(R.id.ingestion_progress)
            progressBar.visibility = if (running) View.VISIBLE else View.GONE
        }
    }
}
