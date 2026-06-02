package com.syrmos.core.domain.di

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
    factory { GetNextDeparturesUseCase(scheduleRepository = get()) }
    factory { SearchStationsUseCase(stationRepository = get()) }
    factory { FindNearestStationUseCase(stationRepository = get()) }
}
