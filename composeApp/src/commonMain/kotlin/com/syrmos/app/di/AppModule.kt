package com.syrmos.app.di

import com.syrmos.core.data.di.dataModule
import com.syrmos.core.database.di.databaseModule
import com.syrmos.core.domain.di.domainModule
import com.syrmos.core.network.di.networkModule
import com.syrmos.feature.home.HomeViewModel
import com.syrmos.feature.home.assistant.AssistantViewModel
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
            getLastTrain = get(),
            getLinesUseCase = get(),
            getLineDetail = get(),
            announcementsRepository = get(),
            liveTrackerService = get(),
            weatherRepository = get(),
        )
    }
    factory {
        AssistantViewModel(
            stationRepository = get(),
            getLinesUseCase = get(),
            getNextDepartures = get(),
            bandProjector = get(),
            getLastTrain = get(),
            planJourney = get(),
            planByArrival = get(),
            searchStations = get(),
            findNearestStation = get(),
            announcementsRepository = get(),
            faresRepository = get(),
            favoritesRepository = get(),
            weatherRepository = get(),
            queryNormalizer = com.syrmos.app.platform.provideQueryNormalizer(),
            assistantClassifier = com.syrmos.app.platform.provideAssistantClassifier(),
            modelDownloader = com.syrmos.app.platform.provideModelDownloader(),
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
            livePositionsService = get(),
            stationOffsetsRepo = get(),
            scheduleSyncRepository = get(),
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
