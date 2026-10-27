package com.syrmos.core.common

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Syrmos 3.0.2 user-facing copy for closures, broadcasts, calendar leave-by and
 * their settings, in the four app languages (same inline pattern as the rest of
 * the KMP UI). Kept in core/common so the Compose UI, the Android notification
 * code and the tests read one source.
 */
object ClosureCopy {
    private val athens = TimeZone.of("Europe/Athens")

    fun clock(epochSeconds: Long): String {
        val t = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(athens)
        return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    }

    /** "08:00" today, or "28/10 08:00" when the instant falls on another Athens day than [now]. */
    fun clockWithDay(epochSeconds: Long, now: Long): String {
        val t = Instant.fromEpochSeconds(epochSeconds).toLocalDateTime(athens)
        val today = Instant.fromEpochSeconds(now).toLocalDateTime(athens).date
        return if (t.date == today) clock(epochSeconds)
        else "${t.dayOfMonth.toString().padStart(2, '0')}/${t.monthNumber.toString().padStart(2, '0')} ${clock(epochSeconds)}"
    }

    /** Short chip label for a mode: kept to one or two words so the chip stays atomic. */
    fun modeChip(mode: ClosureMode, lang: AppLanguage): String = when (mode) {
        ClosureMode.CLOSED -> when (lang) {
            AppLanguage.GREEK -> "Κλειστός"
            AppLanguage.ALBANIAN -> "Mbyllur"
            AppLanguage.ITALIAN -> "Chiusa"
            else -> "Closed"
        }
        ClosureMode.PASS_THROUGH -> when (lang) {
            AppLanguage.GREEK -> "Χωρίς στάση"
            AppLanguage.ALBANIAN -> "Pa ndalesë"
            AppLanguage.ITALIAN -> "Senza fermata"
            else -> "No stop"
        }
        ClosureMode.EXIT_ONLY -> when (lang) {
            AppLanguage.GREEK -> "Μόνο έξοδος"
            AppLanguage.ALBANIAN -> "Vetëm dalje"
            AppLanguage.ITALIAN -> "Solo uscita"
            else -> "Exit only"
        }
        ClosureMode.ENTRY_ONLY -> when (lang) {
            AppLanguage.GREEK -> "Μόνο είσοδος"
            AppLanguage.ALBANIAN -> "Vetëm hyrje"
            AppLanguage.ITALIAN -> "Solo ingresso"
            else -> "Entry only"
        }
    }

    fun modeHeadline(mode: ClosureMode, lang: AppLanguage): String = when (mode) {
        ClosureMode.CLOSED -> when (lang) {
            AppLanguage.GREEK -> "Ο σταθμός είναι κλειστός"
            AppLanguage.ALBANIAN -> "Stacioni është i mbyllur"
            AppLanguage.ITALIAN -> "La stazione è chiusa"
            else -> "The station is closed"
        }
        ClosureMode.PASS_THROUGH -> when (lang) {
            AppLanguage.GREEK -> "Οι συρμοί περνούν χωρίς στάση"
            AppLanguage.ALBANIAN -> "Trenat kalojnë pa ndalur"
            AppLanguage.ITALIAN -> "I treni transitano senza fermarsi"
            else -> "Trains pass without stopping"
        }
        ClosureMode.EXIT_ONLY -> when (lang) {
            AppLanguage.GREEK -> "Μόνο αποβίβαση, όχι επιβίβαση"
            AppLanguage.ALBANIAN -> "Vetëm zbritje, jo hipje"
            AppLanguage.ITALIAN -> "Solo discesa, niente salita"
            else -> "Exit only, no boarding"
        }
        ClosureMode.ENTRY_ONLY -> when (lang) {
            AppLanguage.GREEK -> "Μόνο επιβίβαση, όχι αποβίβαση"
            AppLanguage.ALBANIAN -> "Vetëm hipje, jo zbritje"
            AppLanguage.ITALIAN -> "Solo salita, niente discesa"
            else -> "Entry only, no exit"
        }
    }

