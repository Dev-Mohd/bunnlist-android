package com.bunnlist.core

import kotlinx.serialization.Serializable

/** Per-coffee collection flags (GET/PUT /api/v1/catalog/lots/{slug}/collection). */
@Serializable
data class CollectionFlags(val owned: Boolean = false, val favorite: Boolean = false, val wantsToTry: Boolean = false, val tried: Boolean = false)

/** One row of GET /api/v1/collection. */
@Serializable
data class CollectionItem(
    val lotId: String,
    val slug: String,
    val nameAr: String,
    val roasterNameAr: String = "",
    val originNameAr: String? = null,
    val originCode: String? = null,
    val imageUrl: String? = null,
    val flavorNotes: List<String> = emptyList(),
    val savedAt: String? = null,
    val owned: Boolean = false,
    val favorite: Boolean = false,
    val wantsToTry: Boolean = false,
    val tried: Boolean = false,
) {
    val flag: String? get() = flagOf(originCode)
}

@Serializable internal data class CollectionList(val entries: List<CollectionItem> = emptyList())

/** The collection shelves, in the order shown. */
enum class Shelf(val nameAr: String, val matches: (CollectionItem) -> Boolean) {
    All("الكل", { true }),
    Owned("عندي", { it.owned }),
    Favorite("المفضلة", { it.favorite }),
    WantsToTry("أبي أجربها", { it.wantsToTry }),
    Tried("جرّبتها", { it.tried }),
}

@Serializable
data class MyReview(val rating: Int, val brewMethod: String, val wouldBuyAgain: Boolean, val body: String? = null)

@Serializable internal data class MyReviewEnvelope(val review: MyReview? = null)

@Serializable
data class ReviewResult(val averageRating: Double = 0.0, val reviewCount: Int = 0, val review: MyReview)

/** Methods a review can name (the server's BrewMethod enum). */
val reviewMethods = listOf("V60", "ESPRESSO", "CHEMEX", "AEROPRESS", "FRENCH_PRESS", "COLD_BREW", "OTHER")

/** GET /api/v1/me (the parts the Android app shows). */
@Serializable
data class Me(
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val isAdmin: Boolean = false,
    val discoveries: Int = 0,
    val isPrivate: Boolean = false,
    val hideFromLeaderboard: Boolean = false,
    val followersVisibility: String = "everyone",
    val followingVisibility: String = "everyone",
    val activityVisibility: String = "everyone",
    val onboarded: Boolean = true,
    val tastePrefs: List<String> = emptyList(),
    val brewPrefs: List<String> = emptyList(),
)

enum class FeedbackKind(val code: String, val nameAr: String, val placeholder: String) {
    Suggestion("suggestion", "اقتراح", "وش تبي نضيف أو نغيّر؟ محمصة، قهوة، ميزة…"),
    Experience("experience", "تجربة", "احكِ لنا عن تجربتك مع التطبيق أو مع قهوة جرّبتها."),
    Problem("problem", "مشكلة", "وش صار؟ وفي أي شاشة؟");
    companion object { fun of(code: String) = entries.firstOrNull { it.code == code } ?: Suggestion }
}

@Serializable
data class Feedback(val id: String, val kind: String, val body: String, val createdAt: String, val reply: String? = null)

@Serializable internal data class FeedbackList(val items: List<Feedback> = emptyList())
