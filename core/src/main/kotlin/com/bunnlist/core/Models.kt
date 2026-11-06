package com.bunnlist.core

import kotlinx.serialization.Serializable

// Wire models for the BunnList mobile API (bunnlist.com/api/v1). They mirror the
// server's zod schemas and the iOS app's BunnListCore so both apps read the same data.

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresInSeconds: Int,
    val accountId: String,
    val isNewAccount: Boolean? = null,
)

@Serializable
data class RemoteLot(
    val id: String,
    val slug: String,
    val name: String,
    val nameAr: String,
    val roasterNameAr: String,
    val roasterSlug: String? = null,
    val originNameAr: String? = null,
    val originCode: String? = null,
    val process: String,
    val processLabel: String? = null,
    val flavorNotes: List<String> = emptyList(),
    val roastLevel: String? = null,
    val averageRating: Double = 0.0,
    val reviewCount: Int = 0,
    val imageUrl: String? = null,
) {
    /** Two-letter ISO code → flag emoji, or null. */
    val flag: String? get() = flagOf(originCode)

    val hasRatings: Boolean get() = reviewCount > 0
}

@Serializable
data class BrowsePage(val total: Int, val lots: List<RemoteLot>)
