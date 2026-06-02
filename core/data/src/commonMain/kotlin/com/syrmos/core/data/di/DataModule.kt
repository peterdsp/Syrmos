package com.syrmos.core.data.di

import com.syrmos.core.data.repository.LineRepositoryImpl
import com.syrmos.core.data.repository.ScheduleRepositoryImpl
import com.syrmos.core.data.repository.StationRepositoryImpl
import com.syrmos.core.data.seed.DataSeeder
import org.koin.dsl.module

val dataModule = module {
    single { DataSeeder(database = get(), resourceReader = get()) }
    single { LineRepositoryImpl(database = get()) }
    single { StationRepositoryImpl(database = get()) }
    single { ScheduleRepositoryImpl(database = get()) }
}
