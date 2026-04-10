package com.eaglepoint.storefront

import android.app.Application
import com.eaglepoint.storefront.di.appModule
import com.eaglepoint.storefront.di.databaseModule
import com.eaglepoint.storefront.di.securityModule
import com.eaglepoint.storefront.di.viewModelModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class StorefrontApp : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@StorefrontApp)
            modules(
                databaseModule,
                securityModule,
                appModule,
                viewModelModule
            )
        }
    }
}
