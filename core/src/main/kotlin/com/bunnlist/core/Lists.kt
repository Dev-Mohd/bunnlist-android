package com.bunnlist.core

import kotlinx.serialization.Serializable

@Serializable
data class ListSummary(
    val id: String, val title: String, val note: String? = null, val emoji: String? = null, val isPublic: Boolean = true,
    val count: Int = 0, val covers: List<String> = emptyList(), val updatedAt: String = "", val hasLot: Boolean? = null,
) {
    val countLine: String get() = when (count) { 0 -> "فاضية"; 1 -> "قهوة وحدة"; 2 -> "قهوتين"; in 3..10 -> "$count قهاوي"; else -> "$count قهوة" }
}
@Serializable internal data class ListsEnvelope(val lists: List<ListSummary> = emptyList())

@Serializable
data class ListPage(val list: ListSummary, val owner: Owner, val isMine: Boolean = false, val lots: List<Entry> = emptyList()) {
    @Serializable data class Owner(val name: String, val username: String? = null, val avatarUrl: String? = null)
    @Serializable data class Entry(val lot: RemoteLot, val itemNote: String? = null, val addedAt: String = "")
}

object ListDraft {
    const val MAX_TITLE = 40
    val emojis = listOf("☕", "❤️", "⭐️", "🎁", "☀️", "🧊", "🌙", "🏠", "✈️", "🍓", "🍫", "🌸")
}

@Serializable
data class PublicReview(
    val id: String, val rating: Int, val brewMethod: String, val wouldBuyAgain: Boolean = false, val body: String? = null,
    val authorName: String, val createdAt: String = "", val helpfulCount: Int? = null, val markedHelpful: Boolean? = null,
    val isMine: Boolean? = null, val authorUsername: String? = null, val commentCount: Int? = null,
)
@Serializable internal data class ReviewsEnvelope(val reviews: List<PublicReview> = emptyList())

@Serializable
data class SimilarLots(val similar: List<Item> = emptyList(), val sameRoaster: List<RemoteLot> = emptyList()) {
    @Serializable data class Item(val lot: RemoteLot, val reason: String = "")
}

/** «مجموعتي» at a glance, worked out from the collection (iOS CollectionInsights). */
class CollectionInsights(items: List<CollectionItem>) {
    data class Share(val family: FlavorFamily, val percent: Int)
    val coffees = items.size
    val countryCodes: List<String> = items.mapNotNull { it.originCode?.uppercase() }.filter { it != "XB" }.distinct()
    val roasters = items.map { it.roasterNameAr }.filter { it.isNotEmpty() }.toSet().size
    val favorites = items.count { it.favorite }
    val shares: List<Share> = run {
        val counts = HashMap<FlavorFamily, Int>()
        items.flatMap { it.flavorNotes }.map(FlavorFamily::of).filter { it != FlavorFamily.Other }.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        val top = counts.entries.sortedWith(compareByDescending<Map.Entry<FlavorFamily, Int>> { it.value }.thenBy { it.key.code }).take(5)
        val pct = percentages(top.map { it.value })
        top.zip(pct).map { Share(it.first.key, it.second) }.filter { it.percent > 0 }
    }

    companion object {
        /** Whole percentages that add up to exactly 100 (largest remainder). */
        fun percentages(counts: List<Int>): List<Int> {
            val total = counts.sum(); if (total == 0) return counts.map { 0 }
            val exact = counts.map { it * 100.0 / total }
            val out = exact.map { it.toInt() }.toMutableList()
            exact.indices.sortedByDescending { exact[it] - out[it] }.take(100 - out.sum()).forEach { out[it] += 1 }
            return out
        }
    }
}

/** Where a coffee's notes lean: chocolate and nuts (0) … caramel (0.5) … fruit and flowers (1). Earlier notes weigh more. */
object TasteLean {
    private fun weight(f: FlavorFamily): Double? = when (f) {
        FlavorFamily.Berry, FlavorFamily.Citrus, FlavorFamily.StoneFruit, FlavorFamily.Tropical, FlavorFamily.Floral -> 1.0
        FlavorFamily.Tea -> 0.9; FlavorFamily.DriedFruit -> 0.8; FlavorFamily.Honey, FlavorFamily.Caramel -> 0.5
        FlavorFamily.Spice -> 0.2; FlavorFamily.Nutty -> 0.1; FlavorFamily.Chocolate -> 0.0; FlavorFamily.Other -> null
    }
    fun position(notes: List<String>): Double? {
        var sum = 0.0; var total = 0.0
        notes.take(6).forEachIndexed { i, n -> weight(FlavorFamily.of(n))?.let { v -> val w = 1.0 / (i + 1); sum += v * w; total += w } }
        return if (total > 0) sum / total else null
    }
}
