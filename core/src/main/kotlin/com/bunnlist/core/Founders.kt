package com.bunnlist.core

import kotlinx.serialization.Serializable

// «مبدع مؤسس»: the first 100 creators get a numbered, permanent badge, and only they can
// show their accounts elsewhere on their profile and recipes.

@Serializable
data class FounderBadge(val number: Int) {
    val label: String get() = label(number)
    companion object { fun label(number: Int) = "#" + number.toString().padStart(2, '0') }
}

/** The networks a founder can link. */
enum class SocialNetwork(val code: String, val nameAr: String, val mark: String, private val maxLength: Int, private val hosts: List<String>) {
    TikTok("tiktok", "تيك توك", "TT", 30, listOf("tiktok.com/@", "tiktok.com/")),
    Instagram("instagram", "إنستقرام", "IG", 30, listOf("instagram.com/")),
    YouTube("youtube", "يوتيوب", "YT", 30, listOf("youtube.com/@", "youtube.com/c/", "youtube.com/user/", "youtube.com/")),
    X("x", "إكس", "X", 15, listOf("x.com/", "twitter.com/"));

    fun url(handle: String): String = when (this) {
        TikTok -> "https://www.tiktok.com/@$handle"
        Instagram -> "https://www.instagram.com/$handle"
        YouTube -> "https://www.youtube.com/@$handle"
        X -> "https://x.com/$handle"
    }

    /** The handle from "@name", "name" or a profile link; null when it isn't one (mirrors the server). */
    fun handleFrom(input: String): String? {
        var text = input.trim()
        val lower = text.lowercase()
        for (host in hosts) {
            val i = lower.indexOf(host)
            if (i >= 0) { text = text.substring(i + host.length); break }
        }
        text = text.trimStart('@').split('/', '?', '#').firstOrNull().orEmpty()
        return text.takeIf { it.isNotEmpty() && it.length <= maxLength && it.all { c -> c.isLetterOrDigit() && c.code < 128 || c in "._-" } }
    }
}

/** A founder's accounts elsewhere, as handles. */
@Serializable
data class Socials(val tiktok: String? = null, val instagram: String? = null, val youtube: String? = null, val x: String? = null) {
    operator fun get(network: SocialNetwork): String? = when (network) {
        SocialNetwork.TikTok -> tiktok; SocialNetwork.Instagram -> instagram; SocialNetwork.YouTube -> youtube; SocialNetwork.X -> x
    }
    val linked: List<Pair<SocialNetwork, String>> get() = SocialNetwork.entries.mapNotNull { n -> get(n)?.takeIf { it.isNotBlank() }?.let { n to it } }
    val isEmpty: Boolean get() = linked.isEmpty()
}

@Serializable
data class FounderStats(val limit: Int = 100, val taken: Int = 0, val remaining: Int = 100) {
    val progress: Float get() = if (limit > 0) (taken.toFloat() / limit).coerceIn(0f, 1f) else 0f
    val isFull: Boolean get() = remaining <= 0
}
