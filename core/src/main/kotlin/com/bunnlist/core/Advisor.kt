package com.bunnlist.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.math.roundToInt

/** Flavour families, matched from notes by keyword (Arabic and English), as on iOS. */
enum class FlavorFamily(val code: String, val shortNameAr: String, val rgb: Int, private val keywords: List<String>) {
    Berry("berry", "توت", 0xB83345, listOf("توت", "فراول", "كرز", "عنب", "رمان", "berry", "strawberr", "cherr", "grape", "pomegranate", "cassis", "currant")),
    Citrus("citrus", "حمضيات", 0xF2BD33, listOf("ليمون", "برتقال", "حمضي", "جريب", "يوسفي", "ليم", "lemon", "orange", "citrus", "grapefruit", "lime", "bergamot", "tangerine")),
    StoneFruit("stoneFruit", "خوخ ومشمش", 0xED945C, listOf("خوخ", "مشمش", "دراق", "نكتارين", "برقوق", "peach", "apricot", "nectarine", "plum", "stone fruit")),
    Tropical("tropical", "استوائي", 0xFA9E26, listOf("مانجو", "أناناس", "اناناس", "استوائ", "باشن", "بابايا", "جوافة", "ليتشي", "mango", "pineapple", "tropical", "passion", "papaya", "guava", "lychee")),
    DriedFruit("driedFruit", "فواكه مجففة", 0x8C454D, listOf("زبيب", "تمر", "تين", "مجفف", "raisin", "date", "fig", "dried", "prune")),
    Floral("floral", "زهري", 0xC28FDB, listOf("زهر", "زهور", "ياسمين", "ورد", "لافند", "بنفسج", "floral", "jasmine", "rose", "lavender", "violet", "hibiscus", "blossom")),
    Chocolate("chocolate", "شوكولاتة", 0x5C3826, listOf("شوكولا", "كاكاو", "chocolate", "cocoa", "cacao")),
    Caramel("caramel", "كراميل", 0xC7853D, listOf("كراميل", "سكر", "توفي", "فانيل", "caramel", "sugar", "toffee", "vanilla", "molasses", "butterscotch")),
    Nutty("nutty", "مكسرات", 0xA3784D, listOf("مكسرات", "لوز", "بندق", "جوز", "فستق", "nut", "almond", "hazelnut", "walnut", "pistachio", "peanut")),
    Spice("spice", "توابل", 0xA84D29, listOf("بهار", "قرفة", "هيل", "قرنفل", "زنجبيل", "فلفل", "spice", "cinnamon", "cardamom", "clove", "ginger", "pepper")),
    Tea("tea", "شاي", 0x85A854, listOf("شاي", "شمام", "بطيخ", "تفاح", "كمثرى", "عشب", "نعناع", "tea", "melon", "apple", "pear", "herb", "mint", "green")),
    Honey("honey", "عسل", 0xE3B040, listOf("عسل", "honey")),
    Other("other", "أخرى", 0x8C8766, emptyList());

    companion object {
        fun of(note: String): FlavorFamily {
            val text = note.lowercase()
            return entries.firstOrNull { f -> f.keywords.any { text.contains(it) } } ?: Other
        }
        /** The chips the advisor offers, in order. */
        val advisorChoices = listOf(Berry, Chocolate, Floral, Citrus, Caramel, Nutty, Honey, Tea, Tropical, StoneFruit, Spice, DriedFruit)
    }
}

enum class AdvisorStep { Mood, Method, Milk, MilkDrink, Flavors, Serve, Adventure, Text }

/** «ساعدني أختار» answers; the body for POST /api/v1/advisor. */
data class AdvisorAnswers(
    val mood: String? = null,          // wake | calm | guests | new
    val method: String? = null,        // V60 | ESPRESSO | CHEMEX | FRENCH_PRESS
    val milk: Boolean? = null,         // asked only after espresso
    val milkDrink: MilkDrink? = null,  // asked only after «مع حليب»
    val families: Set<FlavorFamily> = emptySet(),
    val serve: String? = null,         // HOT | ICED
    val adventure: Double = 0.5,       // 0 safe … 1 surprise me
    val text: String = "",
) {
    val steps: List<AdvisorStep>
        get() = buildList {
            add(AdvisorStep.Mood); add(AdvisorStep.Method)
            if (method == "ESPRESSO") add(AdvisorStep.Milk)
            if (method == "ESPRESSO" && milk == true) add(AdvisorStep.MilkDrink)
            addAll(listOf(AdvisorStep.Flavors, AdvisorStep.Serve, AdvisorStep.Adventure, AdvisorStep.Text))
        }

    /** The BunnList recipe for the chosen drink, to show with the results. */
    val milkPreset: BrewPreset?
        get() = if (method == "ESPRESSO" && milk == true && milkDrink != null) BrewPreset.all.firstOrNull { it.milkDrink == milkDrink } else null

    val isComplete: Boolean get() = mood != null && method != null && serve != null && (method != "ESPRESSO" || milk != null)

    fun body(): JsonObject = buildJsonObject {
        put("mood", mood ?: "calm")
        put("method", method ?: "V60")
        putJsonArray("families") {
            FlavorFamily.entries.filter { it in families && it != FlavorFamily.Other }.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.code)) }
        }
        put("serve", serve ?: "HOT")
        put("adventure", (adventure * 100).roundToInt() / 100.0)
        if (method == "ESPRESSO" && milk != null) put("milk", milk)
        val t = text.trim()
        if (t.isNotEmpty()) put("text", t.take(300))
    }
}

@Serializable
data class RemoteAdvisorPick(val lot: RemoteLot, val score: Int, val reason: String, val tag: String? = null)

@Serializable
data class RemoteAdvisorResult(val understood: Understood? = null, val picks: List<RemoteAdvisorPick> = emptyList()) {
    @Serializable data class Understood(val chips: List<String> = emptyList())
}
