package com.eaglepoint.storefront.di

import androidx.room.Room
import com.eaglepoint.storefront.data.db.StorefrontDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            StorefrontDatabase::class.java,
            StorefrontDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    single { get<StorefrontDatabase>().userDao() }
    single { get<StorefrontDatabase>().auditEventDao() }
    single { get<StorefrontDatabase>().sourceRuleDao() }
    single { get<StorefrontDatabase>().ingestionJobRunDao() }
    single { get<StorefrontDatabase>().articleDao() }
    single { get<StorefrontDatabase>().dataBatchVersionDao() }
    single { get<StorefrontDatabase>().dataLineageDao() }
    single { get<StorefrontDatabase>().ingestionAlertDao() }
    single { get<StorefrontDatabase>().validationErrorDao() }
    single { get<StorefrontDatabase>().cartDao() }
    single { get<StorefrontDatabase>().cartLineItemDao() }
    single { get<StorefrontDatabase>().catalogItemDao() }
    single { get<StorefrontDatabase>().inventorySnapshotDao() }
    single { get<StorefrontDatabase>().priceRuleDao() }
    single { get<StorefrontDatabase>().couponDao() }
    single { get<StorefrontDatabase>().taxRateDao() }
    single { get<StorefrontDatabase>().notificationDao() }
    single { get<StorefrontDatabase>().notificationTemplateDao() }
    single { get<StorefrontDatabase>().orderDao() }
}
