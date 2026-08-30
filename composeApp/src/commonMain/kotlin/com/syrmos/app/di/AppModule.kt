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
    // single, not factory: these tab-root ViewModels own a custom CoroutineScope
    // with polling loops (Home's 60s connectivity probe, Map's 1s simulation +
    // 15s/30s pollers, Lines' collector) that is never cancelled. As factories,
    // Voyager recreating a tab's composition on every switch spun up a fresh VM
    // and leaked the old one's loops forever. A single instance bounds that to
    // one and keeps the tab's state across switches.
    single {
        HomeViewModel(
            findNearestStation = get(),
            getNextDepartures = get(),
            getLastTrain = get(),
            getLinesUseCase = get(),
            getLineDetail = get(),
            announcementsRepository = get(),
            liveTrackerService = get(),
            weatherRepository = get(),
            railNewsService = get(),
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
            ariadneChatService = get(),
        )
    }
    single { LinesViewModel(getLinesUseCase = get()) }
    factory {
        LineDetailViewModel(
            getLineDetailUseCase = get(),
            liveTrackerService = get(),
        )
    }
    single {
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
            computeActiveTrains = get(),
            announcementsRepository = get(),
            lineGeometryRepository = get(),
        )
    }
    factory { com.syrmos.core.domain.usecase.GetStationDeparturesUseCase(getNextDepartures = get(), transitPatternRepository = get()) }
    // single: same leak class as the tab ViewModels — its 15s departures loop
    // was never cancelled, so each station opened leaked another loop. A shared
    // instance switches its single refreshJob per station (loadStation cancels
    // the old one), so exactly one loop runs regardless of how many stations
    // are viewed.
    single { StationDetailViewModel(getStationDetail = get(), getStationDepartures = get()) }
}

val appModules = listOf(
    databaseModule,
    networkModule,
    dataModule,
    domainModule,
    featureModule,
)
