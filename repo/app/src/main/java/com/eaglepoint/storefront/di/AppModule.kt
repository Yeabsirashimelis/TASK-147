package com.eaglepoint.storefront.di

import com.eaglepoint.storefront.data.repository.ArticleRepository
import com.eaglepoint.storefront.data.repository.AuditRepository
import com.eaglepoint.storefront.data.repository.AuthRepository
import com.eaglepoint.storefront.data.repository.BackupRepository
import com.eaglepoint.storefront.data.repository.BatchValidationRepository
import com.eaglepoint.storefront.data.repository.CartRepository
import com.eaglepoint.storefront.data.repository.CatalogRepository
import com.eaglepoint.storefront.data.repository.IngestionAlertRepository
import com.eaglepoint.storefront.data.repository.IngestionJobRunRepository
import com.eaglepoint.storefront.data.repository.SourceRuleRepository
import com.eaglepoint.storefront.domain.usecase.BackupUseCase
import com.eaglepoint.storefront.domain.usecase.CheckIngestionAlertsUseCase
import com.eaglepoint.storefront.domain.usecase.CreateUserUseCase
import com.eaglepoint.storefront.domain.usecase.GetAuditLogUseCase
import com.eaglepoint.storefront.domain.usecase.GetBatchQualityReportUseCase
import com.eaglepoint.storefront.domain.usecase.GetIngestionLogsUseCase
import com.eaglepoint.storefront.domain.usecase.GetSourceRulesUseCase
import com.eaglepoint.storefront.domain.usecase.LogAuditEventUseCase
import com.eaglepoint.storefront.domain.usecase.LoginUseCase
import com.eaglepoint.storefront.domain.usecase.ReAuthenticateUseCase
import com.eaglepoint.storefront.domain.usecase.RestoreUseCase
import com.eaglepoint.storefront.domain.usecase.RunIngestionUseCase
import com.eaglepoint.storefront.domain.usecase.SaveSourceRuleUseCase
import com.eaglepoint.storefront.domain.usecase.ScheduleIngestionUseCase
import com.eaglepoint.storefront.domain.usecase.AddToCartUseCase
import com.eaglepoint.storefront.domain.usecase.ApplyCouponUseCase
import com.eaglepoint.storefront.domain.usecase.CalculatePriceUseCase
import com.eaglepoint.storefront.domain.usecase.CheckoutUseCase
import com.eaglepoint.storefront.domain.usecase.GetArticleDetailUseCase
import com.eaglepoint.storefront.domain.usecase.MergeCartsUseCase
import com.eaglepoint.storefront.domain.usecase.SaveArticleOfflineUseCase
import com.eaglepoint.storefront.domain.usecase.SearchArticlesUseCase
import com.eaglepoint.storefront.domain.usecase.UpdateCartUseCase
import com.eaglepoint.storefront.domain.usecase.ValidateBatchUseCase
import com.eaglepoint.storefront.ui.home.ImageLoader
import com.eaglepoint.storefront.ingestion.ContentFilter
import com.eaglepoint.storefront.ingestion.FeedParser
import com.eaglepoint.storefront.ingestion.IngestionEngine
import com.eaglepoint.storefront.ingestion.RequestPacer
import com.eaglepoint.storefront.ingestion.UserAgentRotator
import com.eaglepoint.storefront.pricing.PricingEngine
import com.eaglepoint.storefront.quality.BatchValidator
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    // Repositories
    single { AuthRepository(get(), get()) }
    single { AuditRepository(get()) }
    single { BackupRepository(get(), androidContext()) }
    single { SourceRuleRepository(get()) }
    single { IngestionJobRunRepository(get(), get(), get()) }
    single { ArticleRepository(get()) }
    single { IngestionAlertRepository(get()) }
    single { BatchValidationRepository(get(), get()) }
    single { CartRepository(get(), get()) }
    single { CatalogRepository(get(), get(), get(), get(), get()) }

    // Pricing engine
    single { PricingEngine() }

    // Ingestion engine components
    single { BatchValidator() }
    single { FeedParser() }
    single { ContentFilter() }
    single { UserAgentRotator() }
    single { RequestPacer() }
    single { IngestionEngine(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }

    // Auth use cases
    factory { LogAuditEventUseCase(get()) }
    factory { LoginUseCase(get(), get(), get(), get(), get()) }
    factory { ReAuthenticateUseCase(get(), get(), get()) }
    factory { CreateUserUseCase(get(), get(), get(), get(), get()) }
    factory { GetAuditLogUseCase(get(), get()) }
    factory { com.eaglepoint.storefront.domain.usecase.ExportAuditUseCase(get(), get(), get(), get(), get()) }
    factory { BackupUseCase(get(), get(), get(), get()) }
    factory { RestoreUseCase(get(), get(), get(), get()) }

    // Ingestion use cases
    factory { SaveSourceRuleUseCase(get(), get(), get(), get(), get()) }
    factory { RunIngestionUseCase(get(), get(), get()) }
    factory { GetIngestionLogsUseCase(get()) }
    factory { GetSourceRulesUseCase(get(), get(), get()) }
    factory { CheckIngestionAlertsUseCase(get(), get(), get()) }
    factory { ScheduleIngestionUseCase(androidContext()) }

    // Quality validation use cases
    factory { ValidateBatchUseCase(get(), get(), get(), get(), get(), get()) }
    factory { GetBatchQualityReportUseCase(get()) }

    // Article browsing use cases
    factory { SearchArticlesUseCase(get()) }
    factory { GetArticleDetailUseCase(get()) }
    factory { SaveArticleOfflineUseCase(get()) }

    // Order repository
    single { com.eaglepoint.storefront.data.repository.OrderRepository(get()) }

    // Cart & checkout use cases
    factory { AddToCartUseCase(get(), get()) }
    factory { UpdateCartUseCase(get(), get()) }
    factory { MergeCartsUseCase(get(), get()) }
    factory { ApplyCouponUseCase(get(), get()) }
    factory { CalculatePriceUseCase(get(), get()) }
    factory { CheckoutUseCase(get(), get(), get(), get(), get(), get()) }
    factory { com.eaglepoint.storefront.domain.usecase.GetOrdersUseCase(get(), get(), get()) }

    // Notification system
    single { com.eaglepoint.storefront.notification.TemplateRenderer() }
    single { com.eaglepoint.storefront.data.repository.NotificationRepository(get(), get()) }
    single { com.eaglepoint.storefront.notification.NotificationDispatcher(get()) }
    factory { com.eaglepoint.storefront.domain.usecase.SendNotificationUseCase(get(), get(), get()) }
    factory { com.eaglepoint.storefront.domain.usecase.GetNotificationsUseCase(get()) }

    // Editor/Analyst tools
    single { com.eaglepoint.storefront.domain.usecase.RoleGuard(get()) }
    factory { com.eaglepoint.storefront.domain.usecase.CurateArticleUseCase(get(), get(), get(), get()) }
    factory { com.eaglepoint.storefront.domain.usecase.ReviewIngestionUseCase(get(), get(), get(), get(), get()) }

    // Image loading
    single { ImageLoader() }
}
