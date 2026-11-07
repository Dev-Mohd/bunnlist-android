package com.bunnlist.core

import kotlinx.serialization.Serializable

@Serializable
data class RemoteRecipeStep(val kind: String, val addedWaterGrams: Double? = null, val seconds: Int? = null, val note: String = "")

@Serializable
data class RemoteRecipe(
    val id: String, val title: String = "", val methodCode: String, val temperature: String = "hot",
    val lot: Lot? = null, val doseGrams: Double? = null, val hotWaterGrams: Double? = null, val iceGrams: Double? = null,
    val yieldGrams: Double? = null, val waterTempC: Double? = null, val grinder: String = "", val grindSetting: String = "",
    val grindLevel: String? = null, val brewer: String = "", val notes: String = "", val steps: List<RemoteRecipeStep> = emptyList(),
    val state: String = "draft", val authorName: String = "", val isMine: Boolean = false, val likeCount: Int? = null, val likedByMe: Boolean? = null,
    val publishedAt: String? = null, val updatedAt: String = "",
) {
    @Serializable data class Lot(val slug: String, val nameAr: String, val roasterNameAr: String = "")

    val grindText: String get() = listOf(grindSetting.trim(), grinder.trim()).filter { it.isNotEmpty() }.let {
        if (it.size == 2) "${it[0]} على ${it[1]}" else it.firstOrNull() ?: ""
    }

    /** As a recipe the guided brew can run. */
    fun toPreset(): BrewPreset = BrewPreset(
        id = "recipe:$id", nameAr = title.ifBlank { "وصفة" }, summaryAr = listOfNotNull(Methods.nameAr(methodCode), lot?.nameAr).joinToString(" · "),
        methodCode = methodCode, iced = temperature == "iced" || (iceGrams ?: 0.0) > 0, doseGrams = doseGrams ?: 0.0, hotWaterGrams = hotWaterGrams,
        iceGrams = iceGrams, yieldGrams = yieldGrams, waterTempC = waterTempC ?: 0.0, grindAr = grindText, brewer = brewer,
        steps = steps.map { s -> BrewStep(StepKind.entries.firstOrNull { it.name.equals(s.kind, ignoreCase = true) } ?: StepKind.Wait, s.addedWaterGrams, s.seconds, s.note) },
        source = if (isMine) "mine" else "community", recipeId = id, author = authorName, likes = likeCount ?: 0,
    )
}
@Serializable internal data class RecipeList(val recipes: List<RemoteRecipe> = emptyList())

@Serializable data class LikeState(val count: Int = 0, val liked: Boolean = false)

enum class BrewTaste(val code: String, val nameAr: String, val hintAr: String) {
    Balanced("balanced", "متوازنة", "حلوة ومتوازنة"), Sour("sour", "حامضة", "حموضة حادة، ناقصة"), Bitter("bitter", "مرّة", "مرارة، تنشّف الفم"),
    Weak("weak", "خفيفة", "مائية، طعمها ضعيف"), Strong("strong", "ثقيلة", "قوية زيادة، ثقيلة");
}

/** One change to try next time, never a sure fix (iOS TasteFix.suggest). */
data class TasteFix(val titleAr: String, val detailAr: String) {
    companion object {
        fun suggest(taste: BrewTaste, methodCode: String, doseGrams: Double?, waterGrams: Double?): TasteFix {
            val espresso = methodCode == "ESPRESSO"
            return when (taste) {
                BrewTaste.Balanced -> TasteFix("ثبّتها", "كرّر نفس الطحنة والأرقام. إذا تبي تجرّب، غيّر شي واحد بس.")
                BrewTaste.Sour -> TasteFix("طحنة أنعم شوي", "الحموضة الحادة غالبًا معناها استخلاص ناقص، والطحنة الأنعم تطلّع أكثر. ثبّت الباقي.")
                BrewTaste.Bitter -> TasteFix("طحنة أخشن شوي", "المرارة غالبًا معناها استخلاص زايد، والطحنة الأخشن تخفّفه. ثبّت الباقي.")
                BrewTaste.Weak -> TasteFix(doseGrams?.takeIf { it > 0 }?.let { "زِد البن إلى ${Methods.grams(it + if (espresso) 0.5 else 1.0)}غ" } ?: "زِد البن غرام واحد",
                    "نفس الماء ونفس الطحنة، بس بن أكثر شوي عشان يقوى الطعم.")
                BrewTaste.Strong -> TasteFix(waterGrams?.takeIf { it > 0 }?.let { w -> val n = Methods.grams(w + if (espresso) 4.0 else 20.0); if (espresso) "طوّل الناتج إلى ${n}غ" else "زِد الماء إلى ${n}غ" }
                    ?: if (espresso) "طوّل الناتج شوي" else "زِد الماء شوي", "نفس البن ونفس الطحنة، بس ماء أكثر شوي عشان تخف.")
            }
        }
    }
}

@Serializable
data class Experience(val id: String, val clientId: String = "", val methodCode: String, val stars: Int, val brewedAt: String = "", val note: String = "",
                      val state: String = "draft", val log: Log? = null) {
    @Serializable data class Log(val recipeTitle: String? = null, val doseGrams: Double? = null, val waterGrams: Double? = null, val brewSeconds: Int? = null, val taste: String? = null)
}
@Serializable internal data class ExperienceList(val experiences: List<Experience> = emptyList())

@Serializable
data class Passport(val coffees: Int = 0, val countries: Int = 0, val regions: Int = 0, val processes: Int = 0,
                    val stamps: List<Stamp> = emptyList(), val badges: List<Badge> = emptyList()) {
    @Serializable data class Stamp(val code: String, val nameAr: String, val region: String? = null, val count: Int = 1, val firstAt: String = "")
    @Serializable data class Badge(val id: String, val titleAr: String, val detailAr: String, val icon: String = "", val current: Int = 0, val goal: Int = 1, val earned: Boolean = false) {
        val progress: Float get() = if (goal <= 0) 0f else (current.toFloat() / goal).coerceIn(0f, 1f)
    }
    val earnedCount: Int get() = badges.count { it.earned }
}
