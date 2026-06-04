package com.syrmos.core.network.di

import com.syrmos.core.network.STASYAnnouncementService
import com.syrmos.core.network.RailwayGovLiveTrackerService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module

val networkModule = module {
    single {
        HttpClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                )
            }
        }
    }
    single { STASYAnnouncementService(httpClient = get()) }
    single { RailwayGovLiveTrackerService(httpClient = get()) }
}
