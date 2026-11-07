package com.bunnlist.core

// The recipe editor's three steps and the choices inside them (same codes as iOS and the server).

enum class RecipeWizardStep(val titleAr: String, val subtitleAr: String) {
    Tool("وش بتحضّر؟", "اختر الأداة، ونجهّز لك وصفة باريستا تعدّل عليها."),
    Tune("اضبطها على ذوقك", "غيّر البن، والماء والصبات تتعدل معه."),
    Finish("جاهزة", "سمّها إذا تبي، أو خلّ الاسم المقترح."),
}

private fun camel(name: String) = name.replaceFirstChar { it.lowercase() }

val GrindLevel.code: String get() = camel(name)
fun GrindLevel.Companion.byCode(code: String?): GrindLevel? = GrindLevel.entries.firstOrNull { it.code == code }

enum class FilterType(val nameAr: String) {
    PaperWhite("ورق أبيض"), PaperBrown("ورق بني"), Abaca("أباكا"), Cloth("قماش"), Metal("معدن");
    val code get() = camel(name)
    companion object { fun byCode(c: String?) = entries.firstOrNull { it.code == c } }
}

enum class RoastLevel(val nameAr: String, val suggestedTempC: Double) {
    Light("فاتح", 95.0), Medium("متوسط", 92.0), Dark("غامق", 88.0);
    val code get() = camel(name)
    companion object { fun byCode(c: String?) = entries.firstOrNull { it.code == c } }
}

enum class WaterType(val nameAr: String) {
    Bottled("معبأ"), Filtered("مفلتر"), MineralRecipe("وصفة معادن"), Tap("صنبور");
    val code get() = camel(name)
    companion object { fun byCode(c: String?) = entries.firstOrNull { it.code == c } }
}

enum class MilkDrink(val nameAr: String, val typicalMilkGrams: Double, val textureAr: String) {
    Macchiato("ماكياتو", 15.0, "نقطة رغوة فوق الشوت"), Cortado("كورتادو", 40.0, "حليب مبخّر بقليل من الرغوة"),
    FlatWhite("فلات وايت", 120.0, "رغوة ناعمة رقيقة (ميكروفوم) بسماكة ~١ سم"), Cappuccino("كابتشينو", 150.0, "رغوة أكثف بسماكة ~١٫٥–٢ سم"),
    Latte("لاتيه", 220.0, "رغوة ناعمة رقيقة ~١ سم");
    val code get() = camel(name)
    companion object { fun byCode(c: String?) = entries.firstOrNull { it.code == c } }
}

enum class MilkType(val nameAr: String) {
    Whole("كامل الدسم"), Skim("قليل الدسم"), LactoseFree("خالٍ من اللاكتوز"), Oat("شوفان"), Almond("لوز"), Soy("صويا");
    val code get() = camel(name)
    companion object { fun byCode(c: String?) = entries.firstOrNull { it.code == c } }
}

enum class PourStyle(val nameAr: String) {
    Center("مركزي"), Circular("دائري"), Spiral("حلزوني");
    val code get() = camel(name)
    companion object { fun byCode(c: String?) = entries.firstOrNull { it.code == c } }
}

enum class PourSpeed(val nameAr: String) {
    Slow("بطيء"), Medium("متوسط"), Fast("سريع");
    val code get() = camel(name)
    companion object { fun byCode(c: String?) = entries.firstOrNull { it.code == c } }
}

object RecipeWizard {
    data class Tool(val code: String, val blurb: String)
    val tools = listOf(
        Tool("V60", "مقطّرة صافية"), Tool("ESPRESSO", "شوت أو بالحليب"), Tool("AEROPRESS", "سريعة ومركّزة"), Tool("CHEMEX", "نظيفة وخفيفة"),
        Tool("FRENCH_PRESS", "قوام ممتلئ"), Tool("COLD_BREW", "نقع بارد لساعات"), Tool("ARABIC", "بالدلة والهيل"), Tool("OTHER", "طريقتك الخاصة"),
    )

    /** The grind level and filter of each barista recipe (as on iOS). */
    val presetGrind = mapOf(
        "v60-classic" to GrindLevel.MediumFine, "v60-46" to GrindLevel.Coarse, "v60-iced" to GrindLevel.MediumFine, "chemex-classic" to GrindLevel.MediumCoarse,
        "aeropress-classic" to GrindLevel.MediumFine, "french-clean" to GrindLevel.Coarse, "espresso-classic" to GrindLevel.ExtraFine,
        "cold-brew" to GrindLevel.ExtraCoarse, "arabic-saudi" to GrindLevel.Medium,
    )
    val presetFilter = mapOf(
        "v60-classic" to FilterType.PaperWhite, "v60-46" to FilterType.PaperWhite, "v60-iced" to FilterType.PaperWhite, "chemex-classic" to FilterType.PaperWhite,
        "aeropress-classic" to FilterType.PaperWhite, "french-clean" to FilterType.Metal, "cold-brew" to FilterType.Cloth,
    )

