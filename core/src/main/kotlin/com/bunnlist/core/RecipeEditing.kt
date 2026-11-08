package com.bunnlist.core

// The recipe editor's rules (same as iOS), as pure copies of a draft:
// - A pour's amount is what it ADDS; «حتى» (the scale reading) is computed from the adds; bloom counts once.
// - Pours and bloom have a start time («يبدأ»); wait/stir/swirl/press have a duration («المدة»). Every step stores
//   its duration, so moving a step carries its duration and every start is recomputed from the order.
// - The expected end is the last step's start plus its duration.
// - Ice and dilution water are serve steps with an `addition`; they are never brew water.
// - The recipe's water temperature may be unset («غير محددة»); a pour can override it.

/** Indices of the water steps (bloom and pours). */
val RecipeDraft.waterIndices: List<Int> get() = steps.indices.filter { steps[it].kind.addsWater }
val RecipeDraft.hasBloom: Boolean get() = steps.firstOrNull()?.kind == StepKind.Bloom
val RecipeDraft.pourCount: Int get() = steps.count { it.kind == StepKind.Pour }
/** The scale reading when step `i` begins. */
fun RecipeDraft.scaleBefore(i: Int): Double = cumulativeWater[i] - if (steps[i].kind.addsWater) steps[i].addedWaterGrams ?: 0.0 else 0.0
/** Hot water minus what the pours add: positive = water still missing, negative = over. */
val RecipeDraft.waterShortfall: Double get() = (hotWaterGrams ?: 0.0) - pouredGrams
val RecipeDraft.waterMismatch: Boolean get() = !isEspresso && hotWaterGrams != null && waterIndices.isNotEmpty() && Math.abs(waterShortfall) > 1
fun RecipeDraft.stepTemp(i: Int): Double? = steps.getOrNull(i)?.takeIf { it.kind.addsWater }?.let { it.tempC ?: waterTempC }
val RecipeDraft.iceAddedGrams: Double get() = steps.filter { it.addition == "ice" }.sumOf { it.addedWaterGrams ?: 0.0 }
val RecipeDraft.dilutionGrams: Double get() = steps.filter { it.addition == "dilute" }.sumOf { it.addedWaterGrams ?: 0.0 }

/** «جهّز الماء على 92°م», or null when the temperature is unset. Pours with their own temperature are listed after. */
val RecipeDraft.prepLineAr: String? get() = waterTempC?.let { "جهّز الماء على ${Methods.grams(it)}°م" }
val RecipeDraft.customTempsAr: String? get() {
    var pour = 0
    val custom = steps.mapNotNull { s ->
        if (s.kind == StepKind.Pour) pour++
        val label = if (s.kind == StepKind.Bloom) "التشريب" else "صبّة $pour"
        s.takeIf { it.kind.addsWater && it.tempC != null && it.tempC != waterTempC }?.let { "$label على ${Methods.grams(it.tempC!!)}°" }
    }
    return custom.takeIf { it.isNotEmpty() }?.joinToString("، ")
}

/** The simple editor: `count` pours after an optional bloom (3× the dose), the water split evenly, 30 s apart.
 *  Steps after the last pour (waits, additions) are kept; without any, a drawdown wait of 60 s ends the brew. */
fun RecipeDraft.simplePours(count: Int, bloom: Boolean): RecipeDraft {
    if (isEspresso) return this
    val n = count.coerceIn(1, 5)
    val total = hotWaterGrams ?: 250.0
    val bloomG = if (bloom) minOf(Math.round((doseGrams ?: 15.0) * 3).toDouble(), Math.floor(total / 2)) else 0.0
    val rest = total - bloomG
    val each = Math.floor(rest / n)
    val list = mutableListOf<BrewStep>()
    if (bloom) list += BrewStep(StepKind.Bloom, bloomG, 45, steps.firstOrNull { it.kind == StepKind.Bloom }?.note ?: "صب برفق لترطيب البن واتركه يتنفس.",
        tempC = steps.firstOrNull { it.kind == StepKind.Bloom }?.tempC)
    val oldPours = steps.filter { it.kind == StepKind.Pour }
    repeat(n) { i ->
        val old = oldPours.getOrNull(i)
        list += BrewStep(StepKind.Pour, if (i == n - 1) rest - each * (n - 1) else each, 30, old?.note ?: "", old?.pourStyle, old?.pourSpeed, old?.tempC)
    }
    val lastWater = waterIndices.lastOrNull()
    val tail = if (lastWater == null) emptyList() else steps.drop(lastWater + 1)
    return copy(steps = list + tail.ifEmpty { listOf(BrewStep(StepKind.Wait, null, 60, "اترك الماء ينزل.")) })
}

