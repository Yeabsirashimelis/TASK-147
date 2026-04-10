package com.eaglepoint.storefront.ui.editor.review

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.ui.ingestion.log.IngestionJobRunAdapter
import com.eaglepoint.storefront.ui.quality.ValidationErrorAdapter
import org.koin.androidx.viewmodel.ext.android.viewModel

class FailureInvestigationActivity : AppCompatActivity() {

    private val viewModel: FailureInvestigationViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_failure_investigation)

        val jobRunAdapter = IngestionJobRunAdapter()
        val jobRunsRecycler = findViewById<RecyclerView>(R.id.investigation_job_runs)
        jobRunsRecycler.layoutManager = LinearLayoutManager(this)
        jobRunsRecycler.adapter = jobRunAdapter

        val errorAdapter = ValidationErrorAdapter()
        val errorsRecycler = findViewById<RecyclerView>(R.id.investigation_errors)
        errorsRecycler.layoutManager = LinearLayoutManager(this)
        errorsRecycler.adapter = errorAdapter

        viewModel.jobRuns.observe(this) { runs ->
            jobRunAdapter.submitList(runs)
        }

        viewModel.validationErrors.observe(this) { errors ->
            errorAdapter.submitList(errors)
            val errorSection = findViewById<View>(R.id.investigation_error_section)
            errorSection.visibility = if (errors.isNotEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.setSourceFilter(null) // load all
    }
}
