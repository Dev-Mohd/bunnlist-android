package com.bunnlist.core

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Hands-free brewing (as on iOS): what the timer says and sounds at each second, so the
 * brew can be followed without looking at the phone. Pure text and samples; the app does
 * the speaking and the playing.
 */
object BrewVoice {
    /** What the hands are doing in a step. Drives the tone and the glance colour. */
    enum class Mood { Pour, Act, Wait, Done }

    sealed interface Cue {
        /** A step begins: play the mood's tone, then say the line. */
        data class Step(val index: Int, val line: String, val mood: Mood) : Cue
        /** Three, two, one before the step ends. */
        data class Count(val n: Int) : Cue
        data class Finished(val line: String) : Cue
    }

    const val FINISHED_LINE = "خلصت، بالعافية"
    /** A step shorter than this has no countdown: its own line would still be playing. */
    private const val COUNTDOWN_MIN_SECONDS = 8

    fun countWord(n: Int) = when (n) { 3 -> "ثلاثة"; 2 -> "اثنين"; else -> "واحد" }

    /** Titles that replace the step kind (Arabic coffee), one per step; null elsewhere. */
    fun titles(preset: BrewPreset): List<String?> =
        if (preset.methodCode == "ARABIC") BrewScene.arabicStages(preset.steps).map { it.second } else preset.steps.map { null }

    fun mood(step: BrewStep, titleAr: String? = null): Mood = when {
        titleAr != null -> if (step.kind == StepKind.Wait || step.kind.addsWater) Mood.Wait else Mood.Act
        step.kind.addsWater -> Mood.Pour
        step.kind == StepKind.Wait -> Mood.Wait
        else -> Mood.Act
    }

    /** "30 ثانية", "دقيقة و 15 ثانية", "دقيقتين". */
    fun spokenDuration(seconds: Int): String {
        val m = seconds / 60; val s = seconds % 60
        val parts = ArrayList<String>()
        when (m) { 0 -> {}; 1 -> parts += "دقيقة"; 2 -> parts += "دقيقتين"; in 3..10 -> parts += "$m دقايق"; else -> parts += "$m دقيقة" }
        when (s) { 0 -> {}; 1 -> parts += "ثانية"; 2 -> parts += "ثانيتين"; in 3..10 -> parts += "$s ثواني"; else -> parts += "$s ثانية" }
        return if (parts.isEmpty()) "لحظة" else parts.joinToString(" و ")
    }

    private fun grams(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)

    /** The line for the start of a step. [pourTo] is the scale reading to reach. */
    fun line(step: BrewStep, pourTo: Double?, titleAr: String? = null): String {
        val seconds = step.seconds ?: 0
        if (titleAr != null) return if (mood(step, titleAr) == Mood.Wait && seconds > 0) "$titleAr، ${spokenDuration(seconds)}" else titleAr
        return when (step.kind) {
            StepKind.Bloom -> if (pourTo == null) "تفتيح" else "تفتيح. صبّ لين ${grams(pourTo)} غرام"
            StepKind.Pour -> if (pourTo == null) "صبّ" else "صبّ لين ${grams(pourTo)} غرام"
            StepKind.Wait -> if (seconds > 0) "انتظر ${spokenDuration(seconds)}" else "انتظر"
            StepKind.Swirl -> "حرّك القمع"
            StepKind.Stir -> "قلّب"
            StepKind.Press -> "اضغط بهدوء"
            StepKind.Serve -> {
                val g = step.addedWaterGrams
                if (step.addition != null && g != null) (if (step.addition == "ice") "أضف ${grams(g)} غرام ثلج" else "أضف ${grams(g)} غرام ماء")
                else "قدّم القهوة"
            }
        }
    }

    /** What happens as the clock reaches [second], if anything: the end, else a step's start, else the countdown. */
    fun cue(timeline: BrewTimeline, second: Int, titles: List<String?> = emptyList()): Cue? {
        if (timeline.steps.isEmpty() || second < 0) return null
        val pos = timeline.position(second)
        if (pos.isFinished) return if (second == timeline.totalSeconds) Cue.Finished(FINISHED_LINE) else null
        val step = timeline.steps[pos.stepIndex]
        val title = titles.getOrNull(pos.stepIndex)
        if (second == timeline.start(pos.stepIndex)) return Cue.Step(pos.stepIndex, line(step, pos.pourTo, title), mood(step, title))
        if (pos.secondsLeftInStep in 1..3 && (step.seconds ?: 0) >= COUNTDOWN_MIN_SECONDS) return Cue.Count(pos.secondsLeftInStep)
        return null
    }
}

/**
 * The timer's tones, drawn as samples so they need no sound files: two rising notes to
 * start pouring, two falling to stop, one for an action, three for done.
 */
object BrewTone {
    const val SAMPLE_RATE = 44_100

    private fun notes(mood: BrewVoice.Mood): List<Pair<Double, Double>> = when (mood) {
        BrewVoice.Mood.Pour -> listOf(660.0 to 0.12, 990.0 to 0.16)
        BrewVoice.Mood.Wait -> listOf(880.0 to 0.12, 587.0 to 0.18)
        BrewVoice.Mood.Act -> listOf(784.0 to 0.16)
        BrewVoice.Mood.Done -> listOf(660.0 to 0.12, 880.0 to 0.12, 1175.0 to 0.26)
    }

    /** Mono 16-bit samples, each note faded in and out so it doesn't click. */
    fun samples(mood: BrewVoice.Mood): ShortArray {
        val out = ArrayList<Short>()
        for ((hz, seconds) in notes(mood)) {
            val count = (seconds * SAMPLE_RATE).toInt()
            val fade = SAMPLE_RATE * 0.012
            for (i in 0 until count) {
                val envelope = min(1.0, min(i / fade, (count - 1 - i) / fade))
                out += (sin(2 * PI * hz * i / SAMPLE_RATE) * envelope * 0.55 * Short.MAX_VALUE).toInt().toShort()
            }
        }
        return out.toShortArray()
    }
}
