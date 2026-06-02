package com.syrmos.core.database.di

import com.syrmos.core.database.DatabaseDriverFactory
import com.syrmos.core.database.SyrmosDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule: Module = module {
    single { get<DatabaseDriverFactory>().create() }
    single { SyrmosDatabase(get()) }
}
