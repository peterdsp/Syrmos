package com.syrmos.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import com.syrmos.app.di.appModules
import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.database.DatabaseDriverFactory
import org.koin.core.context.startKoin
import org.koin.dsl.module

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(
            module {
                single { DatabaseDriverFactory() }
                single { ResourceReader() }
            } + appModules,
        )
    }

    CanvasBasedWindow(canvasElementId = "ComposeTarget") {
        SyrmosApp()
    }
}
