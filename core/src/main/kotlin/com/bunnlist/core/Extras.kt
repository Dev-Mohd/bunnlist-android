package com.bunnlist.core

import kotlinx.serialization.Serializable

/** «تحدي الأسبوع». */
@Serializable
data class ChallengeState(val challenge: Challenge? = null, val participants: Int = 0, val finished: Int = 0, val me: Mine? = null) {
    @Serializable data class Mine(val progress: Int = 0, val completed: Boolean = false, val justCompleted: Boolean = false)
    @Serializable data class Filters(val originCode: String? = null, val process: String? = null, val roasterSlug: String? = null, val flavorFamily: String? = null) {
        val isEmpty: Boolean get() = originCode == null && process == null && roasterSlug == null && flavorFamily == null
        fun browse() = BrowseFilters(origins = setOfNotNull(originCode), processes = setOfNotNull(process), roasters = setOfNotNull(roasterSlug), flavors = setOfNotNull(flavorFamily))
    }
    @Serializable data class Challenge(val id: String, val titleAr: String, val subtitleAr: String? = null, val emoji: String? = null, val action: String = "brew",
                                       val filters: Filters = Filters(), val target: Int = 1, val points: Int = 0, val startsAt: String = "", val endsAt: String = "") {
        val actionAr: String get() = when (action) { "brew" -> "حضّر"; "rate" -> "قيّم"; else -> "أضف لمجموعتك" }
        fun timeLeftAr(nowMillis: Long = System.currentTimeMillis()): String {
            val end = runCatching { java.time.Instant.parse(endsAt).toEpochMilli() }.getOrNull() ?: return ""
            val s = (end - nowMillis) / 1000.0
            if (s <= 0) return "انتهى"
            val days = Math.ceil(s / 86_400).toInt()
            return when (days) { 1 -> "آخر يوم"; 2 -> "باقي يومين"; in 3..10 -> "باقي $days أيام"; else -> "باقي $days يوم" }
        }
    }
}

/** «كم تناسبك» on a coffee page. */
@Serializable
data class TasteMatch(val score: Int? = null, val ratedCount: Int = 0, val needed: Int = 0, val reasons: List<Reason> = emptyList(), val myRating: Int? = null) {
    @Serializable data class Reason(val positive: Boolean, val text: String)
    val verdictAr: String? get() = score?.let { when { it >= 85 -> "على ذوقك تمامًا"; it >= 70 -> "تناسبك"; it >= 50 -> "تستاهل تجربة"; else -> "غالبًا مو على ذوقك" } }
}

/** «اقتراحاتي». */
@Serializable
data class Submission(val id: String, val name: String, val roasterName: String, val status: String, val lotSlug: String? = null, val reviewNote: String? = null,
                      val hasPhoto: Boolean = false, val createdAt: String = "") {
    val statusAr: String get() = when (status) { "APPROVED" -> "انقبلت ✓"; "REJECTED" -> "ما انقبلت"; else -> "قيد المراجعة" }
}
@Serializable internal data class SubmissionList(val submissions: List<Submission> = emptyList())

@Serializable data class Points(val total: Int = 0, val week: Int = 0, val streak: Int = 0, val level: Level? = null) {
    /** A level by all-time points («ذوّاق»), with how far to the next. */
    @Serializable data class Level(val nameAr: String, val emoji: String = "", val min: Int = 0, val next: Int? = null, val nextNameAr: String? = null, val progress: Double = 0.0)
}

