package com.syrmos.core.data.seed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * Guards the seed-ordering fix behind [DataSeeder.nestedStationOrder] and
 * [DataSeeder.resolveOrderedStationIds].
 *
 * routes.json lists only 21 of the 33 lines. Nine of the ten buses (VL1, DX1,
 * KP1, TL1, PU1/PU2, X3/2X, KB1) are absent from it, so before the fix their
 * station_line rows were missing and getStationsOnLine fell back to the
 * globally ordered stations.json: the map drew a backtracking zig-zag, and the
 * buses whose stops exist only in the nested lines.json (X3/2X) drew nothing.
 * The fix seeds each absent line's ordered station_line rows from its nested
 * lines.json stations. These tests pin that behaviour with inline fixtures
 * shaped exactly like the bundled seed (PSB is the single bus present in
 * routes.json, so it exercises the routes-wins, do-not-reseed path).
 */
class DataSeederOrderingTest {

    private val json = Json { ignoreUnknownKeys = true }

    // Mirrors schedules-v2/lines.json: PSB is in routes.json below (and its
    // nested order is deliberately REVERSED to prove routes wins); VL1/X3 are
    // absent buses that rely on the nested fallback; M1 is a non-bus in routes.
    private val linesJson = """
        {"version":1,"updatedAt":"t","lines":[
          {"id":"M1","name":"Line 1","nameEl":"Γραμμή 1","type":"metro","color":"#0f0",
           "terminalA":"a","terminalB":"b","stationCount":3,
           "stations":[
             {"id":"M1_A","name":"A","nameEl":"Α","lat":37.94,"lng":23.64},
             {"id":"M1_B","name":"B","nameEl":"Β","lat":37.95,"lng":23.65},
             {"id":"M1_C","name":"C","nameEl":"Γ","lat":37.96,"lng":23.66}]},
          {"id":"PSB","name":"Patras Bus","nameEl":"Λεωφορείο","type":"bus","color":"#00f",
           "terminalA":"a","terminalB":"b","stationCount":3,
           "stations":[
             {"id":"PA_KAT","name":"Kato","nameEl":"Κάτω","lat":38.20,"lng":21.75},
             {"id":"PA_MID","name":"Mid","nameEl":"Μέση","lat":38.17,"lng":21.65},
             {"id":"PA_KAST","name":"Kastelokampos","nameEl":"Καστελλόκαμπος","lat":38.15,"lng":21.56}]},
          {"id":"VL1","name":"Volos Bus","nameEl":"Λεωφορείο","type":"bus","color":"#00f",
           "terminalA":"a","terminalB":"b","stationCount":3,
           "stations":[
             {"id":"VL_VOL","name":"Volos","nameEl":"Βόλος","lat":39.3647,"lng":22.9367},
             {"id":"VL_MID","name":"Mid","nameEl":"Μέση","lat":39.35,"lng":22.80},
             {"id":"VL_VEL","name":"Velestino","nameEl":"Βελεστίνο","lat":39.38,"lng":22.75}]},
          {"id":"X3","name":"Airport Express","nameEl":"Εξπρές","type":"bus","color":"#00f",
           "terminalA":"a","terminalB":"b","stationCount":2,
           "stations":[
             {"id":"THS_AIR","name":"Airport","nameEl":"Αεροδρόμιο","lat":40.5197,"lng":22.9709},
             {"id":"SKG_CEN","name":"Center","nameEl":"Κέντρο","lat":40.6403,"lng":22.9444}]}
        ]}
    """.trimIndent()

    // Mirrors routes.json: M1 and PSB only. PSB's route order differs from its
    // nested order above so we can prove routes.json wins for lines it lists.
    private val routesJson = """
        [
          {"line_id":"M1","station_ids":["M1_A","M1_B","M1_C"]},
          {"line_id":"PSB","station_ids":["PA_KAST","PA_MID","PA_KAT"]}
        ]
    """.trimIndent()

