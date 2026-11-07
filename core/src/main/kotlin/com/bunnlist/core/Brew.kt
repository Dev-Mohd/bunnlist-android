package com.bunnlist.core

import kotlinx.serialization.Serializable

enum class StepKind(val nameAr: String) {
    Bloom("تفتيح"), Pour("صب"), Wait("انتظار"), Swirl("تحريك القمع"), Stir("تقليب"), Press("ضغط"), Serve("تقديم");
    /** Only bloom and pour add water. */
    val addsWater: Boolean get() = this == Bloom || this == Pour
}

@Serializable
data class BrewStep(val kind: StepKind, val addedWaterGrams: Double? = null, val seconds: Int? = null, val note: String = "",
                    val pourStyle: String? = null, val pourSpeed: String? = null)

/** A proven starting recipe that ships with the app (same as iOS). */
data class BrewPreset(
    val id: String, val nameAr: String, val summaryAr: String, val methodCode: String, val iced: Boolean,
    val doseGrams: Double, val hotWaterGrams: Double?, val iceGrams: Double?, val yieldGrams: Double?,
    val waterTempC: Double, val grindAr: String, val brewer: String, val steps: List<BrewStep>,
    /** Where the recipe came from: "base" (ships with the app), "community" or "mine". */
    val source: String = "base",
    val recipeId: String? = null,
    val author: String? = null,
    val likes: Int = 0,
) {
    val sourceAr: String get() = when (source) {
        "mine" -> "وصفتك"
        "community" -> if (likes > 0) "من ${author ?: "الناس"} · $likes إعجاب" else "من ${author ?: "الناس"}"
        else -> "وصفة بداية من BunnList"
    }
    /** Hours-long methods get a checklist, not a countdown. */
    val usesTimer: Boolean get() = methodCode != "COLD_BREW"
    val totalSeconds: Int get() = steps.sumOf { it.seconds ?: 0 }

    /** "1:16.7", from dose and total water (hot + ice), or espresso's yield. */
    val ratioText: String?
        get() {
            val water = yieldGrams ?: ((hotWaterGrams ?: 0.0) + (iceGrams ?: 0.0))
            if (doseGrams <= 0 || water <= 0) return null
            return "1:" + Methods.grams(Math.round(water / doseGrams * 10) / 10.0)
        }

    companion object {
        /** Methods «حضّر» offers, in the order shown. */
        val methods = listOf("V60", "CHEMEX", "AEROPRESS", "FRENCH_PRESS", "ESPRESSO", "COLD_BREW", "ARABIC")
        fun presets(methodCode: String) = all.filter { it.methodCode == methodCode }

        private fun s(kind: StepKind, water: Double? = null, sec: Int? = null, note: String) = BrewStep(kind, water, sec, note)
        private val B = StepKind.Bloom; private val P = StepKind.Pour; private val W = StepKind.Wait
        private val SW = StepKind.Swirl; private val ST = StepKind.Stir; private val SV = StepKind.Serve

        val all: List<BrewPreset> = listOf(
            BrewPreset("v60-classic", "V60 الكلاسيكية", "متوازنة وواضحة النكهات · ١:١٦٫٧ · ٢:٥٥", "V60", false, 15.0, 250.0, null, null, 94.0, "متوسط إلى ناعم", "V60 02", listOf(
                s(B, 45.0, 45, "صب ٤٥غ بحركة دائرية حتى يتبلل كل البن، ثم حرّك القمع حركة دائرية خفيفة."),
                s(P, 105.0, 30, "صب من المنتصف للخارج بثبات حتى ١٥٠غ."),
                s(P, 100.0, 30, "صب ببطء حتى ٢٥٠غ وحافظ على مستوى الماء."),
                s(SW, null, 10, "حرّك القمع بلطف ليستوي سرير البن."),
                s(W, null, 60, "اترك الماء ينزل كاملًا. الهدف ينتهي بين ٢:٣٠ و٣:٠٠."))),
            BrewPreset("v60-46", "طريقة ٤:٦", "طريقة تيتسو كاسويا · خمس صبات · ١:١٥ · ٣:٣٠", "V60", false, 20.0, 300.0, null, null, 92.0, "خشن", "V60 02", listOf(
                s(B, 50.0, 45, "أول ٤٠٪ تتحكم بالحلاوة والحموضة: صبة أولى أصغر = حموضة أوضح."),
                s(P, 70.0, 45, "أكمل حتى ١٢٠غ."),
                s(P, 60.0, 45, "الـ٦٠٪ الأخيرة تتحكم بالقوة: صبات أكثر = كوب أقوى."),
                s(P, 60.0, 45, "حتى ٢٤٠غ."),
                s(P, 60.0, 30, "حتى ٣٠٠غ، ثم اترك الماء ينزل. الهدف ٣:٣٠."))),
            BrewPreset("v60-iced", "المثلجة اليابانية", "صب على الثلج · ثلج ٣٧٪ · ١:١٦", "V60", true, 20.0, 200.0, 120.0, null, 94.0, "متوسط إلى ناعم (أنعم قليلًا من الساخنة)", "V60 02", listOf(
                s(B, 50.0, 45, "الثلج في الإبريق تحت القمع. صب ٥٠غ للتفتيح."),
                s(P, 70.0, 30, "حتى ١٢٠غ."),
                s(P, 80.0, 30, "حتى ٢٠٠غ."),
                s(SW, null, 10, "حرّك القمع بلطف."),
                s(W, null, 45, "اترك الماء ينزل."),
                s(SV, null, 10, "حرّك الإبريق حتى يذوب الثلج وتبرد القهوة، وقدّمها على ثلج جديد."))),
            BrewPreset("chemex-classic", "كيمكس للمشاركة", "كوبان صافيان · ١:١٦٫٧ · ٤:٠٠", "CHEMEX", false, 30.0, 500.0, null, null, 94.0, "متوسط إلى خشن", "Chemex 6 أكواب", listOf(
                s(B, 60.0, 45, "اغسل الورق جيدًا بالماء الحار قبل البدء. صب ٦٠غ وحرّك بلطف."),
                s(P, 190.0, 45, "حتى ٢٥٠غ بحركة دائرية."),
                s(P, 250.0, 60, "حتى ٥٠٠غ، لا تصب على جدار الورق مباشرة."),
                s(W, null, 90, "اترك الماء ينزل. الهدف بين ٤:٠٠ و٤:٣٠."))),
            BrewPreset("aeropress-classic", "إيروبرس اليومية", "نظيفة وحلوة · ١:١٦ · ٢:٤٠", "AEROPRESS", false, 15.0, 240.0, null, null, 90.0, "متوسط إلى ناعم", "AeroPress", listOf(
                s(B, 40.0, 30, "صب ٤٠غ وحرّك ثلاث مرات."),
                s(P, 200.0, 15, "أكمل حتى ٢٤٠غ."),
                s(W, null, 75, "ركّب المكبس قليلًا ليمنع التنقيط، واتركها تنقع."),
                s(SW, null, 10, "حرّك الجهاز بلطف قبل الضغط."),
                s(SV, null, 30, "اضغط ببطء وثبات ٣٠ ثانية، وتوقف عند سماع الهسهسة."))),
            BrewPreset("french-clean", "فرنش برس النظيفة", "قوام غني بدون رواسب · ١:١٦٫٧ · ١٠:٠٠", "FRENCH_PRESS", false, 30.0, 500.0, null, null, 95.0, "متوسط إلى خشن", "Bodum", listOf(
                s(P, 500.0, 15, "صب كل الماء بسرعة وتأكد أن البن تبلل كله."),
                s(W, null, 240, "اتركها ٤ دقائق بدون تحريك."),
                s(SW, null, 30, "اكسر الطبقة العلوية بالملعقة، وأزل الرغوة والحبيبات الطافية."),
                s(W, null, 300, "انتظر ٥ دقائق لتترسب الحبيبات، فيصير الكوب أنظف."),
                s(SV, null, 15, "أنزل المكبس إلى السطح فقط بدون ضغط، واسكب ببطء."))),
            BrewPreset("espresso-classic", "إسبريسو ١:٢", "١٨غ ← ٣٦غ في ٢٥–٣٠ ثانية", "ESPRESSO", false, 18.0, null, null, 36.0, 93.0, "ناعم", "سلة ١٨غ", listOf(
                s(SV, null, 28, "وزّع البن ودكّه بثبات. الهدف ٣٦غ في ٢٥–٣٠ ثانية: أسرع = نعّم الطحنة، أبطأ = خشّنها."))),
            BrewPreset("cold-brew", "كولد برو مركّز", "١:١٠ · نقع ١٢–١٦ ساعة · يُخفف ١:١", "COLD_BREW", true, 100.0, 1000.0, null, null, 20.0, "خشن جدًا", "مرطبان", listOf(
                s(P, 1000.0, 60, "صب ماء بحرارة الغرفة وحرّك حتى يتبلل كل البن."),
                s(W, null, null, "غطّها وانقعها ١٢–١٦ ساعة في الثلاجة."),
                s(SV, null, null, "صفّها بفلتر ورقي، وخففها بالماء أو الحليب ١:١ وقدّمها على ثلج."))),
            BrewPreset("arabic-saudi", "قهوة سعودية بالهيل", "دلة لتر · ٣٠غ بن شقراء · ١٠غ هيل · ١٥ دقيقة", "ARABIC", false, 30.0, 1000.0, null, null, 100.0, "متوسط", "دلة", listOf(
                s(P, 1000.0, 180, "صب لتر ماء في الدلة على نار قوية لين يغلي."),
                s(ST, null, 20, "أضف البن (تحميص فاتح) وحرّك، وانتبه لا تفور برا الدلة."),
                s(W, null, 600, "خفّف النار وخلّها تغلي على هدوء ١٠ دقايق. إذا ارتفعت الرغوة ارفعها عن النار شوي."),
                s(ST, null, 20, "أضف الهيل المطحون، ورشّة زعفران أو قرنفل إذا تحب."),
                s(W, null, 120, "طفّ النار وخلّها تركد دقيقتين عشان ينزل البن تحت."),
                s(SV, null, 30, "صبّها بهدوء في الترمس أو المصفاة، وقدّمها بالفناجين مع التمر."))),
        )
    }
}

