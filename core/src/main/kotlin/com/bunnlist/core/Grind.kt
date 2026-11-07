package com.bunnlist.core

import kotlin.math.roundToInt

/** Grind levels by approximate microns (iOS GrindLevel). */
enum class GrindLevel(val nameAr: String, val usedForAr: String, val lowerMicrons: Int) {
    ExtraFine("ناعم جدًا", "إسبريسو", 0), Fine("ناعم", "موكا، إيروبرس سريعة", 400), MediumFine("متوسط ناعم", "V60، أوريغامي", 550),
    Medium("متوسط", "كاليتا، كليفر، أداة مسطحة", 700), MediumCoarse("متوسط خشن", "كيمكس، باتشات كبيرة", 850),
    Coarse("خشن", "فرنش برس، ٤:٦", 1000), ExtraCoarse("خشن جدًا", "كولد برو", 1200);
    companion object { fun of(microns: Int) = entries.lastOrNull { microns >= it.lowerMicrons } ?: ExtraFine }
}

/** Hand grinders with published microns per click (iOS Grinder.all). */
data class Grinder(val id: String, val name: String, val micronsPerClick: Double?) {
    fun microns(clicks: Double): Int? = micronsPerClick?.takeIf { clicks > 0 }?.let { (clicks * it).roundToInt() }
    fun clicks(microns: Int): Int? = micronsPerClick?.takeIf { microns > 0 }?.let { (microns / it).roundToInt() }
    /** The same grind on the other grinders that publish their step size. */
    fun equivalents(clicks: Double): List<Pair<Grinder, Int>> {
        val m = microns(clicks) ?: return emptyList()
        return convertible.filter { it.id != id }.mapNotNull { g -> g.clicks(m)?.let { g to it } }
    }

    companion object {
        val all = listOf(
            Grinder("comandante-c40", "Comandante C40", 30.0), Grinder("1zpresso-k-ultra", "1Zpresso K-Ultra", 20.0),
            Grinder("1zpresso-jmax", "1Zpresso J-Max", 8.8), Grinder("1zpresso-zp6", "1Zpresso ZP6", 12.0),
            Grinder("1zpresso-jx-pro", "1Zpresso JX-Pro", 12.5), Grinder("kingrinder-k6", "Kingrinder K6", 16.0),
            Grinder("timemore-c3", "Timemore C3", null), Grinder("fellow-ode-2", "Fellow Ode Gen 2", null), Grinder("baratza-encore", "Baratza Encore", null),
            Grinder("niche-zero", "Niche Zero", null), Grinder("df64", "DF64", null), Grinder("ek43", "Mahlkönig EK43", null),
        )
        val convertible get() = all.filter { it.micronsPerClick != null }
    }
}
