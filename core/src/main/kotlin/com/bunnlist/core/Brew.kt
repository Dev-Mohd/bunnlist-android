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
    /** Where the recipe came from: "base" (ships with the app), "champion", "roaster" (this coffee's), "community" or "mine". */
    val source: String = "base",
    val recipeId: String? = null,
    val author: String? = null,
    val likes: Int = 0,
    /** A champion's title, e.g. «بطل العالم للإيروبرس 2024». */
    val creditAr: String? = null,
    /** Where a champion's recipe is published. */
    val sourceUrl: String? = null,
    /** Needs two servers or more steps than a beginner wants. */
    val advanced: Boolean = false,
) {
    val sourceAr: String get() = when (source) {
        "mine" -> "وصفتك"
        "community" -> if (likes > 0) "من ${author ?: "الناس"} · $likes إعجاب" else "من ${author ?: "الناس"}"
        "roaster" -> "من ${author ?: "المحمصة"} · وصفة المحمصة لهالقهوة"
        "champion" -> "${author ?: ""} · ${creditAr ?: ""}"
        else -> "وصفة بداية من BunnList"
    }
    /** This coffee's roaster recipe and champions' stand out in gold. */
    val isGold: Boolean get() = source == "roaster" || source == "champion"
    val isEspresso: Boolean get() = methodCode == "ESPRESSO"

    // Cups: a فنجان of Arabic coffee is about 100 g of water, a mug about 250 g.
    val cupGrams: Double get() = if (methodCode == "ARABIC") 100.0 else 250.0
    val cupNameAr: String get() = if (methodCode == "ARABIC") "فنجان" else "كوب"
    /** Espresso is pulled per shot. */
    val scalesByCups: Boolean get() = !isEspresso
    /** How many cups the recipe makes as written (at least one). */
    val baseCups: Int get() = maxOf(1, Math.round(((hotWaterGrams ?: 0.0) + (iceGrams ?: 0.0)) / cupGrams).toInt())
    val maxCups: Int get() = if (methodCode == "ARABIC") 20 else 6

    /** The recipe for `cups`: coffee, water, ice and every pour scale together, so the strength stays. Times stay. */
    fun scaled(cups: Int): BrewPreset {
        if (!scalesByCups || cups == baseCups || cups <= 0) return this
        val k = cups.toDouble() / baseCups
        fun half(v: Double) = Math.round(v * k * 2) / 2.0
        fun whole(v: Double?) = v?.let { Math.round(it * k).toDouble() }
        return copy(doseGrams = half(doseGrams), hotWaterGrams = whole(hotWaterGrams), iceGrams = whole(iceGrams), yieldGrams = whole(yieldGrams),
            steps = steps.map { it.copy(addedWaterGrams = whole(it.addedWaterGrams)) })
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
        private val SW = StepKind.Swirl; private val ST = StepKind.Stir; private val SV = StepKind.Serve; private val PR = StepKind.Press

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
                s(P, 60.0, 40, "الـ٦٠٪ الأخيرة تتحكم بالقوة، على ٣ صبات متساوية. حتى ١٨٠غ."),
                s(P, 60.0, 30, "حتى ٢٤٠غ."),
                s(P, 60.0, 50, "حتى ٣٠٠غ، وشيل القمع عند ٣:٣٠.")),
                source = "champion", author = "Tetsu Kasuya", creditAr = "بطل العالم لتحضير القهوة 2016",
                sourceUrl = "https://www.hario-europe.com/blogs/hario-community/v60-ambassadors-tetsu-kasuya"),
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
        ) + champions

        /** Champions' published recipes, restated from their own pages (numbers as published, same as iOS). */
        val champions: List<BrewPreset> get() = listOf(
            BrewPreset("v60-hoffmann", "Ultimate V60", "تشريب ودوّرة · ١:١٦٫٧ · ٣:٣٠", "V60", false, 30.0, 500.0, null, null, 100.0, "متوسط إلى ناعم", "V60 02", listOf(
                s(B, 60.0, 45, "تشريب بضعف وزن البن، ودوّر القمع حتى يتبلل كله."),
                s(P, 240.0, 30, "صب حتى ٣٠٠غ خلال ٣٠ ثانية."),
                s(P, 200.0, 30, "صب حتى ٥٠٠غ خلال ٣٠ ثانية، أبطأ شوي."),
                s(ST, null, 10, "حرّك مرة مع عقارب الساعة ومرة عكسها، خله ينزل شوي، ودوّره بهدوء."),
                s(W, null, 95, "خل الماء ينزل كله. الهدف يخلص عند ٣:٣٠.")),
                source = "champion", author = "James Hoffmann", creditAr = "بطل العالم للباريستا 2007",
                sourceUrl = "https://www.hario-usa.com/blogs/recipes-and-more-from-friends/james-hoffmann-uitimate-v60-technique"),
            BrewPreset("aeropress-wac-2025", "وصفة بطل الإيروبرس 2025", "١٠٠غ ماء على ٨٤° + ٧٠غ في السيرفر · ١:١٠", "AEROPRESS", false, 18.0, 100.0, null, null, 84.0,
                "متوسط ناعم (Comandante 31 كليك، منخول على 200 ميكرون)", "AeroPress", listOf(
                s(P, 100.0, 25, "قبل: حط ٧٠غ ماء على ٥٠° في السيرفر. صب ١٠٠غ على ٨٤°."),
                s(ST, null, 25, "حرّك عند ٠:٢٥."),
                s(PR, null, 20, "من ٠:٥٠ اضغط بهدوء حوالي ٢٠ ثانية فوق الماء اللي في السيرفر.")),
                source = "champion", author = "Nemo Pop · أستراليا", creditAr = "بطل العالم للإيروبرس 2025",
                sourceUrl = "https://worldaeropresschampionship.com/pages/recipes"),
            BrewPreset("aeropress-wac-2024", "وصفة بطل الإيروبرس 2024", "تشريب ثم نقع · ١:١٢٫٥ · ٢:١٠", "AEROPRESS", false, 18.0, 225.0, null, null, 93.0,
                "متوسط (Comandante 25 كليك، حوالي 750 ميكرون)", "AeroPress", listOf(
                s(B, 50.0, 30, "ورقتين فلتر مبلولة. صب ٥٠غ وخله يتشرب ٣٠ ثانية."),
                s(P, 175.0, 10, "صب حتى ٢٢٥غ."),
                s(ST, null, 10, "حرّك بهدوء ١٠ ثواني في ٤ اتجاهات."),
                s(W, null, 40, "انتظر لين ١:٣٠."),
                s(PR, null, 40, "اضغط ببطء حوالي ٤٠ ثانية."),
                s(SV, null, 10, "خففها بـ١٥ إلى ٣٠غ ماء بحرارة الغرفة، وخلها تبرد ٢ إلى ٣ دقايق.")),
                source = "champion", author = "George Stanica · رومانيا", creditAr = "بطل العالم للإيروبرس 2024",
                sourceUrl = "https://aeropress.com/blogs/w-a-c-aeropress-recipes/1st-george-stanica-romania-2024"),
            BrewPreset("aeropress-wac-2022", "وصفة بطلة الإيروبرس 2022", "مركّزة ثم تخفيف لين ١٥٠غ · ٢:١٠", "AEROPRESS", false, 18.0, 94.0, null, null, 92.0,
                "خشن، مع نخل الناعم (100 إلى 200 ميكرون)", "AeroPress", listOf(
                s(P, 94.0, 20, "قبل: حط ٢٠غ ماء بارد في السيرفر. صب ٩٤غ على ٩٢°."),
                s(ST, null, 10, "حرّك بهدوء ١٢ مرة."),
                s(W, null, 60, "ركّب المكبس عند ١:٢٠، وانتظر لين ١:٣٠."),
                s(PR, null, 40, "اضغط بهدوء."),
                s(SV, null, 10, "كمّل بماء ٩٠° لين ١٥٠غ.")),
                source = "champion", author = "Jibbi Little · أستراليا", creditAr = "بطلة العالم للإيروبرس 2022",
                sourceUrl = "https://aeropress.com/blogs/w-a-c-aeropress-recipes/1st-jibbi-little-australia-2022"),
            BrewPreset("aeropress-wac-2023", "وصفة بطل الإيروبرس 2023", "متقدمة · سيرفرين · ٢:٢٠", "AEROPRESS", false, 18.0, 105.0, null, null, 89.0, "متوسط", "AeroPress", listOf(
                s(P, 55.0, 25, "٣ أوراق فلتر. ١٦غ بن في الحجرة، وصب ٥٥غ على ٨٩°."),
                s(ST, null, 5, "حرّك ٥ ثواني وركّب المكبس."),
                s(W, null, 15, "انتظر لين ٠:٤٥."),
                s(PR, null, 10, "اضغط ١٠ ثواني في سيرفر أول (حوالي ٣٥غ) وخله على جنب."),
                s(W, null, 10, "انقل الجهاز لسيرفر ثاني وشيل المكبس."),
                s(P, 50.0, 25, "زد ٢غ بن، وصب ٥٠غ."),
                s(ST, null, 5, "حرّك ٥ ثواني، ورجّع خلاصة السيرفر الأول للحجرة."),
                s(W, null, 35, "ركّب المكبس وانتظر لين ٢:١٠."),
                s(PR, null, 10, "اضغط ١٠ ثواني (٨٠ إلى ٨٥غ)."),
                s(SV, null, 10, "ماء بحرارة الغرفة لين ١١٠غ، وبعدها ماء حار لين ١٥٤غ.")),
                source = "champion", author = "Tay Wipvasutt · تايلند", creditAr = "بطل العالم للإيروبرس 2023",
                sourceUrl = "https://aeropress.com/blogs/w-a-c-aeropress-recipes/1st-tay-wipvasutt-thailand-2023", advanced = true),
        )

        /** This coffee's roaster recipe as a plan; null for an xBloom link. Written steps share the total time evenly. */
        fun fromRoaster(r: RemoteRoasterRecipe, roasterName: String, index: Int): BrewPreset? {
            if (r.method == "XBLOOM" && r.link != null) return null
            val dose = r.doseG ?: return null
            val code = if (r.method in methods) r.method else "V60"
            val espresso = code == "ESPRESSO"
            val water = if (espresso) null else r.waterMl?.toDouble()
            val texts = r.steps.map { it.trim() }.filter { it.isNotEmpty() }
            val total = r.timeSec ?: 0
            val steps = if (texts.isEmpty()) listOf(BrewStep(StepKind.Pour, water, total.takeIf { it > 0 }, "حضّرها على أرقام المحمصة."))
                else { val each = if (total > 0) maxOf(total / texts.size, 1) else null
                    texts.mapIndexed { i, t -> BrewStep(if (i == 0) StepKind.Bloom else StepKind.Pour, null, each, t) } }
            return BrewPreset("roaster:$index", "وصفة $roasterName", r.label, code, r.isIced, dose, water, null,
                if (espresso) r.waterMl?.toDouble() else null, r.tempC?.toDouble() ?: 0.0, r.grind ?: "", r.tool ?: "", steps,
                source = "roaster", author = roasterName)
        }
    }
}

