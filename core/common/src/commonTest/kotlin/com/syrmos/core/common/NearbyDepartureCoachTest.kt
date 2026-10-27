package com.syrmos.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Mirrors `fixtures/nearby/coach.json` case for case (shared with Swift
 * `NearbyDepartureCoach`). commonTest cannot read repo files, so the fixture is
 * inlined; regenerate it from the JSON whenever the generator changes.
 */
class NearbyDepartureCoachTest {

    @Test
    fun walkCases_matchFixture() {
        assertEquals(1, NearbyDepartureCoach.walkMinutes(0.0), "0 m")
        assertEquals(1, NearbyDepartureCoach.walkMinutes(30.0), "30 m")
        assertEquals(1, NearbyDepartureCoach.walkMinutes(61.0), "61 m")
        assertEquals(2, NearbyDepartureCoach.walkMinutes(62.0), "62 m")
        assertEquals(7, NearbyDepartureCoach.walkMinutes(400.0), "400 m")
        assertEquals(10, NearbyDepartureCoach.walkMinutes(615.0), "615 m")
        assertEquals(17, NearbyDepartureCoach.walkMinutes(1000.0), "1000 m")
        assertEquals(25, NearbyDepartureCoach.walkMinutes(1500.0), "1500 m")
    }

    private fun check(id: String, walk: Int, deps: List<CoachDeparture>, closed: Boolean, expected: CoachResult) {
        assertEquals(expected, NearbyDepartureCoach.coachCase(walk, deps, closed), id)
    }

    @Test
    fun coachCases_matchFixture() {
        check("owner_example_relax", 5, listOf(CoachDeparture(2, false), CoachDeparture(10, false)), false, CoachResult(CoachCase.fromKey("relaxNextOneIsClose")!!, nextMinutes = 2, followingMinutes = 10, waitMinutes = 5, marginMinutes = null, tight = null))
        check("relax_boundary_wait_8", 5, listOf(CoachDeparture(2, false), CoachDeparture(13, false)), false, CoachResult(CoachCase.fromKey("relaxNextOneIsClose")!!, nextMinutes = 2, followingMinutes = 13, waitMinutes = 8, marginMinutes = null, tight = null))
        check("long_gap_wait_9", 5, listOf(CoachDeparture(2, false), CoachDeparture(14, false)), false, CoachResult(CoachCase.fromKey("longGapTakeItEasy")!!, nextMinutes = 2, followingMinutes = 14, waitMinutes = 9, marginMinutes = null, tight = null))
        check("hurry_margin_0", 5, listOf(CoachDeparture(5, false), CoachDeparture(12, false)), false, CoachResult(CoachCase.fromKey("hurry")!!, nextMinutes = 5, followingMinutes = null, waitMinutes = null, marginMinutes = 0, tight = null))
        check("hurry_margin_2", 5, listOf(CoachDeparture(7, false), CoachDeparture(15, false)), false, CoachResult(CoachCase.fromKey("hurry")!!, nextMinutes = 7, followingMinutes = null, waitMinutes = null, marginMinutes = 2, tight = null))
        check("comfortable_margin_3", 5, listOf(CoachDeparture(8, false), CoachDeparture(15, false)), false, CoachResult(CoachCase.fromKey("comfortable")!!, nextMinutes = 8, followingMinutes = null, waitMinutes = null, marginMinutes = 3, tight = null))
        check("last_train_catchable_tight", 5, listOf(CoachDeparture(6, true)), false, CoachResult(CoachCase.fromKey("lastTrain")!!, nextMinutes = 6, followingMinutes = null, waitMinutes = null, marginMinutes = null, tight = true))
        check("last_train_catchable_relaxed", 5, listOf(CoachDeparture(12, true)), false, CoachResult(CoachCase.fromKey("lastTrain")!!, nextMinutes = 12, followingMinutes = null, waitMinutes = null, marginMinutes = null, tight = false))
        check("missed_first_next_is_last", 5, listOf(CoachDeparture(2, false), CoachDeparture(9, true)), false, CoachResult(CoachCase.fromKey("lastTrain")!!, nextMinutes = 9, followingMinutes = null, waitMinutes = null, marginMinutes = null, tight = false))
        check("missed_last_train", 5, listOf(CoachDeparture(3, true)), false, CoachResult(CoachCase.fromKey("missedLast")!!, nextMinutes = 3, followingMinutes = null, waitMinutes = null, marginMinutes = null, tight = null))
        check("missed_all_not_last", 5, listOf(CoachDeparture(1, false), CoachDeparture(3, false)), false, CoachResult(CoachCase.fromKey("noService")!!, nextMinutes = 1, followingMinutes = null, waitMinutes = null, marginMinutes = null, tight = null))
        check("no_departures", 5, listOf(), false, CoachResult(CoachCase.fromKey("noService")!!, nextMinutes = null, followingMinutes = null, waitMinutes = null, marginMinutes = null, tight = null))
        check("station_closed_wins", 5, listOf(CoachDeparture(8, false), CoachDeparture(15, false)), true, CoachResult(CoachCase.fromKey("stationClosed")!!, nextMinutes = null, followingMinutes = null, waitMinutes = null, marginMinutes = null, tight = null))
        check("departing_now_walk_1", 1, listOf(CoachDeparture(0, false), CoachDeparture(6, false)), false, CoachResult(CoachCase.fromKey("relaxNextOneIsClose")!!, nextMinutes = 0, followingMinutes = 6, waitMinutes = 5, marginMinutes = null, tight = null))
    }

