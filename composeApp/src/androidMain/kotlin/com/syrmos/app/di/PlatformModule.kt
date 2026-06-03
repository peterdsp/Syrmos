package com.syrmos.app.di

import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.database.DatabaseDriverFactory
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val androidPlatformModule = module {
    single { DatabaseDriverFactory(androidContext()) }
    single { ResourceReader(androidContext()) }
}
