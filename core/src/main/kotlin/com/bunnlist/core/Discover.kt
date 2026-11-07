package com.bunnlist.core

import kotlinx.serialization.Serializable

/** GET /api/v1/me/for-you. */
@Serializable
data class ForYou(
    val dailyPick: DailyPick? = null,
    val roasterNews: List<RoasterNews> = emptyList(),
) {
    @Serializable data class DailyPick(val lot: RemoteLot, val score: Int? = null, val reason: String = "", val rated: Int = 0) {
        /** The pick follows the person's taste only once they rated enough coffees. */
        val isPersonal: Boolean get() = score != null
        /** Ratings still needed before the pick follows their taste (iOS: 3). */
        val ratingsToGo: Int? get() = if (score != null) null else maxOf(0, 3 - rated)
    }
    @Serializable data class RoasterNews(val roasterNameAr: String, val lots: List<NewsLot> = emptyList())
    @Serializable data class NewsLot(val slug: String, val nameAr: String, val imageUrl: String? = null, val publishedAt: String = "", val flavorNotes: List<String> = emptyList())
}

/** A coffee in a horizontal row: from a roaster you follow, or newly arrived. */
data class RowLot(val slug: String, val nameAr: String, val subtitle: String, val imageUrl: String?)

object DiscoverRows {
    /** Newest lots from followed roasters, interleaved so one roaster does not fill the row. */
    fun fromRoasters(news: List<ForYou.RoasterNews>, limit: Int = 12): List<RowLot> {
        val queues = news.map { n -> ArrayDeque(n.lots.map { RowLot(it.slug, it.nameAr, n.roasterNameAr, it.imageUrl) }) }
        val out = ArrayList<RowLot>()
        while (out.size < limit && queues.any { it.isNotEmpty() }) queues.forEach { q -> if (out.size < limit) q.removeFirstOrNull()?.let { if (out.none { o -> o.slug == it.slug }) out += it } }
        return out
    }
}

@Serializable data class Facet(val value: String, val labelAr: String, val count: Int = 0)
@Serializable data class Facets(
    val origins: List<Facet> = emptyList(), val roasters: List<Facet> = emptyList(), val processes: List<Facet> = emptyList(),
    val flavors: List<Facet> = emptyList(),
)

/** Filters for /api/v1/catalog/browse. */
data class BrowseFilters(
    val roasters: Set<String> = emptySet(),
    val origins: Set<String> = emptySet(),
    val processes: Set<String> = emptySet(),
    val flavors: Set<String> = emptySet(),
    val sort: String? = null,
) {
    val isEmpty: Boolean get() = roasters.isEmpty() && origins.isEmpty() && processes.isEmpty() && flavors.isEmpty()
    val count: Int get() = roasters.size + origins.size + processes.size + flavors.size
    fun params(): Map<String, String> = buildMap {
        if (roasters.isNotEmpty()) put("roasters", roasters.sorted().joinToString(","))
        if (origins.isNotEmpty()) put("origins", origins.sorted().joinToString(","))
        if (processes.isNotEmpty()) put("processes", processes.sorted().joinToString(","))
        if (flavors.isNotEmpty()) put("flavors", flavors.sorted().joinToString(","))
        sort?.let { put("sort", it) }
    }
}

/** What the bag scan read, and the published coffees that match. */
@Serializable
data class BagScanResult(val extraction: Extraction = Extraction(), val matches: List<RemoteLot> = emptyList(), val remainingToday: Int = 0) {
    @Serializable data class Extraction(
        val name: String? = null, val nameAr: String? = null, val roaster: String? = null, val roasterAr: String? = null,
        val originCountryAr: String? = null,
    ) {
        val summary: String get() = listOfNotNull(nameAr ?: name, roasterAr ?: roaster, originCountryAr).joinToString(" · ")
    }
}
