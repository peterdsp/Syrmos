package com.syrmos.android

import android.app.Application
import com.syrmos.app.di.androidPlatformModule
import com.syrmos.app.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SyrmosApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SyrmosApplication)
            modules(androidPlatformModule + appModules)
        }
    }
}
