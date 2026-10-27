package com.syrmos.core.common

/**
 * Copy for [NearbyDepartureCoach] cases, in the four app languages.
 *
 * Owner tone: friendly, short, a bit playful. Never tell anyone to run: the
 * `hurry` case says "walk briskly". Three variants per case (four for
 * relaxNextOneIsClose), picked by [NearbyDepartureCoach.variantIndex] so the same
 * station reads the same all day and changes the next day. Placeholders:
 * `{destination}`, `{next}`, `{following}`, `{margin}`, `{wait}`.
 */
object NearbyCoachCopy {

    fun variantCount(case: CoachCase): Int = templates(case, AppLanguage.ENGLISH).size

    fun template(case: CoachCase, variant: Int, lang: AppLanguage): String {
        val list = templates(case, lang)
        return list[((variant % list.size) + list.size) % list.size]
    }

    fun render(case: CoachCase, variant: Int, lang: AppLanguage, params: Map<String, String>): String {
        var out = template(case, variant, lang)
        for ((k, v) in params) out = out.replace("{$k}", v)
        return out
    }

    /** Params for a [CoachResult] and a destination label. */
    fun params(result: CoachResult, destination: String): Map<String, String> = buildMap {
        put("destination", destination)
        result.nextMinutes?.let { put("next", it.toString()) }
        result.followingMinutes?.let { put("following", it.toString()) }
        result.marginMinutes?.let { put("margin", it.toString()) }
        result.waitMinutes?.let { put("wait", it.toString()) }
    }

    fun walkLabel(minutes: Int, lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "$minutes' με τα πόδια"
        AppLanguage.ALBANIAN -> "$minutes' në këmbë"
        AppLanguage.ITALIAN -> "$minutes' a piedi"
        else -> "$minutes' walk"
    }

    fun scheduledLabel(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "κατά πρόγραμμα"
        AppLanguage.ALBANIAN -> "sipas orarit"
        AppLanguage.ITALIAN -> "da orario"
        else -> "scheduled"
    }

    fun channelName(lang: AppLanguage): String = when (lang) {
        AppLanguage.GREEK -> "Επόμενα δρομολόγια κοντά σου"
        AppLanguage.ALBANIAN -> "Trenat e radhës pranë teje"
        AppLanguage.ITALIAN -> "Prossimi treni vicino a te"
        else -> "Next trains nearby"
    }

