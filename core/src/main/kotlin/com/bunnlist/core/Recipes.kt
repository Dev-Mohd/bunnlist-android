package com.bunnlist.core

import kotlinx.serialization.Serializable

@Serializable
data class RemoteRecipeStep(val kind: String, val addedWaterGrams: Double? = null, val seconds: Int? = null, val note: String = "")

@Serializable
data class RemoteRecipe(
    val id: String, val clientId: String = "", val title: String = "", val methodCode: String, val temperature: String = "hot",
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


/** What the recipe editor holds; saved with POST /api/v1/recipes (create, or edit by clientId). */
data class RecipeDraft(
    val clientId: String = java.util.UUID.randomUUID().toString(),
    val lotSlug: String? = null,
    val title: String = "",
    val methodCode: String = "V60",
    val iced: Boolean = false,
    val doseGrams: Double? = null,
    val hotWaterGrams: Double? = null,
    val iceGrams: Double? = null,
    val yieldGrams: Double? = null,
    val waterTempC: Double? = null,
    val grinder: String = "",
    val grindSetting: String = "",
    val brewer: String = "",
    val notes: String = "",
    val steps: List<BrewStep> = emptyList(),
    val sourceId: String? = null,
    val publish: Boolean = false,
) {
    val isEspresso: Boolean get() = methodCode == "ESPRESSO"
    val pouredGrams: Double get() = steps.filter { it.kind.addsWater }.sumOf { it.addedWaterGrams ?: 0.0 }

    /** Why it can't be published yet (the server's own rules); drafts save anyway. */
    fun problemsAr(): List<String> = buildList {
        if (title.isBlank()) add("اكتب اسم للوصفة")
        if (steps.any { it.kind.addsWater && (it.addedWaterGrams ?: 1.0) <= 0 }) add("كمية الماء في الصبّات لازم تكون أكثر من صفر")
        if (isEspresso) {
            if ((doseGrams ?: 0.0) <= 0) add("اكتب وزن البن")
            if ((yieldGrams ?: 0.0) <= 0) add("اكتب وزن الناتج")
        } else {
            val water = hotWaterGrams ?: 0.0
            if (water <= 0) add("اكتب كمية الماء الحار")
            else if (Math.abs(pouredGrams - water) > 1) add("مجموع الصبّات (${Methods.grams(pouredGrams)}غ) لازم يساوي الماء الحار (${Methods.grams(water)}غ)")
            if (steps.isEmpty()) add("أضف خطوة وحدة على الأقل")
        }
        if (grindSetting.isBlank()) add("اكتب درجة الطحن")
    }

    fun body(): kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.buildJsonObject {
        fun p(k: String, v: Double?) { if (v != null) put(k, kotlinx.serialization.json.JsonPrimitive(v)) else put(k, kotlinx.serialization.json.JsonNull) }
        put("clientId", kotlinx.serialization.json.JsonPrimitive(clientId))
        put("lotSlug", lotSlug?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
        put("title", kotlinx.serialization.json.JsonPrimitive(title.trim().take(80)))
        put("methodCode", kotlinx.serialization.json.JsonPrimitive(methodCode))
        put("temperature", kotlinx.serialization.json.JsonPrimitive(if (iced) "iced" else "hot"))
        p("doseGrams", doseGrams); p("hotWaterGrams", if (isEspresso) null else hotWaterGrams); p("iceGrams", if (iced) iceGrams else null)
        p("yieldGrams", if (isEspresso) yieldGrams else null); p("waterTempC", waterTempC)
        put("grinder", kotlinx.serialization.json.JsonPrimitive(grinder.trim().take(60)))
        put("grindSetting", kotlinx.serialization.json.JsonPrimitive(grindSetting.trim().take(60)))
        put("brewer", kotlinx.serialization.json.JsonPrimitive(brewer.trim().take(40)))
        put("notes", kotlinx.serialization.json.JsonPrimitive(notes.take(2000)))
        put("steps", kotlinx.serialization.json.JsonArray(steps.take(20).map { s ->
            kotlinx.serialization.json.buildJsonObject {
                put("kind", kotlinx.serialization.json.JsonPrimitive(s.kind.name.lowercase()))
                put("addedWaterGrams", if (s.kind.addsWater && s.addedWaterGrams != null) kotlinx.serialization.json.JsonPrimitive(s.addedWaterGrams) else kotlinx.serialization.json.JsonNull)
                put("seconds", s.seconds?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                put("note", kotlinx.serialization.json.JsonPrimitive(s.note.take(300)))
            }
        }))
        put("sourceId", sourceId?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
        put("publish", kotlinx.serialization.json.JsonPrimitive(publish))
    }

    companion object {
        fun from(preset: BrewPreset, lotSlug: String?) = RecipeDraft(lotSlug = lotSlug, methodCode = preset.methodCode, iced = preset.iced, doseGrams = preset.doseGrams,
            hotWaterGrams = preset.hotWaterGrams, iceGrams = preset.iceGrams, yieldGrams = preset.yieldGrams, waterTempC = preset.waterTempC,
            grindSetting = preset.grindAr, brewer = preset.brewer, steps = preset.steps)
        fun from(r: RemoteRecipe, copy: Boolean) = RecipeDraft(clientId = if (copy) java.util.UUID.randomUUID().toString() else r.clientId.ifBlank { java.util.UUID.randomUUID().toString() },
            lotSlug = r.lot?.slug, title = if (copy) "" else r.title, methodCode = r.methodCode, iced = r.temperature == "iced", doseGrams = r.doseGrams,
            hotWaterGrams = r.hotWaterGrams, iceGrams = r.iceGrams, yieldGrams = r.yieldGrams, waterTempC = r.waterTempC, grinder = r.grinder,
            grindSetting = r.grindSetting, brewer = r.brewer, notes = if (copy) "" else r.notes, steps = r.toPreset().steps,
            sourceId = if (copy) r.id else null, publish = !copy && r.state == "published")
    }
}
