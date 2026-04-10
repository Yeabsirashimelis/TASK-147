package com.eaglepoint.storefront.di

import com.eaglepoint.storefront.domain.validation.InputValidator
import com.eaglepoint.storefront.security.FieldEncryptor
import com.eaglepoint.storefront.security.KeystoreManager
import com.eaglepoint.storefront.security.PasswordHasher
import com.eaglepoint.storefront.security.SessionManager
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val securityModule = module {
    single { KeystoreManager() }
    single { FieldEncryptor(get()) }
    single { PasswordHasher() }
    single { InputValidator() }
    single { SessionManager(androidContext()) }
}
