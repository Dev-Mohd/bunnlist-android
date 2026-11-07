package com.bunnlist.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive

/** GET /api/v1/me/summary: «ملفي» at a glance. */
@Serializable
data class ProfileSummary(
    val collection: Counts = Counts(), val recentBags: List<String> = emptyList(), val discoveries: Int = 0, val submissions: Int = 0,
    val recipes: Int = 0, val experiences: Int = 0, val ratings: Int = 0, val followers: Int? = null, val following: Int? = null,
    val followRequests: Int? = null, val taste: Taste = Taste(),
) {
    @Serializable data class Counts(val owned: Int = 0, val favorite: Int = 0, val wantsToTry: Int = 0, val tried: Int = 0)
    @Serializable data class Group(val name: String, val count: Int = 0, val average: Double = 0.0)
    @Serializable data class Taste(
        val unlocked: Boolean = false, val progress: Int = 0, val required: Int = 5, val averageRating: Double? = null,
        val topOrigins: List<Group> = emptyList(), val topProcesses: List<Group> = emptyList(), val topFlavors: List<String> = emptyList(), val favoriteMethod: String? = null,
    )
}

@Serializable data class UsernameCheck(val username: String = "", val available: Boolean = false, val problem: String? = null) {
    val problemAr: String? get() = when (problem) {
        null -> null; "too_short" -> "٣ أحرف على الأقل"; "too_long" -> "٢٠ حرف كحد أقصى"; "must_start_with_letter" -> "يبدأ بحرف إنجليزي"
        "invalid_chars" -> "حروف إنجليزية صغيرة وأرقام و _ و . فقط"; "reserved" -> "هذا الاسم محجوز"; "taken" -> "الاسم مستخدم، جرّب غيره"; else -> "اسم غير صالح"
    }
}

/** Who can see a part of your profile. */
enum class Visibility(val code: String, val nameAr: String) {
    Everyone("everyone", "الكل"), Followers("followers", "متابعيني"), Me("me", "أنا بس");
    companion object { fun of(code: String) = entries.firstOrNull { it.code == code } ?: Everyone }
}

/** Push notification kinds, with the iOS wording. */
enum class NotificationKind(val code: String, val titleAr: String, val detailAr: String) {
    NewFollower("newFollower", "متابع جديد", "أحد صار يتابعك"),
    ReviewHelpful("reviewHelpful", "تقييمات مفيدة", "أحد شاف تقييمك مفيد"),
    RecipeLiked("recipeLiked", "إعجابات الوصفات", "أحد عجبته وصفة نشرتها"),
    Comments("comments", "التعليقات", "أحد علّق على تحضيرك أو وصفتك"),
    SubmissionApproved("submissionApproved", "انقبل اقتراحك", "قهوة اقترحتها صارت في الدليل"),
    SubmissionRejected("submissionRejected", "ما انقبل اقتراحك", "مع سبب الرفض إن وُجد"),
    WeeklySummary("weeklySummary", "الملخص الأسبوعي", "كل جمعة العصر: نقاطك ونشاطك في الأسبوع"),
    Announcements("announcements", "أخبار التطبيق", "قهوات ومحامص جديدة ومزايا من فريق التطبيق");

    companion object {
        fun parse(o: JsonObject): Map<NotificationKind, Boolean> = entries.associateWith { k -> o[k.code]?.jsonPrimitive?.boolean ?: true }
    }
}
