package com.eaglepoint.storefront.di

import com.eaglepoint.storefront.ui.article.ArticleDetailViewModel
import com.eaglepoint.storefront.ui.audit.AuditLogViewModel
import com.eaglepoint.storefront.ui.cart.CartViewModel
import com.eaglepoint.storefront.ui.checkout.CheckoutViewModel
import com.eaglepoint.storefront.ui.backup.BackupRestoreViewModel
import com.eaglepoint.storefront.ui.ingestion.console.IngestionConsoleViewModel
import com.eaglepoint.storefront.ui.ingestion.editor.SourceRuleEditorViewModel
import com.eaglepoint.storefront.ui.ingestion.log.IngestionLogViewModel
import com.eaglepoint.storefront.ui.home.HomeViewModel
import com.eaglepoint.storefront.ui.login.LoginViewModel
import com.eaglepoint.storefront.ui.quality.BatchQualityViewModel
import com.eaglepoint.storefront.ui.reauth.ReAuthViewModel
import com.eaglepoint.storefront.ui.saved.SavedArticlesViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { LoginViewModel(get(), get(), get()) }
    viewModel { ReAuthViewModel(get()) }
    viewModel { AuditLogViewModel(get(), get()) }
    viewModel { BackupRestoreViewModel(get(), get(), get()) }
    viewModel { IngestionConsoleViewModel(get(), get(), get(), get()) }
    viewModel { SourceRuleEditorViewModel(get(), get()) }
    viewModel { IngestionLogViewModel(get()) }
    viewModel { BatchQualityViewModel(get()) }
    viewModel { HomeViewModel(get(), get()) }
    viewModel { ArticleDetailViewModel(get(), get()) }
    viewModel { SavedArticlesViewModel(get()) }
    viewModel { CartViewModel(get(), get(), get(), get(), get()) }
    viewModel { CheckoutViewModel(get(), get(), get()) }
    viewModel { com.eaglepoint.storefront.ui.notifications.NotificationsViewModel(get()) }
    viewModel { com.eaglepoint.storefront.ui.editor.EditorDashboardViewModel(get()) }
    viewModel { com.eaglepoint.storefront.ui.editor.curation.ArticleCurationViewModel(get(), get()) }
    viewModel { com.eaglepoint.storefront.ui.editor.review.FailureInvestigationViewModel(get()) }
    viewModel { com.eaglepoint.storefront.ui.receipt.ReceiptListViewModel(get()) }
}
