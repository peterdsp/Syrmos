package com.syrmos.app.di

import com.syrmos.core.data.di.dataModule
import com.syrmos.core.database.di.databaseModule
import com.syrmos.core.domain.di.domainModule
import com.syrmos.core.network.di.networkModule
import com.syrmos.feature.home.HomeViewModel
import com.syrmos.feature.lines.LineDetailViewModel
import com.syrmos.feature.lines.LinesViewModel
import com.syrmos.feature.map.MapViewModel
import com.syrmos.feature.stations.StationDetailViewModel
import org.koin.dsl.module

val featureModule = module {
    factory {
        HomeViewModel(
            findNearestStation = get(),
            getNextDepartures = get(),
            getLinesUseCase = get(),
            stasyService = get(),
            liveTrackerService = get(),
        )
    }
    factory { LinesViewModel(getLinesUseCase = get()) }
    factory {
        LineDetailViewModel(
            getLineDetailUseCase = get(),
            liveTrackerService = get(),
        )
    }
    factory {
        MapViewModel(
            stationRepository = get(),
            lineRepository = get(),
            scheduleRepository = get(),
            getNextDepartures = get(),
            transitPatternRepository = get(),
            liveTrackerService = get(),
        )
    }
    factory { com.syrmos.core.domain.usecase.GetStationDeparturesUseCase(getNextDepartures = get(), transitPatternRepository = get()) }
    factory { StationDetailViewModel(getStationDetail = get(), getStationDepartures = get()) }
}

val appModules = listOf(
    databaseModule,
    networkModule,
    dataModule,
    domainModule,
    featureModule,
)
