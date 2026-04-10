package com.eaglepoint.storefront.ui.ingestion.log

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import org.koin.androidx.viewmodel.ext.android.viewModel

class IngestionLogActivity : AppCompatActivity() {

    private val viewModel: IngestionLogViewModel by viewModel()
    private val adapter = IngestionJobRunAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ingestion_log)

        val recyclerView = findViewById<RecyclerView>(R.id.ingestion_log_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val sourceRuleId = intent.getStringExtra(EXTRA_SOURCE_RULE_ID)
        viewModel.setSourceFilter(sourceRuleId)

        viewModel.jobRuns.observe(this) { runs ->
            adapter.submitList(runs)
        }
    }

    companion object {
        const val EXTRA_SOURCE_RULE_ID = "extra_source_rule_id"
    }
}
