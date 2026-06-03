package com.syrmos.app

import androidx.compose.ui.window.ComposeUIViewController
import com.syrmos.app.di.appModules
import com.syrmos.core.database.DatabaseDriverFactory
import com.syrmos.core.data.seed.ResourceReader
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

private val iosPlatformModule = module {
    single { DatabaseDriverFactory() }
    single { ResourceReader() }
}

private var koinStarted = false

fun MainViewController() = run {
    if (!koinStarted) {
        startKoin {
            modules(iosPlatformModule + appModules)
        }
        koinStarted = true
    }
    ComposeUIViewController { SyrmosApp() }
}