    /** "08:00 to 16:00" or "from 08:00 until further notice". */
    fun window(startsAt: Long, endsAt: Long?, now: Long, lang: AppLanguage): String {
        val from = clockWithDay(startsAt, now)
        if (endsAt == null) return when (lang) {
            AppLanguage.GREEK -> "Από $from έως νεωτέρας"
            AppLanguage.ALBANIAN -> "Nga $from deri në një njoftim të ri"
            AppLanguage.ITALIAN -> "Dalle $from fino a nuovo avviso"
            else -> "From $from until further notice"
        }
        val to = clockWithDay(endsAt, now)
        return when (lang) {
            AppLanguage.GREEK -> "$from έως $to"
            AppLanguage.ALBANIAN -> "$from deri $to"
            AppLanguage.ITALIAN -> "Dalle $from alle $to"
            else -> "$from to $to"
        }
    }

    /** One line under "Next departures" when a closure hides every departure. */
    fun noDeparturesClosed(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Καμία αναχώρηση λόγω κλεισίματος"
        AppLanguage.ALBANIAN -> "Asnjë nisje për shkak të mbylljes"
        AppLanguage.ITALIAN -> "Nessuna partenza a causa della chiusura"
        else -> "No departures because of the closure"
    }

    fun untilFurtherNotice(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Έως νεωτέρας"
        AppLanguage.ALBANIAN -> "Deri në një njoftim të ri"
        AppLanguage.ITALIAN -> "Fino a nuovo avviso"
        else -> "Until further notice"
    }

    fun upcoming(startsAt: Long, now: Long, lang: AppLanguage): String {
        val at = clockWithDay(startsAt, now)
        return when (lang) {
            AppLanguage.GREEK -> "Κλείνει στις $at"
            AppLanguage.ALBANIAN -> "Mbyllet në $at"
            AppLanguage.ITALIAN -> "Chiude alle $at"
            else -> "Closes at $at"
        }
    }

    /** "Replacement bus every 10', from Syntagma (Amalias) to Evangelismos". */
    fun replacementBus(bus: ReplacementBus, lang: AppLanguage): String {
        val label = bus.label.text(lang).ifBlank {
            when (lang) {
                AppLanguage.GREEK -> "Έκτακτο λεωφορείο"
                AppLanguage.ALBANIAN -> "Autobus zëvendësues"
                AppLanguage.ITALIAN -> "Bus sostitutivo"
                else -> "Replacement bus"
            }
        }
        val every = bus.headwayMinutes?.let {
            when (lang) {
                AppLanguage.GREEK -> " κάθε $it'"
                AppLanguage.ALBANIAN -> " çdo $it'"
                AppLanguage.ITALIAN -> " ogni $it'"
                else -> " every $it'"
            }
        }.orEmpty()
        val from = bus.fromStop?.text(lang)?.takeIf { it.isNotBlank() }
        val to = bus.toStop?.text(lang)?.takeIf { it.isNotBlank() }
        val route = when {
            from != null && to != null -> when (lang) {
                AppLanguage.GREEK -> ", από $from προς $to"
                AppLanguage.ALBANIAN -> ", nga $from drejt $to"
                AppLanguage.ITALIAN -> ", da $from verso $to"
                else -> ", from $from to $to"
            }
            from != null -> when (lang) {
                AppLanguage.GREEK -> ", από $from"
                AppLanguage.ALBANIAN -> ", nga $from"
                AppLanguage.ITALIAN -> ", da $from"
                else -> ", from $from"
            }
            else -> ""
        }
        return "$label$every$route"
    }

    fun goWarning(stationName: String, lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Η διαδρομή σου περνά από τον σταθμό $stationName, που είναι κλειστός τώρα."
        AppLanguage.ALBANIAN -> "Rruga jote kalon nga stacioni $stationName, që tani është i mbyllur."
        AppLanguage.ITALIAN -> "Il tuo percorso passa dalla stazione $stationName, ora chiusa."
        else -> "Your journey goes through $stationName, which is closed right now."
    }

    fun assistantStatus(stationName: String, mode: ClosureMode, window: String, reason: String, lang: AppLanguage): String {
        val head = "$stationName: ${modeHeadline(mode, lang)}. $window."
        return if (reason.isBlank()) head else "$head $reason."
    }
}

object BroadcastCopy {
    fun channelName(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Ανακοινώσεις από το Syrmos"
        AppLanguage.ALBANIAN -> "Njoftime nga Syrmos"
        AppLanguage.ITALIAN -> "Annunci da Syrmos"
        else -> "Announcements from Syrmos"
    }

