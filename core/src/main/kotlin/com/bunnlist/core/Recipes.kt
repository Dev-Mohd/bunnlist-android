package com.bunnlist.core

import kotlinx.serialization.Serializable

@Serializable
data class RemoteRecipeStep(val kind: String, val addedWaterGrams: Double? = null, val seconds: Int? = null, val note: String = "",
                            val pourStyle: String? = null, val pourSpeed: String? = null,
                            val tempC: Double? = null, val addition: String? = null, val additionTemp: String? = null)

@Serializable
data class RemoteRecipe(
    val id: String, val clientId: String = "", val title: String = "", val methodCode: String, val temperature: String = "hot",
    val lot: Lot? = null, val doseGrams: Double? = null, val hotWaterGrams: Double? = null, val iceGrams: Double? = null,
    val yieldGrams: Double? = null, val waterTempC: Double? = null, val grinder: String = "", val grindSetting: String = "",
    val grindLevel: String? = null, val brewer: String = "", val notes: String = "", val steps: List<RemoteRecipeStep> = emptyList(),
    val grinderModel: String? = null, val grindMicrons: Int? = null, val filterType: String? = null, val roastLevel: String? = null, val restDays: Int? = null,
    val waterType: String? = null, val waterBrand: String? = null, val waterTdsPpm: Int? = null, val beverageTds: Double? = null,
    val milkDrink: String? = null, val milkType: String? = null, val milkGrams: Double? = null, val sourceId: String? = null,
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
        steps = steps.map { s -> BrewStep(StepKind.entries.firstOrNull { it.name.equals(s.kind, ignoreCase = true) } ?: StepKind.Wait, s.addedWaterGrams, s.seconds, s.note, s.pourStyle, s.pourSpeed, s.tempC, s.addition, s.additionTemp) },
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
    @Serializable data class Log(val recipeId: String? = null, val recipeTitle: String? = null, val doseGrams: Double? = null, val waterGrams: Double? = null, val brewSeconds: Int? = null, val taste: String? = null)
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


/** A recipe being written (iOS RecipeDraft): every helper returns an updated copy. */
data class RecipeDraft(
    val clientId: String = java.util.UUID.randomUUID().toString(),
    val lotSlug: String? = null,
    val lotName: String? = null,
    val title: String = "",
    val methodCode: String = "V60",
    val iced: Boolean = false,
    val doseGrams: Double? = 15.0,
    val hotWaterGrams: Double? = 250.0,
    val iceGrams: Double? = null,
    val yieldGrams: Double? = null,
    val waterTempC: Double? = 96.0,
    val grinder: String = "",
    val grindSetting: String = "",
    val grinderModel: String? = null,
    val grindLevel: GrindLevel? = null,
    val grindMicrons: Int? = null,
    val brewer: String = "",
    val filterType: FilterType? = null,
    val roastLevel: RoastLevel? = null,
    val restDays: Int? = null,
    val waterType: WaterType? = null,
    val waterBrand: String = "",
    val waterTdsPpm: Int? = null,
    val beverageTds: Double? = null,
    val milkDrink: MilkDrink? = null,
    val milkType: MilkType? = null,
    val milkGrams: Double? = null,
    val notes: String = "",
    val steps: List<BrewStep> = pourOverSteps(250.0),
    val sourceId: String? = null,
    val publish: Boolean = false,
    /** The server state when editing; null for a new recipe. */
    val existingState: String? = null,
) {
    val isEspresso: Boolean get() = methodCode == "ESPRESSO"
    val isNew: Boolean get() = existingState == null
    val pouredGrams: Double get() = steps.filter { it.kind.addsWater }.sumOf { it.addedWaterGrams ?: 0.0 }

    /** Water per gram of coffee (espresso: yield per gram). */
    val ratio: Double? get() {
        val dose = doseGrams?.takeIf { it > 0 } ?: return null
        val water = if (isEspresso) yieldGrams else hotWaterGrams?.let { it + (iceGrams ?: 0.0) }
        return water?.takeIf { it > 0 }?.let { it / dose }
    }
    /** Final beverage weight shown on the card. */
    val outputGrams: Double? get() = if (isEspresso) yieldGrams else hotWaterGrams?.let { it + if (iced) iceGrams ?: 0.0 else 0.0 }
    val totalSeconds: Int get() = steps.sumOf { it.seconds ?: 0 }
    /** Running water total after each step. */
    val cumulativeWater: List<Double> get() {
        var run = 0.0
        return steps.map { if (it.kind.addsWater) run += it.addedWaterGrams ?: 0.0; run }
    }
    /** Seconds from the start at which each step begins. */
    val startTimes: List<Int> get() {
        var run = 0
        return steps.map { s -> run.also { run += s.seconds ?: 0 } }
    }

    /** Sets the ratio from the dose (pour-over: hot water, espresso: yield) and rescales pours. */
    fun applyRatio(r: Double): RecipeDraft {
        val dose = doseGrams?.takeIf { it > 0 } ?: return this
        val total = Math.round(dose * r).toDouble()
        return if (isEspresso) copy(yieldGrams = total) else setHotWater(total - if (iced) iceGrams ?: 0.0 else 0.0)
    }

    /** Changes the hot water and scales the water steps to still add up; the last pour absorbs rounding. */
    fun setHotWater(newTotalRaw: Double): RecipeDraft {
        val newTotal = maxOf(newTotalRaw, 0.0)
        val idx = steps.indices.filter { steps[it].kind.addsWater && steps[it].addedWaterGrams != null }
        val oldTotal = idx.sumOf { steps[it].addedWaterGrams ?: 0.0 }
        if (idx.isEmpty() || oldTotal <= 0) return copy(hotWaterGrams = newTotal)
        val list = steps.toMutableList()
        var assigned = 0.0
        for (i in idx.dropLast(1)) {
            val scaled = Math.round((list[i].addedWaterGrams ?: 0.0) * newTotal / oldTotal).toDouble()
            list[i] = list[i].copy(addedWaterGrams = scaled); assigned += scaled
        }
        list[idx.last()] = list[idx.last()].copy(addedWaterGrams = newTotal - assigned)
        return copy(hotWaterGrams = newTotal, steps = list)
    }

    /** Changes the dose and keeps the strength: water (or the shot), ice and pours scale with it. */
    fun setDose(gramsRaw: Double): RecipeDraft {
        val grams = Math.round(gramsRaw * 10) / 10.0
        if (grams <= 0) return this
        val r = ratio
        val old = doseGrams
        val ice = if (old != null && old > 0 && iceGrams != null) Math.round(iceGrams * grams / old).toDouble() else iceGrams
        val next = copy(doseGrams = grams, iceGrams = ice)
        return if (r != null) next.applyRatio(r) else next
    }

    /** Hot or iced. Iced pour-over without a recipe of its own melts 40% of the water as ice. */
    fun setTemperature(toIced: Boolean): RecipeDraft {
        if (isEspresso) return copy(iced = false)
        val r = ratio
        var next = copy(iced = toIced)
        if (toIced && iceGrams == null) next = next.copy(iceGrams = Math.round((hotWaterGrams ?: 250.0) * 0.4).toDouble())
        if (!toIced) next = next.copy(iceGrams = null)
        return if (r != null) next.applyRatio(r) else next
    }

    /** Loads a barista recipe's numbers; the name, coffee and notes stay. */
    fun apply(preset: BrewPreset): RecipeDraft = copy(
        methodCode = preset.methodCode, iced = preset.iced, doseGrams = preset.doseGrams, hotWaterGrams = preset.hotWaterGrams,
        iceGrams = preset.iceGrams, yieldGrams = preset.yieldGrams, waterTempC = preset.waterTempC,
        grindLevel = RecipeWizard.presetGrind[preset.id] ?: grindLevel, grindMicrons = null, brewer = preset.brewer,
        filterType = RecipeWizard.presetFilter[preset.id], steps = preset.steps,
    )

    /** Starts over from the tool's barista recipe for this temperature. */
    fun start(method: String, toIced: Boolean): RecipeDraft {
        val list = BrewPreset.presets(method)
        val preset = list.firstOrNull { it.iced == toIced } ?: list.firstOrNull()
        val base = if (preset != null) apply(preset) else copy(methodCode = method)
        return if (base.iced != toIced) base.setTemperature(toIced) else base
    }

    /** Moves the start of step `index`; the boundary with the previous step moves, later starts stay. */
    fun setStart(index: Int, seconds: Int): RecipeDraft {
        if (index <= 0 || index !in steps.indices) return this
        val starts = startTimes
        val end = if (index + 1 < steps.size) starts[index + 1] else totalSeconds
        val clamped = seconds.coerceIn(starts[index - 1], end)
        val list = steps.toMutableList()
        list[index - 1] = list[index - 1].copy(seconds = clamped - starts[index - 1])
        list[index] = list[index].copy(seconds = end - clamped)
        return copy(steps = list)
    }

    /** The scale reading to pour to by the end of water step `index`; the next water step absorbs the difference. */
    fun setPourTo(index: Int, grams: Double): RecipeDraft {
        if (index !in steps.indices || !steps[index].kind.addsWater) return this
        val cum = cumulativeWater
        val before = cum[index] - (steps[index].addedWaterGrams ?: 0.0)
        val newAdded = maxOf(grams - before, 0.0)
        val delta = newAdded - (steps[index].addedWaterGrams ?: 0.0)
        val list = steps.toMutableList()
        list[index] = list[index].copy(addedWaterGrams = newAdded)
        val next = steps.indices.firstOrNull { it > index && steps[it].kind.addsWater }
        return if (next != null) {
            list[next] = list[next].copy(addedWaterGrams = (list[next].addedWaterGrams ?: 0.0) - delta); copy(steps = list)
        } else if (!isEspresso) copy(steps = list, hotWaterGrams = (hotWaterGrams ?: 0.0) + delta) else copy(steps = list)
    }

    /** When the brew ends; moves only the last step's length. */
    fun setFinish(seconds: Int): RecipeDraft {
        val last = steps.indices.lastOrNull() ?: return this
        val list = steps.toMutableList()
        list[last] = list[last].copy(seconds = maxOf(seconds - startTimes[last], 0))
        return copy(steps = list)
    }

    /** After steps change, the hot water follows the pours (not espresso). */
    fun syncHotWater(force: Boolean = false): RecipeDraft {
        if (isEspresso) return this
        val poured = cumulativeWater.lastOrNull() ?: 0.0
        return if (force || poured > 0) copy(hotWaterGrams = poured) else this
    }

    /** Picks a grinder and click setting; fills microns and level when the grinder publishes its step. */
    fun setGrind(grinderId: String?, settingText: String): RecipeDraft {
        val g = Grinder.all.firstOrNull { it.id == grinderId }
        val clicks = settingText.replace(',', '.').toDoubleOrNull()
        val microns = if (g != null && clicks != null) g.microns(clicks) else null
        return copy(grinderModel = grinderId, grinder = g?.name ?: grinder, grindSetting = settingText, grindMicrons = microns,
            grindLevel = microns?.let { GrindLevel.of(it) } ?: grindLevel)
    }

    fun setMilkDrink(drink: MilkDrink?): RecipeDraft =
        if (drink != null) copy(milkDrink = drink, milkGrams = drink.typicalMilkGrams) else copy(milkDrink = null, milkGrams = null, milkType = null)

    /** A name for an untitled recipe: the tool, iced if so, and the coffee. */
    val suggestedTitle: String get() {
        var first = if (methodCode == "V60") "V60" else Methods.nameAr(methodCode)
        if (iced) first += " مثلج"
        return listOfNotNull(first, lotName).joinToString(" · ")
    }

    /** Estimated beverage weight: the shot, or water minus what the grounds hold (≈2 g per g), ice included. */
    val beverageGrams: Double? get() {
        if (isEspresso) return yieldGrams
        val hot = hotWaterGrams ?: return null; val dose = doseGrams ?: return null
        return (hot - dose * 2.0 + if (iced) iceGrams ?: 0.0 else 0.0).takeIf { it > 0 }
    }
    /** Extraction yield (%) = TDS% × beverage ÷ dose. */
    val extractionYield: Double? get() {
        val tds = beverageTds ?: return null; val bev = beverageGrams ?: return null; val dose = doseGrams?.takeIf { it > 0 } ?: return null
        return tds * bev / dose
    }

    /** Why it can't be published yet (the server's own rules); drafts save anyway. */
    fun problemsAr(): List<String> = buildList {
        if (title.isBlank()) add("اكتب اسم للوصفة")
        if (steps.any { it.kind.addsWater && (it.addedWaterGrams ?: 1.0) <= 0 }) add("كمية الماء في الصبّات لازم تكون أكثر من صفر")
        if (isEspresso) {
            if ((doseGrams ?: 0.0) <= 0) add("اكتب وزن البن")
            if ((yieldGrams ?: 0.0) <= 0) add("اكتب وزن الشوت")
        } else {
            val water = hotWaterGrams ?: 0.0
            if (water <= 0) add("اكتب كمية الماء")
            else if (Math.abs(pouredGrams - water) > 1) add("مجموع الصبّات (${Methods.grams(pouredGrams)}غ) لازم يساوي الماء (${Methods.grams(water)}غ)")
            if (steps.isEmpty()) add("أضف خطوة وحدة على الأقل")
        }
        if (grindLevel == null && grindSetting.isBlank()) add("اختر درجة الطحن")
    }
    val pourSumMismatch: Boolean get() = !isEspresso && hotWaterGrams != null && Math.abs(pouredGrams - (hotWaterGrams ?: 0.0)) > 1

    fun body(): kotlinx.serialization.json.JsonObject = kotlinx.serialization.json.buildJsonObject {
        val N = kotlinx.serialization.json.JsonNull
        fun prim(v: Any?): kotlinx.serialization.json.JsonElement = when (v) {
            null -> N; is Number -> kotlinx.serialization.json.JsonPrimitive(v); is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
            else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
        }
        put("clientId", prim(clientId)); put("lotSlug", prim(lotSlug))
        put("title", prim(title.trim().take(80))); put("methodCode", prim(methodCode)); put("temperature", prim(if (iced) "iced" else "hot"))
        put("doseGrams", prim(doseGrams)); put("hotWaterGrams", prim(if (isEspresso) null else hotWaterGrams))
        put("iceGrams", prim(if (iced) iceGrams else null)); put("yieldGrams", prim(if (isEspresso) yieldGrams else null))
        put("waterTempC", prim(waterTempC))
        put("grinder", prim(grinder.trim().take(60))); put("grindSetting", prim(grindSetting.trim().take(60)))
        put("grinderModel", prim(grinderModel)); put("grindLevel", prim(grindLevel?.code)); put("grindMicrons", prim(grindMicrons))
        put("brewer", prim(brewer.trim().take(40))); put("filterType", prim(filterType?.code))
        put("roastLevel", prim(roastLevel?.code)); put("restDays", prim(restDays))
        put("waterType", prim(waterType?.code)); put("waterBrand", prim(waterBrand.trim().take(60))); put("waterTdsPpm", prim(waterTdsPpm))
        put("beverageTds", prim(beverageTds))
        put("milkDrink", prim(if (isEspresso) milkDrink?.code else null))
        put("milkType", prim(if (isEspresso && milkDrink != null) milkType?.code else null))
        put("milkGrams", prim(if (isEspresso && milkDrink != null) milkGrams else null))
        put("notes", prim(notes.take(2000)))
        put("steps", kotlinx.serialization.json.JsonArray(steps.take(20).map { st ->
            kotlinx.serialization.json.buildJsonObject {
                put("kind", prim(st.kind.name.lowercase()))
                val isAddition = st.kind == StepKind.Serve && st.addition != null
                put("addedWaterGrams", prim(if (st.kind.addsWater || isAddition) st.addedWaterGrams else null))
                put("seconds", prim(st.seconds)); put("note", prim(st.note.take(300)))
                put("pourStyle", prim(if (st.kind.addsWater) st.pourStyle else null)); put("pourSpeed", prim(if (st.kind.addsWater) st.pourSpeed else null))
                put("tempC", prim(if (st.kind.addsWater) st.tempC else null))
                put("addition", prim(if (isAddition) st.addition else null))
                put("additionTemp", prim(if (isAddition && st.addition == "dilute") st.additionTemp else null))
            }
        }))
        put("sourceId", prim(sourceId)); put("publish", prim(publish))
    }

    companion object {
        fun pourOverSteps(total: Double): List<BrewStep> {
            val bloom = Math.round(total / 5).toDouble()
            val first = Math.round((total - bloom) / 2).toDouble()
            return listOf(
                BrewStep(StepKind.Bloom, bloom, 45, "صب برفق لترطيب البن ثم اتركه يتنفس."),
                BrewStep(StepKind.Pour, first, 40, "صب بثبات حتى الوزن المستهدف."),
                BrewStep(StepKind.Pour, total - bloom - first, 40, "صب حتى الوزن النهائي ثم اترك الماء ينزل."),
            )
        }
        fun from(preset: BrewPreset, lotSlug: String?, lotName: String? = null) = RecipeDraft(lotSlug = lotSlug, lotName = lotName).apply(preset)
        fun from(r: RemoteRecipe, copy: Boolean) = RecipeDraft(
            clientId = if (copy) java.util.UUID.randomUUID().toString() else r.clientId.ifBlank { java.util.UUID.randomUUID().toString() },
            lotSlug = r.lot?.slug, lotName = r.lot?.nameAr, title = if (copy) "" else r.title, methodCode = r.methodCode, iced = r.temperature == "iced",
            doseGrams = r.doseGrams, hotWaterGrams = r.hotWaterGrams, iceGrams = r.iceGrams, yieldGrams = r.yieldGrams, waterTempC = r.waterTempC,
            grinder = r.grinder, grindSetting = r.grindSetting, grinderModel = r.grinderModel, grindLevel = GrindLevel.byCode(r.grindLevel),
            grindMicrons = r.grindMicrons, brewer = r.brewer, filterType = FilterType.byCode(r.filterType), roastLevel = RoastLevel.byCode(r.roastLevel),
            restDays = r.restDays, waterType = WaterType.byCode(r.waterType), waterBrand = r.waterBrand.orEmpty(), waterTdsPpm = r.waterTdsPpm,
            beverageTds = r.beverageTds, milkDrink = MilkDrink.byCode(r.milkDrink), milkType = MilkType.byCode(r.milkType), milkGrams = r.milkGrams,
            notes = if (copy) "" else r.notes, steps = r.toPreset().steps,
            sourceId = if (copy) r.id else r.sourceId, publish = !copy && r.state == "published", existingState = if (copy) null else r.state)
    }
}
