package com.syrmos.core.domain.di

import com.syrmos.core.domain.live.HellenicTrainLiveArrivalsProvider
import com.syrmos.core.domain.live.LiveArrivalsProvider
import com.syrmos.core.domain.live.LiveArrivalsRouter
import com.syrmos.core.domain.live.OasaLiveArrivalsProvider
import com.syrmos.core.domain.live.StasyLiveArrivalsProvider
import com.syrmos.core.domain.usecase.ComputeDeparturesFromBandsUseCase
import com.syrmos.core.domain.usecase.FindNearestStationUseCase
import com.syrmos.core.domain.usecase.GetLineDetailUseCase
import com.syrmos.core.domain.usecase.GetLinesUseCase
import com.syrmos.core.domain.usecase.GetNextDeparturesUseCase
import com.syrmos.core.domain.usecase.GetStationDetailUseCase
import com.syrmos.core.domain.usecase.SearchStationsUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { GetLinesUseCase(lineRepository = get()) }
    factory { GetLineDetailUseCase(lineRepository = get(), stationRepository = get(), scheduleRepository = get()) }
    factory { GetStationDetailUseCase(stationRepository = get(), lineRepository = get()) }
    factory { ComputeDeparturesFromBandsUseCase(scheduleSync = get()) }
    factory { GetNextDeparturesUseCase(scheduleRepository = get(), bandProjector = get()) }
    factory { SearchStationsUseCase(stationRepository = get()) }
    factory { FindNearestStationUseCase(stationRepository = get()) }

    // Live arrivals infrastructure. All providers return null today (no
    // operator publishes a real-time arrivals feed for Athens). When any
    // operator does, fill in the relevant provider — no other code changes.
    single { StasyLiveArrivalsProvider() }
    single { OasaLiveArrivalsProvider() }
    single { HellenicTrainLiveArrivalsProvider() }
    single {
        LiveArrivalsRouter(
            providers = listOf<LiveArrivalsProvider>(
                get<StasyLiveArrivalsProvider>(),
                get<OasaLiveArrivalsProvider>(),
                get<HellenicTrainLiveArrivalsProvider>(),
            )
        )
    }
}