/** The recipe tabs of «حضّر», same as iOS: for this coffee (roaster's, then the community's), BunnList's, yours, the community's. */
data class BrewGroups(val forCoffee: List<BrewPreset>, val bunnlist: List<BrewPreset>, val mine: List<BrewPreset>, val community: List<BrewPreset>) {
    enum class Tab(val titleAr: String) { ForCoffee("★ لهالقهوة"), BunnList("BunnList"), Mine("وصفاتي"), Community("المجتمع") }
    fun plans(tab: Tab) = when (tab) { Tab.ForCoffee -> forCoffee; Tab.BunnList -> bunnlist; Tab.Mine -> mine; Tab.Community -> community }
    val tabs: List<Tab> get() = Tab.entries.filter { plans(it).isNotEmpty() }
    val all: List<BrewPreset> get() = Tab.entries.flatMap { plans(it) }
    fun tabOf(id: String?): Tab? = tabs.firstOrNull { t -> plans(t).any { it.id == id } }
    val defaultTab: Tab get() = if (forCoffee.isEmpty()) Tab.BunnList else Tab.ForCoffee

    companion object {
        fun build(method: String, lotSlug: String?, roasterName: String, roasterRecipes: List<RemoteRoasterRecipe>, mine: List<RemoteRecipe>,
                  lotCommunity: List<RemoteRecipe>, methodCommunity: List<RemoteRecipe>): BrewGroups {
            val roaster = roasterRecipes.mapIndexedNotNull { i, r -> BrewPreset.fromRoaster(r, roasterName, i) }.filter { it.methodCode == method }
            val lotOthers = lotCommunity.filter { !it.isMine && it.methodCode == method && it.state == "published" && it.lot?.slug == lotSlug }
                .sortedByDescending { it.likeCount ?: 0 }.map { it.toPreset() }
            val own = mine.filter { it.isMine && it.methodCode == method && it.state != "deleted" }
                .sortedBy { if (it.lot?.slug == lotSlug) 0 else 1 }.map { it.toPreset() }
            val lotIds = lotOthers.map { it.id }.toSet()
            val others = methodCommunity.filter { !it.isMine && it.methodCode == method && it.state == "published" }
                .map { it.toPreset() }.filter { it.id !in lotIds }.sortedByDescending { it.likes }.take(10)
            val presets = BrewPreset.presets(method)
            val bunnlist = presets.filter { it.source == "base" } + presets.filter { it.source == "champion" && !it.advanced } + presets.filter { it.advanced }
            return BrewGroups(roaster + lotOthers, bunnlist, own, others)
        }
    }
}

/** «وقت» on recipe cards: "2:45 د", or whole minutes ("16 د") from ten minutes up. */
fun brewMinutes(seconds: Int): String = when {
    seconds >= 600 -> "${Math.round(seconds / 60.0)} د"
    seconds < 60 -> "$seconds ث"
    else -> Methods.clock(seconds) + " د"
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
