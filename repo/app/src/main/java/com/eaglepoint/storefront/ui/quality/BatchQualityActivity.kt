package com.eaglepoint.storefront.ui.quality

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.BatchQualityReport
import org.koin.androidx.viewmodel.ext.android.viewModel

class BatchQualityActivity : AppCompatActivity() {

    private val viewModel: BatchQualityViewModel by viewModel()

    private lateinit var batchAdapter: BatchQualityAdapter
    private lateinit var errorAdapter: ValidationErrorAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_quality)

        setupBatchList()
        setupErrorList()
        observeState()
    }

    private fun setupBatchList() {
        batchAdapter = BatchQualityAdapter { batch ->
            viewModel.loadReport(batch.id)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.batch_list_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = batchAdapter
    }

    private fun setupErrorList() {
        errorAdapter = ValidationErrorAdapter()

        val recyclerView = findViewById<RecyclerView>(R.id.error_list_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = errorAdapter
    }

    private fun observeState() {
        viewModel.recentBatches.observe(this) { batches ->
            batchAdapter.submitList(batches)
        }

        viewModel.selectedReport.observe(this) { report ->
            val detailSection = findViewById<View>(R.id.report_detail_section)
            if (report != null) {
                detailSection.visibility = View.VISIBLE
                bindReportDetail(report)
            } else {
                detailSection.visibility = View.GONE
            }
        }

        viewModel.batchErrors.observe(this) { errors ->
            errorAdapter.submitList(errors)
            val errorSection = findViewById<View>(R.id.error_section)
            errorSection.visibility = if (errors.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.isLoading.observe(this) { loading ->
            val progressBar = findViewById<View>(R.id.quality_progress)
            progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }
    }

    private fun bindReportDetail(report: BatchQualityReport) {
        findViewById<TextView>(R.id.report_batch_name).text = report.batchVersion.batchName
        findViewById<TextView>(R.id.report_total_items).text =
            getString(R.string.quality_total_items, report.totalItems)
        findViewById<TextView>(R.id.report_error_count).text =
            getString(R.string.quality_error_count, report.errorCount)
        findViewById<TextView>(R.id.report_error_rate).text =
            getString(R.string.quality_error_rate, report.errorRate * 100)
        findViewById<TextView>(R.id.report_status).text =
            if (report.passed) getString(R.string.quality_passed) else getString(R.string.quality_failed)
        findViewById<TextView>(R.id.report_status).setTextColor(
            getColor(if (report.passed) R.color.status_success else R.color.status_failure)
        )

        // Show error breakdown by rule
        val breakdownText = findViewById<TextView>(R.id.report_breakdown)
        if (report.errorsByRule.isNotEmpty()) {
            breakdownText.text = report.errorsByRule.entries.joinToString("\n") { (rule, count) ->
                "$rule: $count"
            }
            breakdownText.visibility = View.VISIBLE
        } else {
            breakdownText.visibility = View.GONE
        }
    }
}