    fun inboxTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Ανακοινώσεις"
        AppLanguage.ALBANIAN -> "Njoftime"
        AppLanguage.ITALIAN -> "Annunci"
        else -> "Announcements"
    }
}

object CalendarLeaveByCopy {
    fun channelName(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Υπενθυμίσεις ημερολογίου"
        AppLanguage.ALBANIAN -> "Kujtues nga kalendari"
        AppLanguage.ITALIAN -> "Promemoria dal calendario"
        else -> "Calendar reminders"
    }

    fun title(kind: CalendarNotificationKind, lang: AppLanguage): String = when (kind) {
        CalendarNotificationKind.FLIGHT_CHECKLIST -> when (lang) {
            AppLanguage.GREEK -> "Έτοιμος για την πτήση;"
            AppLanguage.ALBANIAN -> "Gati për fluturimin?"
            AppLanguage.ITALIAN -> "Pronto per il volo?"
            else -> "Ready for your flight?"
        }
        CalendarNotificationKind.HEADS_UP -> when (lang) {
            AppLanguage.GREEK -> "Σε 15' ξεκινάς"
            AppLanguage.ALBANIAN -> "Nisesh pas 15'"
            AppLanguage.ITALIAN -> "Si parte tra 15'"
            else -> "You leave in 15'"
        }
        CalendarNotificationKind.LEAVE_NOW -> when (lang) {
            AppLanguage.GREEK -> "Ώρα να φύγεις"
            AppLanguage.ALBANIAN -> "Koha të nisesh"
            AppLanguage.ITALIAN -> "È ora di andare"
            else -> "Time to go"
        }
    }

    /**
     * Body for a scheduled notification. Params: `{title}` event title, `{line}`
     * first ride line, `{station}` boarding station, `{next}` minutes until that
     * departure at fire time, `{buffer}` arrival buffer minutes, `{fare}` an optional
     * " (9€)" suffix from the seed fares (empty when unknown).
     */
    fun body(kind: CalendarNotificationKind, event: CalendarEventKind, lang: AppLanguage, params: Map<String, String>): String {
        var out = template(kind, event, lang)
        for ((k, v) in params) out = out.replace("{$k}", v)
        return out
    }

