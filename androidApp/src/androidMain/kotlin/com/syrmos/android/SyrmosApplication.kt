package com.syrmos.android

import android.app.Application
import com.syrmos.app.di.androidPlatformModule
import com.syrmos.app.di.appModules
import com.syrmos.app.platform.initLocationProvider
import com.syrmos.app.platform.initAndroidPlatform
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SyrmosApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initAndroidPlatform(this)
        initLocationProvider(this)
        startKoin {
            androidContext(this@SyrmosApplication)
            modules(androidPlatformModule + appModules)
        }
    }
}
