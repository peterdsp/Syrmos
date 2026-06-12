package com.syrmos.core.data.di

import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.ScheduleRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.data.repository.TransitPatternRepositoryImpl
import com.syrmos.core.data.seed.DataSeeder
import com.syrmos.core.data.seed.LinesRefresher
import com.syrmos.core.data.sync.ScheduleSyncRepository
import com.syrmos.core.data.sync.VisualOverridesRepository
import org.koin.dsl.module

val dataModule = module {
    single { DataSeeder(database = get(), resourceReader = get()) }
    single { LinesRefresher(database = get(), linesService = get()) }
    single { ScheduleSyncRepository(schedulesService = get(), resourceReader = get()) }
    single { VisualOverridesRepository(service = get()) }
    single { LineRepositoryImpl(database = get(), resourceReader = get()) }
    single { StationRepositoryImpl(database = get(), resourceReader = get()) }
    single { ScheduleRepositoryImpl(database = get()) }
    single { TransitPatternRepositoryImpl(resourceReader = get()) }
}
