package com.syrmos.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

class CommunityHistoryContractTest {
    @Test
    fun decodesDailyGoodAndIssueCounts() {
        val payload = """
            {
              "granularity":"day",
              "scopeId":null,
              "buckets":[{
                "period":"2026-08-06",
                "totalReports":3,
                "positiveReports":2,
                "issueReports":1,
                "counts":{"normal":1,"clean":1,"delayed":1}
              }],
              "updatedAt":"2026-08-06T03:00:00Z",
              "privacy":"Permanent anonymous aggregates only."
            }
        """.trimIndent()

        val history = Json { ignoreUnknownKeys = true }.decodeFromString<CommunityHistory>(payload)

        assertEquals("day", history.granularity)
        assertEquals(3, history.buckets.single().totalReports)
        assertEquals(2, history.buckets.single().positiveReports)
        assertEquals(1, history.buckets.single().counts["delayed"])
    }
}
