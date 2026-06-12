package com.syrmos.core.data.sync

import com.syrmos.core.data.seed.ResourceReader
import com.syrmos.core.network.SyrmosSchedulesService
import com.syrmos.core.network.SyrmosSchedulesService.FareProduct
import com.syrmos.core.network.SyrmosSchedulesService.FaresPayload
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json

/**
 * Native OASA fare catalogue. Mirrors ScheduleSyncRepository / StationOffsets
 * shape: hydrate from a bundled snapshot for offline-first, then refresh()
 * from /api/fares to overlay newer rows. All failures silent.
 */
class FaresRepository(
    private val schedulesService: SyrmosSchedulesService,
    private val resourceReader: ResourceReader? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _products = MutableStateFlow<List<FareProduct>>(emptyList())
    val products: StateFlow<List<FareProduct>> = _products.asStateFlow()

    private val _updatedAt = MutableStateFlow("")
    val updatedAt: StateFlow<String> = _updatedAt.asStateFlow()

    suspend fun hydrateFromBundleIfNeeded() {
        if (_products.value.isNotEmpty()) return
        val reader = resourceReader ?: return
        val body = runCatching {
            reader.readText("files/seed/schedules-v2/fares.json")
        }.getOrNull() ?: return
        if (body.isBlank() || body == "{}") return
        val payload = runCatching {
            json.decodeFromString<FaresPayload>(body)
        }.getOrNull() ?: return
        if (payload.products.isNotEmpty()) {
            _products.value = payload.products
            _updatedAt.value = payload.updatedAt
        }
    }

    suspend fun refresh() {
        val payload = schedulesService.fetchFares().firstOrNull() ?: return
        if (payload.products.isNotEmpty()) {
            _products.value = payload.products
            _updatedAt.value = payload.updatedAt
        }
    }

    fun productsIn(section: String): List<FareProduct> =
        _products.value.filter { it.section == section }
}