/** Where a guided brew is at a given elapsed time. Untimed steps take no time. */
class BrewTimeline(val steps: List<BrewStep>) {
    data class Position(val stepIndex: Int, val secondsLeftInStep: Int, val pourTo: Double?, val isFinished: Boolean)

    private val starts: List<Int>
    private val cumulative: List<Double>
    val totalSeconds: Int

    init {
        var running = 0; var water = 0.0
        val st = ArrayList<Int>(); val cu = ArrayList<Double>()
        for (step in steps) {
            st += running
            running += step.seconds ?: 0
            if (step.kind.addsWater) step.addedWaterGrams?.let { water += it }
            cu += water
        }
        starts = st; cumulative = cu; totalSeconds = running
    }

    fun position(elapsed: Int): Position {
        if (steps.isEmpty()) return Position(0, 0, null, true)
        if (elapsed >= totalSeconds) return Position(steps.size - 1, 0, null, true)
        var index = 0
        for (i in steps.indices) if (starts[i] <= elapsed && elapsed < starts[i] + (steps[i].seconds ?: 0)) index = i
        val left = starts[index] + (steps[index].seconds ?: 0) - elapsed
        return Position(index, left, if (steps[index].kind.addsWater) cumulative[index] else null, false)
    }

    fun start(index: Int): Int = starts.getOrElse(index) { totalSeconds }
}