    @Test
    fun headlineCases_matchFixture() {
        assertEquals(1, NearbyDepartureCoach.headlineIndex(listOf(CoachCase.fromKey("comfortable")!!, CoachCase.fromKey("relaxNextOneIsClose")!!)))
        assertEquals(0, NearbyDepartureCoach.headlineIndex(listOf(CoachCase.fromKey("hurry")!!, CoachCase.fromKey("longGapTakeItEasy")!!, CoachCase.fromKey("comfortable")!!)))
        assertEquals(1, NearbyDepartureCoach.headlineIndex(listOf(CoachCase.fromKey("comfortable")!!, CoachCase.fromKey("lastTrain")!!)))
        assertEquals(1, NearbyDepartureCoach.headlineIndex(listOf(CoachCase.fromKey("noService")!!, CoachCase.fromKey("comfortable")!!)))
        assertEquals(0, NearbyDepartureCoach.headlineIndex(listOf(CoachCase.fromKey("comfortable")!!, CoachCase.fromKey("comfortable")!!)))
        assertEquals(null, NearbyDepartureCoach.headlineIndex(listOf()))
    }

    @Test
    fun variantCases_matchFixture() {
        assertEquals(40465884L, NearbyDepartureCoach.fnv1a32("M3_NIK|2026-10-27"))
        assertEquals(0, NearbyDepartureCoach.variantIndex("M3_NIK", "2026-10-27", 3))
        assertEquals(157909217L, NearbyDepartureCoach.fnv1a32("M3_NIK|2026-10-28"))
        assertEquals(2, NearbyDepartureCoach.variantIndex("M3_NIK", "2026-10-28", 3))
        assertEquals(3267926017L, NearbyDepartureCoach.fnv1a32("M2_SYN|2026-10-27"))
        assertEquals(1, NearbyDepartureCoach.variantIndex("M2_SYN", "2026-10-27", 3))
        assertEquals(2072798215L, NearbyDepartureCoach.fnv1a32("T6_SYN|2026-12-31"))
        assertEquals(3, NearbyDepartureCoach.variantIndex("T6_SYN", "2026-12-31", 4))
        assertEquals(914645290L, NearbyDepartureCoach.fnv1a32("Νίκαια|2026-10-27"))
        assertEquals(1, NearbyDepartureCoach.variantIndex("Νίκαια", "2026-10-27", 3))
    }