    fun template(kind: CalendarNotificationKind, event: CalendarEventKind, lang: AppLanguage): String = when (kind) {
        CalendarNotificationKind.FLIGHT_CHECKLIST -> when (lang) {
            AppLanguage.GREEK -> "Πτήση σε λίγες ώρες. Έλεγξε διαβατήριο ή ταυτότητα και εισιτήριο μετρό αεροδρομίου{fare}."
            AppLanguage.ALBANIAN -> "Fluturim pas pak orësh. Kontrollo pasaportën ose ID-në dhe biletën e metrosë për aeroportin{fare}."
            AppLanguage.ITALIAN -> "Volo tra qualche ora. Controlla passaporto o carta d'identità e il biglietto della metro per l'aeroporto{fare}."
            else -> "Flight in a few hours. Check your passport or ID and the airport metro ticket{fare}."
        }
        CalendarNotificationKind.HEADS_UP -> if (event == CalendarEventKind.FLIGHT) when (lang) {
            AppLanguage.GREEK -> "Σε 15' ξεκίνα για το αεροδρόμιο. {line} από {station}, φτάνεις {buffer}' πριν την πτήση."
            AppLanguage.ALBANIAN -> "Pas 15' nisu për në aeroport. {line} nga {station}, arrin {buffer}' para fluturimit."
            AppLanguage.ITALIAN -> "Tra 15' parti per l'aeroporto. {line} da {station}, arrivi {buffer}' prima del volo."
            else -> "In 15' head to the airport. {line} from {station}, you'll be there {buffer}' before your flight."
        } else when (lang) {
            AppLanguage.GREEK -> "Σε 15' ξεκίνα για «{title}». {line} από {station}, φτάνεις με {buffer}' περιθώριο."
            AppLanguage.ALBANIAN -> "Pas 15' nisu për \"{title}\". {line} nga {station}, arrin me {buffer}' rezervë."
            AppLanguage.ITALIAN -> "Tra 15' parti per \"{title}\". {line} da {station}, arrivi con {buffer}' di margine."
            else -> "In 15' set off for \"{title}\". {line} from {station}, you'll arrive with {buffer}' to spare."
        }
        CalendarNotificationKind.LEAVE_NOW -> when (event) {
            CalendarEventKind.FLIGHT -> when (lang) {
                AppLanguage.GREEK -> "Ώρα να φύγεις! Η πτήση δεν περιμένει, το {line} όμως έρχεται σε {next}'."
                AppLanguage.ALBANIAN -> "Koha të nisesh! Fluturimi nuk pret, por {line} vjen pas {next}'."
                AppLanguage.ITALIAN -> "È ora di andare! Il volo non aspetta, ma la linea {line} passa tra {next}'."
                else -> "Time to go! Your flight won't wait, but the {line} comes in {next}'."
            }
            CalendarEventKind.LONG_DISTANCE -> when (lang) {
                AppLanguage.GREEK -> "Ώρα να φύγεις! Το πλοίο ή το τρένο δεν περιμένει, το {line} από {station} έρχεται σε {next}'."
                AppLanguage.ALBANIAN -> "Koha të nisesh! Trageti ose treni nuk pret, {line} nga {station} vjen pas {next}'."
                AppLanguage.ITALIAN -> "È ora di andare! Traghetto o treno non aspettano, la linea {line} da {station} passa tra {next}'."
                else -> "Time to go! The ferry or train won't wait, the {line} from {station} comes in {next}'."
            }
            CalendarEventKind.VENUE -> when (lang) {
                AppLanguage.GREEK -> "Ώρα να φύγεις για «{title}»! Το {line} έρχεται σε {next}', να πιάσεις καλή θέση."
                AppLanguage.ALBANIAN -> "Koha të nisesh për \"{title}\"! {line} vjen pas {next}', zër një vend të mirë."
                AppLanguage.ITALIAN -> "È ora di andare a \"{title}\"! La linea {line} passa tra {next}', così prendi un buon posto."
                else -> "Time to go to \"{title}\"! The {line} comes in {next}', grab a good spot."
            }
            CalendarEventKind.APPOINTMENT -> when (lang) {
                AppLanguage.GREEK -> "Ώρα να φύγεις για «{title}». Το {line} από {station} έρχεται σε {next}', θα φτάσεις στην ώρα σου."
                AppLanguage.ALBANIAN -> "Koha të nisesh për \"{title}\". {line} nga {station} vjen pas {next}', do arrish në kohë."
                AppLanguage.ITALIAN -> "È ora di andare a \"{title}\". La linea {line} da {station} passa tra {next}', arriverai puntuale."
                else -> "Time to go to \"{title}\". The {line} from {station} comes in {next}', you'll be on time."
            }
            CalendarEventKind.DEFAULT -> when (lang) {
                AppLanguage.GREEK -> "Ώρα να φύγεις για «{title}». Το {line} από {station} έρχεται σε {next}'."
                AppLanguage.ALBANIAN -> "Koha të nisesh për \"{title}\". {line} nga {station} vjen pas {next}'."
                AppLanguage.ITALIAN -> "È ora di andare a \"{title}\". La linea {line} da {station} passa tra {next}'."
                else -> "Time to go to \"{title}\". The {line} from {station} comes in {next}'."
            }
        }
    }

    fun late(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Λίγο αργά πια, αλλά αν φύγεις τώρα φτάνεις όσο πιο νωρίς γίνεται."
        AppLanguage.ALBANIAN -> "Pak vonë tani, por po u nise tani arrin sa më shpejt."
        AppLanguage.ITALIAN -> "Un po' tardi ormai, ma se parti ora arrivi il prima possibile."
        else -> "A bit late now, but if you leave now you'll get there as soon as possible."
    }
}

object ProactiveSettingsCopy {
    fun broadcasts(lang: AppLanguage) = BroadcastCopy.channelName(lang)

