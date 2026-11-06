package com.bunnlist.core

import kotlinx.serialization.Serializable

/** GET /api/v1/catalog/lots/{slug}. Fields older servers may omit default to empty. */
@Serializable
data class RemoteLotDetail(
    val id: String,
    val slug: String,
    val name: String,
    val nameAr: String,
    val roasterNameAr: String,
    val roasterSlug: String? = null,
    val originNameAr: String? = null,
    val originCode: String? = null,
    val process: String = "",
    val processLabel: String? = null,
    val flavorNotes: List<String> = emptyList(),
    val roastLevel: String? = null,
    val averageRating: Double = 0.0,
    val reviewCount: Int = 0,
    val imageUrl: String? = null,
    val descriptionAr: String? = null,
    val regionAr: String? = null,
    val variety: String? = null,
    val recommendedBrewMethods: List<String> = emptyList(),
    val brewStats: List<RemoteBrewStat> = emptyList(),
    val community: RemoteLotCommunity? = null,
    val tags: List<RemoteLotTag> = emptyList(),
    val altitudeMeters: Int? = null,
    val buy: RemoteBuyLink? = null,
    val roasterRecipes: List<RemoteRoasterRecipe> = emptyList(),
) {
    val flag: String? get() = flagOf(originCode)
    val processAr: String? get() = processLabel ?: Methods.processAr(process)
}

@Serializable
data class RemoteBrewStat(val method: String, val averageRating: Double = 0.0, val reviewCount: Int = 0, val wouldBuyAgain: Int = 0)

@Serializable
data class RemoteLotCommunity(val collectors: Int = 0, val brewers: Int = 0, val methods: List<Method> = emptyList()) {
    @Serializable data class Method(val method: String, val count: Int)
}

@Serializable
data class RemoteLotTag(val tag: String, val note: String? = null)

@Serializable
data class RemoteBuyLink(val url: String, val host: String, val kind: String, val storeNameAr: String? = null) {
    val isRoasterStore: Boolean get() = kind != "store"
    val title: String get() = if (isRoasterStore) "اشترِ من المحمصة" else "اشترِها من ${storeNameAr ?: host}"
    val subtitle: String get() = if (isRoasterStore) "$host · الموقع الرسمي" else "متجر يبيعها · $host"
}

@Serializable
data class RemoteRoasterRecipe(
    val method: String,
    val serve: String? = null,
    val link: String? = null,
    val tool: String? = null,
    val doseG: Double? = null,
    val waterMl: Int? = null,
    val tempC: Int? = null,
    val timeSec: Int? = null,
    val grind: String? = null,
    val ratio: Double? = null,
    val steps: List<String> = emptyList(),
) {
    val isIced: Boolean get() = serve == "ICED"
    val label: String get() = (tool ?: Methods.nameAr(method)) + if (isIced) " · بارد" else ""
}

/** Arabic names for the server's method and process codes. */
object Methods {
    fun nameAr(code: String): String = when (code) {
        "V60" -> "V60"
        "CHEMEX" -> "كيمكس"
        "AEROPRESS" -> "إيروبرس"
        "FRENCH_PRESS" -> "فرنش برس"
        "ESPRESSO" -> "إسبريسو"
        "COLD_BREW" -> "كولد برو"
        "ARABIC" -> "قهوة سعودية"
        "XBLOOM" -> "xBloom"
        else -> code
    }

    fun processAr(code: String): String? = when (code) {
        "WASHED" -> "مغسولة"
        "NATURAL" -> "مجففة"
        "HONEY" -> "عسلية"
        "ANAEROBIC" -> "لاهوائية"
        "SEMI_WASHED", "WET_HULLED" -> "شبه مغسولة"
        "CARBONIC_MACERATION" -> "نقع كربوني"
        "EXPERIMENTAL" -> "تجريبية"
        else -> null // OTHER, empty, or a code we don't name yet: hide the tile
    }

    /** "2:15" for 135 seconds, "30 ث" under a minute. */
    fun clock(seconds: Int): String = if (seconds < 60) "$seconds ث" else "${seconds / 60}:${String.format(java.util.Locale.ROOT, "%02d", seconds % 60)}"

    /** 18 → "18", 18.5 → "18.5". */
    fun grams(value: Double): String = if (value == Math.rint(value)) value.toLong().toString() else String.format(java.util.Locale.ROOT, "%.1f", value)
}

internal fun flagOf(code: String?): String? = code?.takeIf { it.length == 2 && it.all(Char::isLetter) }?.uppercase()?.let { c ->
    c.map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
}