    private fun templates(case: CoachCase, lang: AppLanguage): List<String> = when (case) {
        CoachCase.RELAX_NEXT_ONE_IS_CLOSE -> when (lang) {
            AppLanguage.GREEK -> listOf(
                "Μπορεί το επόμενο για {destination} να έρχεται σε {next}', αλλά το μεθεπόμενο έρχεται σε {following}'. Μην αγχώνεσαι.",
                "Το επόμενο για {destination} φεύγει σε {next}'. Χαλαρά, το μεθεπόμενο είναι σε {following}'.",
                "Άσ' το να φύγει: για {destination} έρχεται άλλο σε {following}'. Περπάτα με την ησυχία σου.",
                "Σε {next}' φεύγει ένα για {destination}, σε {following}' το επόμενο. Θα περιμένεις μόνο {wait}'.",
            )
            AppLanguage.ALBANIAN -> listOf(
                "I radhës për {destination} vjen pas {next}', por tjetri vjen pas {following}'. Mos u streso.",
                "Një niset për {destination} pas {next}'. Qetë, tjetri është pas {following}'.",
                "Lëre të ikë: një tjetër për {destination} vjen pas {following}'. Merre me qetësi.",
                "Një për {destination} pas {next}', tjetri pas {following}'. Do të presësh vetëm {wait}'.",
            )
            AppLanguage.ITALIAN -> listOf(
                "Il prossimo per {destination} arriva tra {next}', ma quello dopo è tra {following}'. Niente stress.",
                "Uno parte per {destination} tra {next}'. Tranquillo, il successivo è tra {following}'.",
                "Lascialo andare: un altro per {destination} arriva tra {following}'. Fai con calma.",
                "Uno per {destination} tra {next}', il successivo tra {following}'. Aspetterai solo {wait}'.",
            )
            else -> listOf(
                "The next one to {destination} is in {next}', but the one after is in {following}'. No stress.",
                "One leaves for {destination} in {next}'. Relax, the next is in {following}'.",
                "Let that one go: another to {destination} comes in {following}'. Take your time.",
                "One to {destination} in {next}', the next in {following}'. You'll only wait {wait}'.",
            )
        }
        CoachCase.LONG_GAP_TAKE_IT_EASY -> when (lang) {
            AppLanguage.GREEK -> listOf(
                "Το τωρινό για {destination} δεν το προλαβαίνεις, το επόμενο σε {following}'. Ώρα για ένα freddo.",
                "Αυτό για {destination} σού ξεφεύγει. Το επόμενο σε {following}', πάρε τον χρόνο σου.",
                "Για {destination} το επόμενο που προλαβαίνεις είναι σε {following}'. Χαλαρά, χωρίς βιασύνη.",
            )
            AppLanguage.ALBANIAN -> listOf(
                "Këtë për {destination} nuk e kap dot; tjetri pas {following}'. Kohë për një kafe.",
                "Ky për {destination} të ikën. Tjetri pas {following}', merre shtruar.",
                "I radhës për {destination} që e kap është pas {following}'. Qetë, pa nxitim.",
            )
            AppLanguage.ITALIAN -> listOf(
                "Questo per {destination} lo perdi; il prossimo è tra {following}'. È l'ora di un caffè.",
                "Questo per {destination} ti sfugge. Il prossimo è tra {following}', prenditela comoda.",
                "Il primo per {destination} che riesci a prendere è tra {following}'. Con calma, senza fretta.",
            )
            else -> listOf(
                "You'll miss this one to {destination}; the next is in {following}'. Coffee time.",
                "This one to {destination} gets away. The next is in {following}', take your time.",
                "The next one to {destination} you can make is in {following}'. Easy, no rush.",
            )
        }
        CoachCase.HURRY -> when (lang) {
            AppLanguage.GREEK -> listOf(
                "Για {destination} σε {next}'. Με γρήγορο βήμα το προλαβαίνεις.",
                "Σε {next}' φεύγει για {destination}. Ανέβασε λίγο ρυθμό και είσαι εκεί.",
                "Για {destination} σε {next}'. Λίγο πιο γρήγορο βήμα και το έχεις.",
            )
            AppLanguage.ALBANIAN -> listOf(
                "Për {destination} pas {next}'. Me hap të shpejtë e kap.",
                "Pas {next}' niset për {destination}. Shpejto pak hapin dhe je aty.",
                "Për {destination} pas {next}'. Pak më shpejt me hapa dhe e ke.",
            )
            AppLanguage.ITALIAN -> listOf(
                "Per {destination} tra {next}'. A passo svelto ce la fai.",
                "Parte per {destination} tra {next}'. Allunga un po' il passo e ci sei.",
                "Per {destination} tra {next}'. Un passo un po' più deciso ed è tuo.",
            )
            else -> listOf(
                "{destination} in {next}'. Walk briskly and you'll make it.",
                "Leaves for {destination} in {next}'. Pick up the pace a little and you're there.",
                "{destination} in {next}'. A slightly quicker step and it's yours.",
            )
        }
        CoachCase.COMFORTABLE -> when (lang) {
            AppLanguage.GREEK -> listOf(
                "Για {destination} σε {next}'. Φτάνεις άνετα, με {margin}' περιθώριο.",
                "Άνετα: για {destination} σε {next}', σου μένουν {margin}'.",
                "Για {destination} σε {next}'. Περπάτα κανονικά, έχεις {margin}' να περισσεύουν.",
            )
            AppLanguage.ALBANIAN -> listOf(
                "Për {destination} pas {next}'. Arrin rehat, me {margin}' rezervë.",
                "Rehat: për {destination} pas {next}', të mbeten {margin}'.",
                "Për {destination} pas {next}'. Ec normalisht, ke {margin}' tepër.",
            )
            AppLanguage.ITALIAN -> listOf(
                "Per {destination} tra {next}'. Arrivi comodo, con {margin}' di margine.",
                "Tranquillo: per {destination} tra {next}', ti avanzano {margin}'.",
                "Per {destination} tra {next}'. Cammina normale, hai {margin}' di scorta.",
            )
            else -> listOf(
                "{destination} in {next}'. You'll get there with {margin}' to spare.",
                "Easy: {destination} in {next}', with {margin}' left over.",
                "{destination} in {next}'. Walk normally, you have {margin}' in hand.",
            )
        }
        CoachCase.LAST_TRAIN -> when (lang) {
            AppLanguage.GREEK -> listOf(
                "Προσοχή: το {next}' για {destination} είναι το τελευταίο για σήμερα.",
                "Το τελευταίο για {destination} φεύγει σε {next}'. Μην το αφήσεις.",
                "Τελευταίο για σήμερα προς {destination}, σε {next}'. Κράτα το.",
            )
            AppLanguage.ALBANIAN -> listOf(
                "Kujdes: ai pas {next}' për {destination} është i fundit për sot.",
                "I fundit për {destination} niset pas {next}'. Mos e lër të ikë.",
                "I fundit për sot drejt {destination}, pas {next}'. Mbaje parasysh.",
            )
            AppLanguage.ITALIAN -> listOf(
                "Attenzione: quello tra {next}' per {destination} è l'ultimo di oggi.",
                "L'ultimo per {destination} parte tra {next}'. Non fartelo scappare.",
                "Ultimo di oggi per {destination}, tra {next}'. Tienilo a mente.",
            )
            else -> listOf(
                "Heads up: the {next}' to {destination} is the last one today.",
                "The last one to {destination} leaves in {next}'. Don't let it go.",
                "Last one today to {destination}, in {next}'. Keep it in mind.",
            )
        }
        CoachCase.MISSED_LAST -> when (lang) {
            AppLanguage.GREEK -> listOf(
                "Το τελευταίο για {destination} έφυγε. Δες εναλλακτικές στο Syrmos.",
                "Για {destination} δεν υπάρχει άλλο σήμερα. Το Syrmos έχει εναλλακτικές.",
                "Τέλος τα δρομολόγια για {destination} για σήμερα. Άνοιξε το Syrmos για άλλη διαδρομή.",
            )
            AppLanguage.ALBANIAN -> listOf(
                "I fundit për {destination} ka ikur. Shiko alternativat në Syrmos.",
                "Për {destination} s'ka më sot. Syrmos ka alternativa.",
                "Shërbimi për {destination} mbaroi për sot. Hap Syrmos për një rrugë tjetër.",
            )
            AppLanguage.ITALIAN -> listOf(
                "L'ultimo per {destination} è partito. Guarda le alternative su Syrmos.",
                "Per {destination} non c'è più nulla oggi. Syrmos ha delle alternative.",
                "Il servizio per {destination} è finito per oggi. Apri Syrmos per un altro percorso.",
            )
            else -> listOf(
                "The last one to {destination} has gone. Check alternatives in Syrmos.",
                "Nothing more to {destination} today. Syrmos has alternatives.",
                "Service to {destination} is over for today. Open Syrmos for another route.",
            )
        }
        CoachCase.STATION_CLOSED -> when (lang) {
            AppLanguage.GREEK -> listOf(
                "Ο σταθμός είναι κλειστός τώρα. Δες τη διαδρομή αντικατάστασης.",
                "Ο σταθμός δεν λειτουργεί αυτή τη στιγμή. Το Syrmos σού δείχνει εναλλακτική.",
                "Κλειστός σταθμός για την ώρα. Δες στο Syrmos πώς να συνεχίσεις.",
            )
            AppLanguage.ALBANIAN -> listOf(
                "Stacioni është i mbyllur tani. Shiko rrugën zëvendësuese.",
                "Stacioni nuk funksionon për momentin. Syrmos të tregon një alternativë.",
                "Stacion i mbyllur për tani. Shiko në Syrmos si të vazhdosh.",
            )
            AppLanguage.ITALIAN -> listOf(
                "La stazione è chiusa in questo momento. Guarda il percorso sostitutivo.",
                "La stazione non è operativa al momento. Syrmos ti mostra un'alternativa.",
                "Stazione chiusa per ora. Guarda su Syrmos come proseguire.",
            )
            else -> listOf(
                "The station is closed right now. See the replacement route.",
                "The station isn't operating at the moment. Syrmos shows you an alternative.",
                "Station closed for now. See in Syrmos how to carry on.",
            )
        }
        CoachCase.NO_SERVICE -> when (lang) {
            AppLanguage.GREEK -> listOf(
                "Δεν υπάρχει δρομολόγιο για {destination} αυτή την ώρα.",
                "Για {destination} δεν φεύγει τίποτα αυτή τη στιγμή.",
                "Καμία αναχώρηση για {destination} τώρα. Δες εναλλακτικές στο Syrmos.",
            )
            AppLanguage.ALBANIAN -> listOf(
                "Nuk ka shërbim për {destination} në këtë orë.",
                "Për {destination} nuk niset asgjë për momentin.",
                "Asnjë nisje për {destination} tani. Shiko alternativat në Syrmos.",
            )
            AppLanguage.ITALIAN -> listOf(
                "Nessun servizio per {destination} in questo momento.",
                "Per {destination} non parte niente al momento.",
                "Nessuna partenza per {destination} ora. Guarda le alternative su Syrmos.",
            )
            else -> listOf(
                "No service to {destination} right now.",
                "Nothing leaves for {destination} at the moment.",
                "No departures to {destination} now. Check alternatives in Syrmos.",
            )
        }
    }
}

