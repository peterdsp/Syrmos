package com.syrmos.core.network.di

import com.syrmos.core.network.AriadneChatService
import com.syrmos.core.network.CommunityReportService
import com.syrmos.core.network.OasaAirportBusService
import com.syrmos.core.network.RailNewsService
import com.syrmos.core.network.STASYAnnouncementService
import com.syrmos.core.network.RailwayGovLiveTrackerService
import com.syrmos.core.network.SyrmosLinesService
import com.syrmos.core.network.SyrmosContactService
import com.syrmos.core.network.SyrmosLivePositionsService
import com.syrmos.core.network.SyrmosSchedulesService
import com.syrmos.core.network.SyrmosVisualOverridesService
import com.syrmos.core.network.WeatherService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
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
            // Without this the Web (Ktor JS/fetch) engine has NO default timeout,
            // so a half-open socket (tunnel, network handoff, captive portal)
            // hangs the 10s live-poll loop forever with no recovery. Bound every
            // request so a stalled call fails and the loop retries.
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
            }
        }
    }
    single { RailNewsService(httpClient = get()) }
    single { STASYAnnouncementService(httpClient = get()) }
    single { RailwayGovLiveTrackerService(httpClient = get()) }
    single { SyrmosLinesService(httpClient = get()) }
    single { SyrmosSchedulesService(httpClient = get()) }
    single { SyrmosLivePositionsService(httpClient = get()) }
    single { SyrmosContactService(httpClient = get()) }
    single { SyrmosVisualOverridesService(httpClient = get()) }
    single { WeatherService(httpClient = get()) }
    single { AriadneChatService(httpClient = get()) }
    single { CommunityReportService(httpClient = get()) }
    single { OasaAirportBusService(httpClient = get()) }
}