    fun nearbyTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Επόμενο δρομολόγιο κοντά σε σταθμό"
        AppLanguage.ALBANIAN -> "Treni i radhës pranë një stacioni"
        AppLanguage.ITALIAN -> "Prossimo treno vicino a una stazione"
        else -> "Next train near a station"
    }

    fun nearbyExplain(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Όταν βρίσκεσαι κοντά σε σταθμό, σου λέμε πότε περνά το επόμενο. Χρειάζεται τοποθεσία «Πάντα». Η τοποθεσία μένει στη συσκευή σου και δεν στέλνεται πουθενά."
        AppLanguage.ALBANIAN -> "Kur je pranë një stacioni, të tregojmë kur vjen treni i radhës. Kërkon vendndodhjen \"Gjithmonë\". Vendndodhja mbetet në pajisjen tënde dhe nuk dërgohet askund."
        AppLanguage.ITALIAN -> "Quando sei vicino a una stazione, ti diciamo quando passa il prossimo treno. Serve la posizione \"Sempre\". La posizione resta sul dispositivo e non viene inviata da nessuna parte."
        else -> "When you are near a station, we tell you when the next train comes. Needs \"Allow all the time\" location. Your location stays on your device and is never sent anywhere."
    }

    fun backgroundLocationStep(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Στην επόμενη οθόνη επίλεξε «Να επιτρέπεται πάντα» για την τοποθεσία."
        AppLanguage.ALBANIAN -> "Në ekranin tjetër zgjidh \"Lejo gjithmonë\" për vendndodhjen."
        AppLanguage.ITALIAN -> "Nella prossima schermata scegli \"Consenti sempre\" per la posizione."
        else -> "On the next screen, choose \"Allow all the time\" for location."
    }

    fun calendarTitle(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Υπενθυμίσεις ημερολογίου"
        AppLanguage.ALBANIAN -> "Kujtues nga kalendari"
        AppLanguage.ITALIAN -> "Promemoria dal calendario"
        else -> "Calendar reminders"
    }

    fun calendarExplain(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Για εκδηλώσεις με τοποθεσία τις επόμενες 48 ώρες, σου λέμε πότε να ξεκινήσεις. Το ημερολόγιο διαβάζεται μόνο στη συσκευή σου."
        AppLanguage.ALBANIAN -> "Për ngjarjet me vendndodhje në 48 orët e ardhshme, të themi kur të nisesh. Kalendari lexohet vetëm në pajisjen tënde."
        AppLanguage.ITALIAN -> "Per gli eventi con un luogo nelle prossime 48 ore, ti diciamo quando partire. Il calendario viene letto solo sul tuo dispositivo."
        else -> "For events with a location in the next 48 hours, we tell you when to set off. Your calendar is read on your device only."
    }

    fun kind(kind: CalendarEventKind, lang: AppLanguage): String = when (kind) {
        CalendarEventKind.FLIGHT -> when (lang) {
            AppLanguage.GREEK -> "Πτήσεις"
            AppLanguage.ALBANIAN -> "Fluturime"
            AppLanguage.ITALIAN -> "Voli"
            else -> "Flights"
        }
        CalendarEventKind.LONG_DISTANCE -> when (lang) {
            AppLanguage.GREEK -> "Πλοία και τρένα"
            AppLanguage.ALBANIAN -> "Trageta dhe trena"
            AppLanguage.ITALIAN -> "Traghetti e treni"
            else -> "Ferries and trains"
        }
        CalendarEventKind.VENUE -> when (lang) {
            AppLanguage.GREEK -> "Συναυλίες, γήπεδα, θέατρα"
            AppLanguage.ALBANIAN -> "Koncerte, stadiume, teatro"
            AppLanguage.ITALIAN -> "Concerti, stadi, teatri"
            else -> "Concerts, stadiums, theatres"
        }
        CalendarEventKind.APPOINTMENT -> when (lang) {
            AppLanguage.GREEK -> "Ραντεβού"
            AppLanguage.ALBANIAN -> "Takime"
            AppLanguage.ITALIAN -> "Appuntamenti"
            else -> "Appointments"
        }
        CalendarEventKind.DEFAULT -> when (lang) {
            AppLanguage.GREEK -> "Όλες οι άλλες εκδηλώσεις"
            AppLanguage.ALBANIAN -> "Të gjitha ngjarjet e tjera"
            AppLanguage.ITALIAN -> "Tutti gli altri eventi"
            else -> "All other events"
        }
    }

    fun flightBuffer(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Άφιξη στο αεροδρόμιο πριν την πτήση"
        AppLanguage.ALBANIAN -> "Mbërritja në aeroport para fluturimit"
        AppLanguage.ITALIAN -> "Arrivo in aeroporto prima del volo"
        else -> "Be at the airport before the flight"
    }
}