/** One (line, destination) direction at the station: next departures, ascending. */
data class NearbyDirection(
    val lineId: String,
    val destination: String,
    val departures: List<CoachDeparture>,
    val isLive: Boolean = false,
)

data class NearbyNotificationContent(
    val title: String,
    val lines: List<String>,
    val headline: String,
    val headlineCase: CoachCase,
)

/**
 * Builds the nearby next-train notification: title "Station · 6' walk", one line
 * per direction with its next two times, and the headline catchphrase from the
 * direction whose case ranks first. Pure, so it is unit tested.
 */
object NearbyCoachNotification {
    fun build(
        stationId: String,
        stationName: String,
        walkMinutes: Int,
        directions: List<NearbyDirection>,
        stationClosed: Boolean,
        athensDate: String,
        lang: AppLanguage,
    ): NearbyNotificationContent? {
        val title = "$stationName · ${NearbyCoachCopy.walkLabel(walkMinutes, lang)}"
        if (stationClosed) {
            val v = NearbyDepartureCoach.variantIndex(stationId, athensDate, NearbyCoachCopy.variantCount(CoachCase.STATION_CLOSED))
            return NearbyNotificationContent(
                title, emptyList(),
                NearbyCoachCopy.render(CoachCase.STATION_CLOSED, v, lang, emptyMap()), CoachCase.STATION_CLOSED,
            )
        }
        if (directions.isEmpty()) return null
        val results = directions.map { NearbyDepartureCoach.coachCase(walkMinutes, it.departures, false) }
        val index = NearbyDepartureCoach.headlineIndex(results.map { it.case }) ?: return null
        val case = results[index].case
        val variant = NearbyDepartureCoach.variantIndex(stationId, athensDate, NearbyCoachCopy.variantCount(case))
        val headline = NearbyCoachCopy.render(case, variant, lang, NearbyCoachCopy.params(results[index], directions[index].destination))
        val lines = directions.map { d ->
            val times = d.departures.take(2).joinToString(", ") { "${it.minutes}'" }
            val tag = if (d.isLive) "" else " (${NearbyCoachCopy.scheduledLabel(lang)})"
            "${d.lineId} ${d.destination}: ${times.ifEmpty { "-" }}$tag"
        }
        return NearbyNotificationContent(title, lines, headline, case)
    }
}