/** Sets how much water step `i` adds; the next pour absorbs the difference (or the total grows if it's the last). */
fun RecipeDraft.setAdded(i: Int, grams: Double): RecipeDraft =
    if (i in steps.indices && steps[i].kind.addsWater) setPourTo(i, scaleBefore(i) + maxOf(grams, 0.0)) else this

/** Moves a step; it keeps its duration, so every start after the change is recomputed from the new order. */
fun RecipeDraft.moveStep(from: Int, to: Int): RecipeDraft {
    if (from !in steps.indices || to !in steps.indices || from == to) return this
    val list = steps.toMutableList(); val s = list.removeAt(from); list.add(to, s)
    return copy(steps = list)
}

fun RecipeDraft.removeStep(i: Int): RecipeDraft = if (i in steps.indices) copy(steps = steps.toMutableList().also { it.removeAt(i) }) else this

/** «＋ صبّة»: after the last pour, 30 s later. It takes the water still missing, else half of the last pour. */
fun RecipeDraft.addPour(): RecipeDraft {
    val last = waterIndices.lastOrNull()
    val list = steps.toMutableList()
    if (last == null) {
        list.add(0, BrewStep(StepKind.Pour, hotWaterGrams ?: 0.0, 30)); return copy(steps = list)
    }
    val missing = waterShortfall
    val grams = if (missing > 0.5) missing else {
        val half = Math.floor((list[last].addedWaterGrams ?: 0.0) / 2)
        list[last] = list[last].copy(addedWaterGrams = (list[last].addedWaterGrams ?: 0.0) - half); half
    }
    list.add(last + 1, BrewStep(StepKind.Pour, grams, 30, "", tempC = null))
    return copy(steps = list)
}

/** «＋ إضافة خطوة»: wait, swirl, stir, press, ice, dilution or serve, at the end. */
enum class ExtraStep(val titleAr: String, val hintAr: String) {
    Wait("انتظار", "المدة بالثواني"), Swirl("تدوير القمع", "يستوي سرير البن"), Stir("تقليب بالملعقة", "شمال جنوب شرق غرب"),
    Press("ضغط", "إيروبرس وفرنش برس"), Ice("إضافة ثلج", "بالغرام"), Dilute("إضافة ماء للتخفيف", "الكمية والحرارة"), Serve("تقديم", "آخر خطوة");
}

fun RecipeDraft.addStep(kind: ExtraStep): RecipeDraft = copy(steps = steps + when (kind) {
    ExtraStep.Wait -> BrewStep(StepKind.Wait, null, 30)
    ExtraStep.Swirl -> BrewStep(StepKind.Swirl, null, 10)
    ExtraStep.Stir -> BrewStep(StepKind.Stir, null, 10)
    ExtraStep.Press -> BrewStep(StepKind.Press, null, 30)
    ExtraStep.Ice -> BrewStep(StepKind.Serve, 100.0, 10, addition = "ice")
    ExtraStep.Dilute -> BrewStep(StepKind.Serve, 20.0, 10, addition = "dilute", additionTemp = "room")
    ExtraStep.Serve -> BrewStep(StepKind.Serve, null, 10)
})

/** «أضف 10غ لآخر صبّة»: the last pour takes the missing (or extra) water so the steps match the target. */
fun RecipeDraft.giveShortfallToLastPour(): RecipeDraft {
    val last = waterIndices.lastOrNull() ?: return this
    val list = steps.toMutableList()
    list[last] = list[last].copy(addedWaterGrams = maxOf((list[last].addedWaterGrams ?: 0.0) + waterShortfall, 0.0))
    return copy(steps = list)
}

/** «اعتمد إجمالي 240غ»: the target becomes what the steps pour. */
fun RecipeDraft.adoptPouredTotal(): RecipeDraft = syncHotWater(force = true)

/** The step's title in the editor and review: تشريب, صبّة, انتظار, ثلج, تخفيف… */
fun BrewStep.titleAr(): String = when {
    addition == "ice" -> "ثلج"
    addition == "dilute" -> "تخفيف"
    kind == StepKind.Bloom -> "تشريب"
    kind == StepKind.Pour -> "صبّة"
    kind == StepKind.Swirl -> "تدوير"
    else -> kind.nameAr
}

val ADDITION_TEMPS = listOf("room" to "حرارة الغرفة", "hot" to "حار", "cold" to "بارد")
