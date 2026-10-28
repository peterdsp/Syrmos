package com.syrmos.core.data.sync

import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.ClosureMode
import com.syrmos.core.common.StationClosures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Decodes the payload shapes in docs/3.0.2-CONTRACTS.md. */
class ClosuresPayloadParserTest {

    private val contractClosures = """
        {
          "schemaVersion": 1,
          "generatedAt": "2026-10-27T18:00:00Z",
          "closures": [
            {
              "id": "syn-parade-2026",
              "stationIds": ["M2_SYN", "M3_SYN"],
              "lineIds": [],
              "mode": "pass_through",
              "startsAt": "2026-10-28T06:00:00Z",
              "startsAtEpochSeconds": 1793167200,
              "endsAt": null,
              "endsAtEpochSeconds": null,
              "status": "scheduled",
              "reason": {"el": "Με εντολή της Αστυνομίας", "en": "By police order"},
              "replacementBus": {
                "label": {"el": "Έκτακτο λεωφορείο", "en": "Replacement bus"},
                "fromStop": {"el": "Σύνταγμα (Αμαλίας)", "en": "Syntagma (Amalias)"},
                "toStop": {"el": "Ευαγγελισμός", "en": "Evangelismos"},
                "headwayMinutes": 10,
                "stops": [{"name": {"el": "Σύνταγμα"}, "lat": 37.9755, "lon": 23.7356}]
              },
              "source": "admin"
            },
            {"id": "future-mode", "stationIds": ["M1_KIF"], "mode": "partial", "startsAtEpochSeconds": 1},
            {"id": "", "stationIds": ["M1_KIF"], "mode": "closed", "startsAtEpochSeconds": 1}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesContractExample_andSkipsBadRows() {
        val list = ClosuresPayloadParser.parseClosures(contractClosures)!!
        assertEquals(1, list.size)
        val c = list.single()
        assertEquals(ClosureMode.PASS_THROUGH, c.mode)
        assertEquals(1793167200L, c.startsAtEpochSeconds)
        assertNull(c.endsAtEpochSeconds)
        assertEquals("By police order", c.reason!!.text(AppLanguage.ITALIAN))
        assertEquals(10, c.replacementBus!!.headwayMinutes)
        assertEquals("Σύνταγμα", c.replacementBus!!.stops.single().name.text(AppLanguage.ENGLISH))
        val e = StationClosures.effect(list, "M3_SYN", "M3", 1793167200L)
        assertTrue(e.active && !e.canBoard && !e.canAlight)
    }

    @Test
    fun malformedBodyReturnsNull_emptyListIsValid() {
        assertNull(ClosuresPayloadParser.parseClosures("not json"))
        assertNull(ClosuresPayloadParser.parseClosures(""))
        assertEquals(emptyList(), ClosuresPayloadParser.parseClosures("""{"schemaVersion":1,"closures":[]}"""))
    }

    @Test
    fun parsesContractBroadcast() {
        val body = """
            {"schemaVersion": 1, "broadcasts": [{
              "id": "b_01J", "title": {"el": "28η Οκτωβρίου", "en": "28 October"},
              "body": {"el": "Από τις 08:00 ο σταθμός Σύνταγμα είναι κλειστός", "en": "..."},
              "audience": "all", "sentAt": "2026-10-27T18:00:00Z", "sentAtEpochSeconds": 1793124000,
              "expiresAt": "2026-10-28T22:00:00Z", "expiresAtEpochSeconds": 1793224800,
              "deepLink": "syrmos://station/M2_SYN", "closureId": "syn-parade-2026"}]}
        """.trimIndent()
        val b = ClosuresPayloadParser.parseBroadcasts(body)!!.single()
        assertEquals("b_01J", b.id)
        assertEquals("28 October", b.title.text(AppLanguage.ENGLISH))
        assertEquals(1793224800L, b.expiresAtEpochSeconds)
        assertEquals("syrmos://station/M2_SYN", b.deepLink)
    }

    @Test
    fun replacementBusWithoutHeadwayOrStops_omitsThoseParts() {
        val body = """
            {"schemaVersion":1,"closures":[{"id":"draft-bus","stationIds":["M3_EVA"],"mode":"closed",
             "startsAtEpochSeconds":1,"endsAtEpochSeconds":null,"status":"active",
             "replacementBus":{"label":{"en":"Replacement bus"},"fromStop":{},"toStop":{},"headwayMinutes":null,"stops":[]}}]}
        """.trimIndent()
        val bus = ClosuresPayloadParser.parseClosures(body)!!.single().replacementBus!!
        assertNull(bus.headwayMinutes)
        assertEquals("Replacement bus", com.syrmos.core.common.ClosureCopy.replacementBus(bus, AppLanguage.ENGLISH))
        assertEquals("Έκτακτο λεωφορείο", com.syrmos.core.common.ClosureCopy.replacementBus(bus.copy(label = com.syrmos.core.common.LocalizedText()), AppLanguage.GREEK))
    }
}
