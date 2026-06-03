package com.syrmos.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import com.syrmos.app.di.appModules
import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.database.DatabaseDriverFactory
import com.syrmos.core.designsystem.theme.SyrmosTheme
import com.syrmos.feature.home.HomeScreen
import com.syrmos.feature.home.HomeViewModel
import com.syrmos.feature.lines.LinesScreen
import com.syrmos.feature.lines.LinesViewModel
import com.syrmos.feature.map.MapScreen
import com.syrmos.feature.settings.SettingsScreen
import org.koin.compose.koinInject
import org.koin.core.context.startKoin
import org.koin.dsl.module

private val iosPlatformModule = module {
    single { DatabaseDriverFactory() }
    single { ResourceReader() }
}

private var koinStarted = false

fun doInitKoin() {
    if (!koinStarted) {
        startKoin {
            modules(iosPlatformModule + appModules)
        }
        koinStarted = true
    }
}

fun makeTabViewController(tab: String) = ComposeUIViewController(
    configure = {
        enforceStrictPlistSanityCheck = false
    }
) {
    SyrmosTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            when (tab) {
                "home" -> {
                    val viewModel = koinInject<HomeViewModel>()
                    HomeScreen(viewModel = viewModel)
                }
                "lines" -> {
                    val viewModel = koinInject<LinesViewModel>()
                    LinesScreen(viewModel = viewModel)
                }
                "map" -> MapScreen()
                "settings" -> SettingsScreen()
                else -> {
                    val viewModel = koinInject<HomeViewModel>()
                    HomeScreen(viewModel = viewModel)
                }
            }
        }
    }
}

fun MainViewController() = run {
    doInitKoin()
    ComposeUIViewController { SyrmosApp() }
}
