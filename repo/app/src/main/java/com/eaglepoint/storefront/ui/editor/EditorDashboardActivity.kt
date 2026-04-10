package com.eaglepoint.storefront.ui.editor

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.storefront.R
import com.eaglepoint.storefront.domain.model.UserRole
import com.eaglepoint.storefront.security.SessionManager
import com.eaglepoint.storefront.ui.editor.curation.ArticleCurationActivity
import com.eaglepoint.storefront.ui.editor.review.FailureInvestigationActivity
import com.google.android.material.button.MaterialButton
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

class EditorDashboardActivity : AppCompatActivity() {

    private val viewModel: EditorDashboardViewModel by viewModel()
    private val sessionManager: SessionManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editor_dashboard)

        val role = sessionManager.currentRole ?: UserRole.EDITOR

        val curateButton = findViewById<MaterialButton>(R.id.editor_curate_button)
        curateButton.setOnClickListener {
            startActivity(Intent(this, ArticleCurationActivity::class.java))
        }

        val investigateButton = findViewById<MaterialButton>(R.id.editor_investigate_button)
        investigateButton.setOnClickListener {
            startActivity(Intent(this, FailureInvestigationActivity::class.java))
        }

        // Hide curation for Analyst (read-only for ingestion logs)
        if (role == UserRole.ANALYST) {
            curateButton.isEnabled = false
            curateButton.alpha = 0.5f
        }

        viewModel.pendingReviewCount.observe(this) { count ->
            val badge = findViewById<TextView>(R.id.editor_pending_count)
            badge.text = getString(R.string.pending_review_count, count)
        }

        val jobRunsRecycler = findViewById<RecyclerView>(R.id.editor_job_runs_recycler)
        jobRunsRecycler.layoutManager = LinearLayoutManager(this)
        val adapter = com.eaglepoint.storefront.ui.ingestion.log.IngestionJobRunAdapter()
        jobRunsRecycler.adapter = adapter

        viewModel.getRecentJobRuns().observe(this) { runs ->
            adapter.submitList(runs)
        }
    }
}
