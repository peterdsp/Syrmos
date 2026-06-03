package com.syrmos.app.di

import com.syrmos.core.data.di.dataModule
import com.syrmos.core.database.di.databaseModule
import com.syrmos.core.domain.di.domainModule
import com.syrmos.core.network.di.networkModule
import com.syrmos.feature.home.HomeViewModel
import com.syrmos.feature.lines.LinesViewModel
import org.koin.dsl.module

val featureModule = module {
    factory {
        HomeViewModel(
            findNearestStation = get(),
            getNextDepartures = get(),
            getLinesUseCase = get(),
            stasyService = get(),
        )
    }
    factory { LinesViewModel(getLinesUseCase = get()) }
}

val appModules = listOf(
    databaseModule,
    networkModule,
    dataModule,
    domainModule,
    featureModule,
)
