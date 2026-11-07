package com.bunnlist.core

import kotlinx.serialization.Serializable

/** A post in the community pulse: a published brew, a recipe, or a discovery. */
@Serializable
data class PulseItem(
    val kind: String,
    val at: String,
    val name: String,
    val username: String? = null,
    val avatarUrl: String? = null,
    val lotSlug: String? = null,
    val lotNameAr: String? = null,
    val recipeId: String? = null,
    val recipeTitle: String? = null,
    val methodCode: String? = null,
    val stars: Int? = null,
    val note: String? = null,
    val imageUrl: String? = null,
    val roasterNameAr: String? = null,
    val originCode: String? = null,
    val doseGrams: Double? = null,
    val waterGrams: Double? = null,
    val brewSeconds: Int? = null,
    val brewId: String? = null,
    val commentCount: Int = 0,
) {
    /** What comments attach to: the brew or the recipe; discoveries have none. */
    val commentTarget: Pair<String, String>?
        get() = when {
            kind == "brew" && brewId != null -> "brew" to brewId
            kind == "recipe" && recipeId != null -> "recipe" to recipeId
            else -> null
        }

    val actionAr: String
        get() = when (kind) {
            "brew" -> "حضّر" + (methodCode?.let { " ${Methods.nameAr(it)}" } ?: "")
            "recipe" -> "نشر وصفة"
            "discovery" -> "اكتشف قهوة جديدة"
            else -> ""
        }

    val flag: String? get() = flagOf(originCode)
}

@Serializable data class HomeFeed(val trending: Trending? = null, val pulse: List<PulseItem> = emptyList()) {
    @Serializable data class Trending(val lots: List<TrendingLot> = emptyList(), val roasters: List<TrendingRoaster> = emptyList(), val recipes: List<TrendingRecipe> = emptyList())
}

@Serializable internal data class PulseList(val items: List<PulseItem> = emptyList())

@Serializable
data class OfficialUpdate(val id: String, val title: String, val body: String, val createdAt: String, val commentCount: Int = 0)

@Serializable internal data class UpdateList(val updates: List<OfficialUpdate> = emptyList())

@Serializable
data class LeaderEntry(val rank: Int, val name: String, val username: String? = null, val avatarUrl: String? = null, val points: Int = 0, val isMe: Boolean = false,
                       val levelAr: String? = null, val levelEmoji: String? = null)

@Serializable
data class Leaderboard(val entries: List<LeaderEntry> = emptyList(), val me: Me? = null) {
    @Serializable data class Me(val rank: Int? = null, val points: Int = 0)
}

enum class LeaderKind(val code: String, val nameAr: String) {
    Points("points", "النقاط"), Discoverers("discoverers", "المكتشفين"), Raters("raters", "المقيّمين"), Helpful("helpful", "الأكثر فائدة")
}

@Serializable
data class PersonCard(
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val isPrivate: Boolean = false,
    val isFollowing: Boolean = false,
    val requested: Boolean = false,
    val reasonAr: String? = null,
    val sharedPercent: Int? = null,
)

@Serializable internal data class PeopleList(val people: List<PersonCard> = emptyList())
@Serializable data class SuggestedPeople(val similar: List<PersonCard> = emptyList(), val active: List<PersonCard> = emptyList())

@Serializable
data class PublicProfile(
    val username: String,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val points: Int = 0,
    val followers: Int? = null,
    val following: Int? = null,
    val activityHidden: Boolean = false,
    val isFollowing: Boolean = false,
    val isPrivate: Boolean = false,
    val requested: Boolean = false,
    val isMe: Boolean = false,
    val isBlocked: Boolean = false,
    val discoveries: List<RemoteLot> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
) {
    @Serializable data class Recipe(val id: String, val title: String, val methodCode: String, val lotNameAr: String? = null)
    val shownName: String get() = displayName ?: "@$username"
}

@Serializable data class FollowResult(val following: Boolean, val requested: Boolean)

@Serializable
data class PersonCoffees(val counts: Map<String, Int> = emptyMap(), val coffees: List<CollectionItem> = emptyList())

@Serializable
data class Comment(
    val id: String, val body: String, val createdAt: String, val name: String,
    val username: String? = null, val avatarUrl: String? = null,
    val isMine: Boolean = false, val canDelete: Boolean = false, val isOfficial: Boolean = false,
)

@Serializable data class CommentList(val comments: List<Comment> = emptyList(), val count: Int = 0)

enum class ReportReason(val code: String, val nameAr: String) {
    Spam("spam", "سبام أو إعلان"), Offensive("offensive", "مسيء أو غير لائق"), Wrong("wrong", "معلومة غلط"), Other("other", "سبب ثاني")
}

/** "قبل ٥ د" style relative time from an ISO timestamp. */
object RelativeTime {
    fun ar(iso: String, nowMillis: Long = System.currentTimeMillis()): String {
        val then = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
        val s = ((nowMillis - then) / 1000).coerceAtLeast(0)
        return when {
            s < 60 -> "الحين"
            s < 3600 -> "قبل ${s / 60} د"
            s < 86_400 -> "قبل ${s / 3600} س"
            s < 7 * 86_400 -> "قبل ${s / 86_400} يوم"
            else -> "قبل ${s / (7 * 86_400)} أسبوع"
        }
    }
}

@Serializable data class TrendingRoaster(val slug: String, val nameAr: String, val score: Double = 0.0, val coffees: Int = 0, val imageUrl: String? = null)
@Serializable data class TrendingRecipe(val id: String, val title: String, val methodCode: String = "", val authorName: String = "", val lotNameAr: String? = null,
                                        val likes: Int = 0, val brews: Int = 0)