    private fun lines(): List<SeedLine> =
        json.decodeFromString<SeedLinesPayload>(linesJson).lines

    private fun routesByLine(): Map<String, List<String>> =
        json.decodeFromString<List<SeedRoute>>(routesJson).associate { it.lineId to it.stationIds }

    private fun routeLineIds(): Set<String> = routesByLine().keys

    @Test
    fun busAbsentFromRoutesIsSeededFromItsNestedStationOrder() {
        val vl1 = lines().first { it.id == "VL1" }

        val order = DataSeeder.nestedStationOrder(vl1, routeLineIds())

        assertEquals(
            listOf("VL_VOL", "VL_MID", "VL_VEL"),
            order.map { it.id },
            "an absent bus must be seeded in its nested lines.json order",
        )
    }

    @Test
    fun busPresentInRoutesIsNotReseededFromNestedStations() {
        val psb = lines().first { it.id == "PSB" }

        // Empty => the nested loop inserts nothing, so the routes.json order is
        // the only station_line ordering and no stop is double-inserted.
        assertTrue(
            DataSeeder.nestedStationOrder(psb, routeLineIds()).isEmpty(),
            "a line already in routes.json must not be re-seeded from nested stations",
        )
    }

    @Test
    fun routesOrderWinsOverNestedOrderWhenBothExist() {
        val psb = lines().first { it.id == "PSB" }

        assertEquals(
            listOf("PA_KAST", "PA_MID", "PA_KAT"),
            DataSeeder.resolveOrderedStationIds(psb, routesByLine()),
            "routes.json ordering must win over the (reversed) nested ordering",
        )
    }

    @Test
    fun absentBusResolvesToItsNestedOrder() {
        val x3 = lines().first { it.id == "X3" }

        // X3's stops exist only in nested lines.json; without the fallback it
        // would resolve to nothing and draw no line.
        assertEquals(
            listOf("THS_AIR", "SKG_CEN"),
            DataSeeder.resolveOrderedStationIds(x3, routesByLine()),
            "a bus absent from routes.json must resolve via its nested stations",
        )
    }

    @Test
    fun everyBusResolvesToAtLeastTwoOrderedStops() {
        val routes = routesByLine()

        lines().filter { it.type == "bus" }.forEach { bus ->
            val ordered = DataSeeder.resolveOrderedStationIds(bus, routes)
            assertTrue(
                ordered.size >= 2,
                "bus ${bus.id} resolved to ${ordered.size} ordered stop(s); a route needs at least 2",
            )
            assertEquals(
                ordered.size,
                ordered.toSet().size,
                "bus ${bus.id} has a duplicate stop in its ordered sequence",
            )
        }
    }

    @Test
    fun nestedStopsForAbsentBusCarryFiniteCoordinates() {
        lines()
            .filter { it.type == "bus" }
            .flatMap { DataSeeder.nestedStationOrder(it, routeLineIds()) }
            .forEach { s ->
                assertTrue(
                    s.lat.isFinite() && s.lng.isFinite() && (s.lat != 0.0 || s.lng != 0.0),
                    "nested stop ${s.id} must carry real coordinates, not ${s.lat},${s.lng}",
                )
            }
    }

    @Test
    fun aBusWithFewerThanTwoStopsIsDetectable() {
        // Proves the >= 2 guard above has teeth: a degenerate bus is flagged.
        val degenerate = json.decodeFromString<SeedLinesPayload>(
            """
            {"lines":[
              {"id":"BAD","name":"Bad","nameEl":"Κακό","type":"bus","color":"#f00",
               "terminalA":"a","terminalB":"b","stationCount":1,
               "stations":[{"id":"ONLY","name":"Only","nameEl":"Μόνο","lat":38.0,"lng":23.0}]}
            ]}
            """.trimIndent(),
        ).lines.first()

        assertTrue(
            DataSeeder.resolveOrderedStationIds(degenerate, emptyMap()).size < 2,
            "a bus with a single nested stop must be detectable as too short to draw",
        )
    }
}