/** A grinder in the guide (GET /api/v1/grinders). */
@Serializable
data class GuideGrinder(
    val id: String, val brand: String, val model: String, val variant: String? = null, val category: String = "", val adjustment: String? = null,
    val rangeMin: Double? = null, val rangeMax: Double? = null, val clicksPerRotation: Int? = null, val micronsPerStep: Double? = null,
    val zeroGuideAr: String? = null, val notesAr: String? = null, val sources: List<Source> = emptyList(), val imageUrl: String? = null, val imageCredit: String? = null,
) {
    @Serializable data class Source(val label: String, val url: String)
    val title: String get() = variant?.let { "$model · $it" } ?: model
    val categoryAr: String get() = when (category) { "hand" -> "يدوية"; "electric" -> "كهربائية للبيت"; "single_dose" -> "جرعة وحدة"; "built_in" -> "داخل مكينة القهوة"; "espresso" -> "لمحضّري الإسبريسو"; "commercial" -> "تجارية"; else -> category }
    private val range: String? get() = if (rangeMin != null && rangeMax != null) "${Methods.grams(rangeMin)}–${Methods.grams(rangeMax)}" else null
    val rangeAr: String? get() = when (adjustment) {
        "timer" -> "بالوقت (بدون درجات طحن)"; "stepless" -> range?.let { "$it · بدون درجات" } ?: "بدون درجات"
        "clicks" -> range?.let { "$it كليك" } ?: clicksPerRotation?.let { "$it كليك في اللفة" } ?: "كليكات"
        "stepped" -> range?.let { "$it درجة" } ?: "بدرجات"; else -> null
    }
    fun matches(q: String) = q.isBlank() || "$brand $model ${variant ?: ""}".lowercase().contains(q.trim().lowercase())
}
@Serializable internal data class GrinderList(val grinders: List<GuideGrinder> = emptyList())

/** Home extras from /me/for-you. */
@Serializable
data class ForYouExtras(val resume: List<Resume> = emptyList(), val friendsDrinking: List<Friends> = emptyList()) {
    @Serializable data class Resume(val lot: RemoteLot, val since: String = "")
    @Serializable data class Friends(val lot: RemoteLot, val names: List<String> = emptyList(), val people: Int = 0) {
        val line: String get() {
            val shown = names.take(2); val rest = people - shown.size
            var who = shown.joinToString(" و"); if (rest > 0) who += " و$rest غيرهم"
            return if (people == 1) "$who جرّبها هالأسبوع" else "$who جرّبوها هالأسبوع"
        }
    }
}

/** Browse sort orders (server BROWSE_SORTS). */
enum class BrowseSort(val code: String, val nameAr: String) {
    ForYou("for_you", "لك"), Newest("newest", "الأحدث"), RatingDesc("rating_desc", "الأعلى تقييمًا"), RatingAsc("rating_asc", "الأقل تقييمًا"),
    NameAsc("name_asc", "الاسم أ-ي"), NameDesc("name_desc", "الاسم ي-أ"), Oldest("oldest", "الأقدم");
}

/** The app icon choices and the season that picks one by itself (iOS AppIconChoice). */
enum class AppIconChoice(val nameAr: String) {
    Classic("كلاسيك"), Dark("داكن"), Ramadan("رمضان"), EidFitr("عيد الفطر"), EidAdha("عيد الأضحى"), NationalDay("اليوم الوطني"), FoundingDay("يوم التأسيس");

    companion object {
        fun season(date: java.time.LocalDate): AppIconChoice? {
            if (date.monthValue == 9 && date.dayOfMonth in 20..24) return NationalDay
            if (date.monthValue == 2 && date.dayOfMonth in 20..23) return FoundingDay
            val h = java.time.chrono.HijrahDate.from(date)
            val month = h.get(java.time.temporal.ChronoField.MONTH_OF_YEAR); val day = h.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
            return when {
                month == 9 -> Ramadan
                month == 10 && day <= 3 -> EidFitr
                month == 12 && day in 9..13 -> EidAdha
                else -> null
            }
        }
    }
}

/** «رسائل BunnList»: one-way messages from the team, signed «فريق BunnList». */
@Serializable data class TeamMessage(val id: String, val kind: String = "general", val title: String, val body: String, val createdAt: String = "", val read: Boolean = false)
@Serializable data class TeamMessages(val messages: List<TeamMessage> = emptyList(), val unread: Int = 0)

/** Minimum and latest app builds (/api/v1/app/config): below the minimum the app must update. */
@Serializable data class AppConfig(val ios: Platform = Platform(), val android: Platform = Platform(), val updateMessageAr: String = "") {
    @Serializable data class Platform(val minBuild: Int = 0, val latestBuild: Int = 0, val storeUrl: String = "")
    enum class Verdict { Ok, Suggested, Required }
    companion object {
        fun verdict(build: Int, p: Platform): Verdict = when {
            p.minBuild > 0 && build < p.minBuild -> Verdict.Required
            p.latestBuild > 0 && build < p.latestBuild -> Verdict.Suggested
            else -> Verdict.Ok
        }
    }
}