    @Test
    fun guardCases_matchFixture() {
        assertEquals("allow", NearbyDepartureCoach.guard(CoachGuardInput(enabled = true, goJourneyActive = false, athensHour = 12, speedKmh = 3.0, lastNotifiedForStationEpochSeconds = null, notificationsToday = 0, now = 1793167200L)).key, "allow_default")
        assertEquals("disabled", NearbyDepartureCoach.guard(CoachGuardInput(enabled = false, goJourneyActive = true, athensHour = 2, speedKmh = 3.0, lastNotifiedForStationEpochSeconds = null, notificationsToday = 0, now = 1793167200L)).key, "disabled_first")
        assertEquals("goActive", NearbyDepartureCoach.guard(CoachGuardInput(enabled = true, goJourneyActive = true, athensHour = 12, speedKmh = 3.0, lastNotifiedForStationEpochSeconds = null, notificationsToday = 0, now = 1793167200L)).key, "go_active")
        assertEquals("quietHours", NearbyDepartureCoach.guard(CoachGuardInput(enabled = true, goJourneyActive = false, athensHour = 23, speedKmh = 3.0, lastNotifiedForStationEpochSeconds = null, notificationsToday = 0, now = 1793167200L)).key, "quiet_23")
        assertEquals("quietHours", NearbyDepartureCoach.guard(CoachGuardInput(enabled = true, goJourneyActive = false, athensHour = 6, speedKmh = 3.0, lastNotifiedForStationEpochSeconds = null, notificationsToday = 0, now = 1793167200L)).key, "quiet_6")
        assertEquals("allow", NearbyDepartureCoach.guard(CoachGuardInput(enabled = true, goJourneyActive = false, athensHour = 7, speedKmh = 3.0, lastNotifiedForStationEpochSeconds = null, notificationsToday = 0, now = 1793167200L)).key, "hour_7_allowed")
        assertEquals("moving", NearbyDepartureCoach.guard(CoachGuardInput(enabled = true, goJourneyActive = false, athensHour = 12, speedKmh = 40.0, lastNotifiedForStationEpochSeconds = null, notificationsToday = 0, now = 1793167200L)).key, "moving_fast")
        assertEquals("allow", NearbyDepartureCoach.guard(CoachGuardInput(enabled = true, goJourneyActive = false, athensHour = 12, speedKmh = 25.0, lastNotifiedForStationEpochSeconds = null, notificationsToday = 0, now = 1793167200L)).key, "speed_25_allowed")
        assertEquals("allow", NearbyDepartureCoach.guard(CoachGuardInput(enabled = true, goJourneyActive = false, athensHour = 12, speedKmh = null, lastNotifiedForStationEpochSeconds = null, notificationsToday = 0, now = 1793167200L)).key, "speed_unknown_allowed")
        assertEquals("cooldown", NearbyDepartureCoach.guard(CoachGuardInput(enabled = true, goJourneyActive = false, athensHour = 12, speedKmh = 3.0, lastNotifiedForStationEpochSeconds = 1793164560L, notificationsToday = 0, now = 1793167200L)).key, "cooldown_44min")
        assertEquals("allow", NearbyDepartureCoach.guard(CoachGuardInput(enabled = true, goJourneyActive = false, athensHour = 12, speedKmh = 3.0, lastNotifiedForStationEpochSeconds = 1793164500L, notificationsToday = 0, now = 1793167200L)).key, "cooldown_45min_allowed")
        assertEquals("dailyCap", NearbyDepartureCoach.guard(CoachGuardInput(enabled = true, goJourneyActive = false, athensHour = 12, speedKmh = 3.0, lastNotifiedForStationEpochSeconds = null, notificationsToday = 4, now = 1793167200L)).key, "daily_cap")
    }

    @Test
    fun copy_hasEveryVariantInEveryLanguage_andNeverSaysRun() {
        val runWords = listOf("run", "τρέξ", "τρεξ", "τρέχ", "τρεχ", "vrap", "corri", "correre")
        for (case in CoachCase.entries) {
            val count = NearbyCoachCopy.variantCount(case)
            assertEquals(if (case == CoachCase.RELAX_NEXT_ONE_IS_CLOSE) 4 else 3, count, case.key)
            val enPlaceholders = (0 until count).map { Regex("\\{[a-z]+\\}").findAll(NearbyCoachCopy.template(case, it, AppLanguage.ENGLISH)).map { m -> m.value }.toSet() }
            for (lang in AppLanguage.entries) {
                for (v in 0 until count) {
                    val t = NearbyCoachCopy.template(case, v, lang)
                    assertTrue(t.isNotBlank())
                    val lower = t.lowercase()
                    runWords.forEach { w -> assertFalse(lower.contains(w), "$lang ${case.key} $v says '$w'") }
                    val ph = Regex("\\{[a-z]+\\}").findAll(t).map { m -> m.value }.toSet()
                    assertEquals(enPlaceholders[v], ph, "$lang ${case.key} $v placeholders")
                }
            }
        }
    }

    @Test
    fun copy_rendersOwnerExample() {
        val r = NearbyDepartureCoach.coachCase(5, listOf(CoachDeparture(2), CoachDeparture(10)), false)
        val text = NearbyCoachCopy.render(r.case, 0, AppLanguage.GREEK, NearbyCoachCopy.params(r, "Δουκίσσης Πλακεντίας"))
        assertEquals("Μπορεί το επόμενο για Δουκίσσης Πλακεντίας να έρχεται σε 2', αλλά το μεθεπόμενο έρχεται σε 10'. Μην αγχώνεσαι.", text)
    }
}
