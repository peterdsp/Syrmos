package com.syrmos.feature.stations

import com.syrmos.core.common.AppLanguage
import com.syrmos.core.domain.usecase.UpcomingDeparture
import com.syrmos.core.model.transit.Direction
import com.syrmos.core.model.transit.Line
import com.syrmos.core.model.transit.LineColor
import com.syrmos.core.model.transit.LineType
import kotlin.test.Test
import kotlin.test.assertEquals

class DepartureDirectionLabelTest {
    private val line1 = Line(
        id = "M1",
        name = "Line 1",
        nameEl = "Γραμμή 1",
        type = LineType.METRO,
        color = LineColor.GREEN,
        terminalA = "Piraeus",
        terminalB = "Kifissia",
        stationCount = 24,
    )

    @Test
    fun offlineOutboundDepartureUsesDestinationTerminal() {
        val departure = departure(direction = Direction.OUTBOUND)

        assertEquals(
            "Kifissia",
            departureDirectionLabel(departure, listOf(line1), AppLanguage.ENGLISH),
        )
    }

    @Test
    fun offlineInboundDepartureUsesOriginTerminal() {
        val departure = departure(direction = Direction.INBOUND)

        assertEquals(
            "Piraeus",
            departureDirectionLabel(departure, listOf(line1), AppLanguage.ENGLISH),
        )
    }

    @Test
    fun serverDirectionTakesPriority() {
        val departure = departure(
            direction = Direction.OUTBOUND,
            notes = "Operator destination",
        )

        assertEquals(
            "Operator destination",
            departureDirectionLabel(departure, listOf(line1), AppLanguage.ENGLISH),
        )
    }

    @Test
    fun airportBranchUsesLocalizedAirportDestination() {
        val departure = departure(
            direction = Direction.OUTBOUND,
            lineId = "M3_AIR",
            serviceType = "airport",
        )

        assertEquals(
            "Αεροδρόμιο",
            departureDirectionLabel(departure, emptyList(), AppLanguage.GREEK),
        )
    }

    private fun departure(
        direction: Direction,
        lineId: String = "M1",
        notes: String? = null,
        serviceType: String? = null,
    ) = UpcomingDeparture(
        time = "21:02",
        minutesAway = 2,
        direction = direction,
        lineId = lineId,
        notes = notes,
        serviceType = serviceType,
    )
}
