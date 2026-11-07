package com.bunnlist.core

import kotlinx.serialization.Serializable

/** A coffee in «ترند هالأسبوع»: the lot's fields plus its rank and why it's trending. */
@Serializable
data class TrendingLot(
    val id: String, val slug: String, val name: String = "", val nameAr: String, val roasterNameAr: String = "",
    val roasterSlug: String? = null, val originNameAr: String? = null, val originCode: String? = null,
    val process: String = "", val processLabel: String? = null, val flavorNotes: List<String> = emptyList(),
    val averageRating: Double = 0.0, val reviewCount: Int = 0, val imageUrl: String? = null,
    val rank: Int = 0, val reason: String? = null, val brews: Int = 0, val reviews: Int = 0, val adds: Int = 0, val recipes: Int = 0,
) {
    val lot: RemoteLot get() = RemoteLot(id, slug, name, nameAr, roasterNameAr, roasterSlug, originNameAr, originCode, process, processLabel, flavorNotes, null, averageRating, reviewCount, imageUrl)
    val isTopUp: Boolean get() = reason == "added"

    /** Why it's here, as short chips: "١٢ تجربة", "٥ تقييمات", "+٩ أضافوها". */
    val reasonsAr: List<String>
        get() {
            if (isTopUp) return listOf("+$adds أضافوها هالشهر")
            return buildList {
                if (brews > 0) add(HomeText.count(brews, "تجربة وحدة", "تجربتين", "تجارب", "تجربة"))
                if (reviews > 0) add(HomeText.count(reviews, "تقييم واحد", "تقييمين", "تقييمات", "تقييم"))
                if (adds > 0) add("+$adds أضافوها")
                if (recipes > 0) add(HomeText.count(recipes, "وصفة وحدة", "وصفتين", "وصفات", "وصفة"))
            }
        }
}

@Serializable data class HomeTrending(val trending: Trending = Trending()) {
    @Serializable data class Trending(val lots: List<TrendingLot> = emptyList())
}

@Serializable
data class RoasterCard(val slug: String, val nameAr: String, val name: String? = null, val cityAr: String? = null,
                       val logoUrl: String? = null, val lots: Int = 0, val averageRating: Double = 0.0, val images: List<String> = emptyList())
@Serializable internal data class RoasterList(val roasters: List<RoasterCard> = emptyList())

@Serializable
data class OriginInfo(
    val code: String, val nameAr: String, val nameEn: String = "", val lots: Int = 0, val roasters: Int = 0,
    val families: List<Key> = emptyList(), val processes: List<Key> = emptyList(), val regions: List<String> = emptyList(),
) {
    @Serializable data class Key(val key: String, val count: Int = 0)
    val flag: String? get() = flagOf(code)
}
@Serializable internal data class OriginList(val origins: List<OriginInfo> = emptyList())

/** The words behind «اكتشف», same rules as iOS (DiscoverText, Greeting, ISODate.ago). */
object HomeText {
    /** "صباح الخير" until noon, "مساء الخير" after; with the first name when known. */
    fun greeting(hour: Int, name: String?): String {
        val base = if (hour in 5..11) "صباح الخير" else "مساء الخير"
        val first = name?.trim()?.split(" ")?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return base
        return "$base، $first"
    }

    /** Arabic counting: 1 and 2 have their own words, 3–10 take the plural, 11+ the singular. */
    fun count(n: Int, one: String, two: String, few: String, many: String) = when (n) {
        1 -> one; 2 -> two; in 3..10 -> "$n $few"; else -> "$n $many"
    }

    fun coffees(n: Int) = when (n) { 1 -> "قهوة وحدة"; 2 -> "قهوتين"; else -> "$n قهاوي" }

    /** "اليوم", "أمس", "من ٥ أيام", "من ١١ يوم", "من أسبوعين" … from an ISO date. */
    fun ago(iso: String, nowMillis: Long = System.currentTimeMillis()): String {
        val then = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
        val days = ((nowMillis - then) / 86_400_000L).toInt().coerceAtLeast(0)
        return when (days) {
            0 -> "اليوم"; 1 -> "أمس"; 2 -> "من يومين"
            in 3..10 -> "من $days أيام"; in 11..13 -> "من $days يوم"; in 14..20 -> "من أسبوعين"
            in 21..59 -> "من ${days / 7} أسابيع"; else -> "من ${days / 30} شهور"
        }
    }

    /** "🇪🇹 إثيوبيا · لاهوائية". */
    fun originLine(originCode: String?, originNameAr: String?, process: String, processLabel: String?): String {
        val origin = listOfNotNull(flagOf(originCode), originNameAr).joinToString(" ")
        return listOf(origin, processLabel ?: Methods.processAr(process).orEmpty()).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    /** The newest coffee of each roaster, so one big import doesn't fill the row. */
    fun onePerRoaster(lots: List<RemoteLot>, limit: Int): List<RemoteLot> {
        val seen = HashSet<String>(); val out = ArrayList<RemoteLot>()
        for (l in lots) if (seen.add(l.roasterNameAr)) { out += l; if (out.size == limit) break }
        return out
    }

    /** "★ 4.5 · ١٢ تقييم", or null before anyone rated it. */
    fun ratingLine(lot: RemoteLot): String? = if (lot.reviewCount <= 0) null else
        "★ ${String.format(java.util.Locale.ROOT, "%.1f", lot.averageRating)} · ${count(lot.reviewCount, "تقييم واحد", "تقييمين", "تقييمات", "تقييم")}"

    /** The distinct families of a coffee's notes, in order. */
    fun spectrum(notes: List<String>, limit: Int = 4): List<FlavorFamily> {
        val out = ArrayList<FlavorFamily>()
        for (f in notes.map(FlavorFamily::of)) if (f != FlavorFamily.Other && f !in out) { out += f; if (out.size == limit) break }
        return out
    }

    fun dominant(notes: List<String>): FlavorFamily = notes.asSequence().map(FlavorFamily::of).firstOrNull { it != FlavorFamily.Other } ?: FlavorFamily.Other
}

/** GET /api/v1/catalog/roasters/{slug}: a roaster's page. */
@Serializable
data class RoasterPage(
    val roaster: Roaster, val isFollowing: Boolean = false, val origins: List<Key> = emptyList(), val processes: List<Key> = emptyList(),
    val families: List<Key> = emptyList(), val topRated: List<RemoteLot> = emptyList(), val newest: List<RemoteLot> = emptyList(), val lots: List<RemoteLot> = emptyList(),
) {
    @Serializable data class Roaster(val slug: String, val nameAr: String, val name: String = "", val cityAr: String? = null, val logoUrl: String? = null,
                                     val lots: Int = 0, val averageRating: Double = 0.0, val reviews: Int = 0, val images: List<String> = emptyList(), val followers: Int = 0)
    @Serializable data class Key(val key: String, val count: Int = 0)
    /** Family shares in whole percents adding up to 100. */
    val familyShares: List<Pair<FlavorFamily, Int>> get() {
        val fam = families.mapNotNull { k -> FlavorFamily.entries.firstOrNull { it.code == k.key && it != FlavorFamily.Other }?.let { it to k.count } }
        return fam.zip(CollectionInsights.percentages(fam.map { it.second })).map { it.first.first to it.second }.filter { it.second > 0 }
    }
}
