package com.syrmos.core.model.transit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LineType {
    @SerialName("metro") METRO,
    @SerialName("tram") TRAM,
    @SerialName("suburban") SUBURBAN,

    /**
     * A rail-replacement or connecting bus run by the rail operator on a corridor
     * where the train is suspended (Larisa-Volos, Kiato-Patras, ...). It is the
     * rail line standing in for the rail service, not an OASA city bus. Always
     * labelled as a bus so it never implies a train.
     */
    @SerialName("bus") BUS,

    @SerialName("scenic") SCENIC,
}