    fun brewers(method: String): List<String> = when (method) {
        "V60" -> listOf("V60 01", "V60 02", "Origami", "Kalita Wave 185", "Orea", "April")
        "CHEMEX" -> listOf("Chemex 6 أكواب", "Chemex 3 أكواب")
        "AEROPRESS" -> listOf("AeroPress", "AeroPress Go", "AeroPress XL")
        "FRENCH_PRESS" -> listOf("Bodum", "Espro P7")
        "ESPRESSO" -> listOf("سلة ١٨غ", "سلة ٢٠غ", "VST 18", "VST 20")
        "COLD_BREW" -> listOf("Hario Mizudashi", "Toddy", "مرطبان")
        "ARABIC" -> listOf("دلة", "دلة كهربائية")
        else -> listOf("Clever", "Hario Switch", "موكا")
    }

    fun typicalRatio(m: String): ClosedFloatingPointRange<Double> = when (m) {
        "ESPRESSO" -> 1.5..3.0; "COLD_BREW" -> 4.0..12.0; "ARABIC" -> 20.0..45.0; "AEROPRESS" -> 12.0..18.0; else -> 14.0..18.0
    }
    fun typicalSeconds(m: String): IntRange? = when (m) {
        "ESPRESSO" -> 20..35; "V60" -> 120..240; "CHEMEX" -> 210..330; "AEROPRESS" -> 90..240; "FRENCH_PRESS" -> 240..720; "ARABIC" -> 600..1500; else -> null
    }
    fun typicalTds(m: String): ClosedFloatingPointRange<Double> = if (m == "ESPRESSO") 8.0..12.0 else 1.15..1.45

    private fun n(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)
    private fun t(s: Int) = String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60)

    /** Non-blocking barista tips about a draft (iOS BrewAdvisor.tips). */
    fun tips(d: RecipeDraft): List<String> = buildList {
        val m = d.methodCode
        d.waterTempC?.takeIf { m != "COLD_BREW" && m != "ARABIC" }?.let {
            if (it < 88) add("الحرارة منخفضة: للتحميص الفاتح جرّب ٩٢–٩٦°م لاستخلاص أحلى.")
            else if (it > 98) add("الحرارة عالية: قد تبرز المرارة، خصوصًا مع التحميص الغامق.")
        }
        d.ratio?.let { r ->
            val ty = typicalRatio(m)
            if (r < ty.start - 0.05) add("النسبة أقوى من المعتاد لهذه الطريقة (1:${n(ty.start)}–1:${n(ty.endInclusive)}).")
            else if (r > ty.endInclusive + 0.05) add("النسبة أخف من المعتاد لهذه الطريقة (1:${n(ty.start)}–1:${n(ty.endInclusive)}).")
        }
        d.steps.firstOrNull()?.takeIf { it.kind == StepKind.Bloom }?.let { b ->
            if ((b.seconds ?: 99) < 30) add("اترك التفتيح ٣٠–٤٥ ثانية ليخرج ثاني أكسيد الكربون ويتشبع البن بالتساوي.")
            val w = b.addedWaterGrams; val dose = d.doseGrams
            if (w != null && dose != null && w < dose * 2 - 0.5) add("ماء التفتيح يكون عادة ضعفي إلى ثلاثة أضعاف وزن البن.")
        }
        typicalSeconds(m)?.takeIf { d.totalSeconds > 0 }?.let { ty ->
            if (d.totalSeconds < ty.first) add("الوقت أقصر من المعتاد (${t(ty.first)}–${t(ty.last)}): جرّب طحنة أنعم.")
            else if (d.totalSeconds > ty.last) add("الوقت أطول من المعتاد (${t(ty.first)}–${t(ty.last)}): جرّب طحنة أخشن.")
        }
        if (d.iced && m != "COLD_BREW" && !d.isEspresso) {
            val ice = d.iceGrams; val hot = d.hotWaterGrams
            if (ice != null && hot != null && ice + hot > 0) { val share = ice / (ice + hot); if (share < 0.25 || share > 0.5) add("في الطريقة اليابانية خلّ الثلج ٣٠–٤٠٪ من الماء الكلي.") }
        }
        if (d.roastLevel == RoastLevel.Dark && m != "ARABIC" && (d.waterTempC ?: 0.0) > 93) add("للتحميص الغامق اخفض الحرارة إلى ٨٨–٩٠°م لتقليل المرارة.")
        d.restDays?.let { if (it < (if (d.isEspresso) 7 else 4)) add("البن طازج جدًا: غاز ثاني أكسيد الكربون يعيق الاستخلاص. اتركه يرتاح أسبوعًا على الأقل.") }
        d.waterTdsPpm?.let { if (it < 75) add("الماء خفيف جدًا بالمعادن (أقل من ٧٥ ppm): الكوب يطلع باهت.") else if (it > 150) add("الماء ثقيل بالمعادن (أكثر من ١٥٠ ppm): يخفي الحموضة والحلاوة.") }
        d.extractionYield?.takeIf { m != "COLD_BREW" }?.let { if (it < 18) add("الاستخلاص أقل من ١٨٪: نعّم الطحنة أو ارفع الحرارة.") else if (it > 22) add("الاستخلاص أكثر من ٢٢٪: خشّن الطحنة أو اخفض الحرارة.") }
    }
}
