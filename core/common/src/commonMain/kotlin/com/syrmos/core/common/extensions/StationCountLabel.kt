package com.syrmos.core.common.extensions

import com.syrmos.core.common.AppLanguage
import com.syrmos.core.common.L

/**
 * "Browse all 394 stations" with the real count. The number used to be typed
 * into the string and read 389 while the list held 394. Before the stations
 * have loaded (count of zero) the label drops the number rather than
 * announcing "all 0 stations". Twin of `browseAllStationsLabel` in iOS
 * Localization.swift.
 */
fun browseAllStationsLabel(count: Int, lang: AppLanguage): String {
    val template = L.BROWSE_ALL_STATIONS.text(lang)
    return if (count > 0) template.replace("{n}", count.toString()) else template.replace("{n} ", "")
}
