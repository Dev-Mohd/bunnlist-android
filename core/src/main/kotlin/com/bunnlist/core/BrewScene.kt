package com.bunnlist.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Which brewer the animation draws (same as iOS). */
sealed interface BrewSceneKind {
    data class PourOver(val iced: Boolean) : BrewSceneKind
    data object AeroPress : BrewSceneKind
    data object Espresso : BrewSceneKind
    data object Arabic : BrewSceneKind
    /** Methods without their own scene keep the plain timer ring. */
    data object Ring : BrewSceneKind

    companion object {
        fun of(preset: BrewPreset): BrewSceneKind = when (preset.methodCode) {
            "V60" -> PourOver(iced = preset.iced || (preset.iceGrams ?: 0.0) > 0)
            "AEROPRESS" -> AeroPress
            "ESPRESSO" -> Espresso
            "ARABIC" -> Arabic
            else -> Ring
        }
    }
}

enum class SceneStage { Ready, Bloom, Pour, Swirl, Stir, Wait, Press, Serve, Done, Heat, AddCoffee, Simmer, AddCardamom, Settle }

/** What the brewing animation draws at a moment. Pure, so the timer's numbers and the drawing agree. */
data class BrewSceneState(
    val stage: SceneStage,
    /** 0…1 through the current stage. */
    val progress: Double,
    val stepIndex: Int,
    /** Water poured so far, as a share of the recipe's water (0…1). */
    val poured: Double,
    /** Coffee through into the server (0…1), never more than [poured]. */
    val drained: Double,
    /** Seconds until the next step; null on the last. */
    val secondsToNext: Double?,
    /** A title when the step kind alone doesn't say it (Arabic coffee). */
    val titleAr: String?,
) {
    val standing: Double get() = max(0.0, poured - drained)
    /** The last three seconds before a step change: the cue to look up. */
    val isCueing: Boolean get() = (secondsToNext ?: Double.POSITIVE_INFINITY) <= 3 && (secondsToNext ?: 0.0) > 0
}

object BrewScene {
    fun state(preset: BrewPreset, elapsed: Double, started: Boolean): BrewSceneState {
        val steps = preset.steps
        val durations = steps.map { max(it.seconds ?: 0, 0).toDouble() }
        val total = durations.sum()
        val water = steps.map { if (it.kind.addsWater) it.addedWaterGrams ?: 0.0 else 0.0 }
        val totalWater = max(water.sum(), 1.0)
        val arabic = preset.methodCode == "ARABIC"

        if (!started || steps.isEmpty()) {
            return BrewSceneState(SceneStage.Ready, 0.0, 0, 0.0, 0.0, null, if (arabic) arabicStages(steps).firstOrNull()?.second else null)
        }
        if (elapsed >= total) {
            return BrewSceneState(SceneStage.Done, 1.0, steps.size - 1, 1.0, 1.0, null, if (arabic) "قهوتك جاهزة" else null)
        }
        var start = 0.0; var index = 0
        for ((i, d) in durations.withIndex()) { if (elapsed < start + d) { index = i; break }; start += d }
        val duration = max(durations[index], 0.001)
        val progress = ((elapsed - start) / duration).coerceIn(0.0, 1.0)

        // Water lands over the first part of a step: blooms are a quick pour, pours a steady one.
        var pouredGrams = water.take(index).sum()
        if (water[index] > 0) {
            val span = if (steps[index].kind == StepKind.Bloom) 0.4 else 0.85
            pouredGrams += water[index] * min(progress / span, 1.0)
        }
        val poured = min(pouredGrams / totalWater, 1.0)
        // Drawdown starts a little into the first step and ends with the recipe.
        val lag = durations.firstOrNull()?.let { it * 0.5 } ?: 0.0
        val flow = if (total > lag) ((elapsed - lag) / (total - lag)).coerceIn(0.0, 1.0) else 0.0
        val drained = min(poured, flow.pow(0.9))
        val next = if (index < steps.size - 1) start + duration - elapsed else null

        var stage = when (steps[index].kind) {
            StepKind.Bloom -> SceneStage.Bloom; StepKind.Pour -> SceneStage.Pour; StepKind.Swirl -> SceneStage.Swirl
            StepKind.Stir -> SceneStage.Stir; StepKind.Wait -> SceneStage.Wait; StepKind.Press -> SceneStage.Press; StepKind.Serve -> SceneStage.Serve
        }
        var title: String? = null
        if (arabic) arabicStages(steps).getOrNull(index)?.let { stage = it.first; title = it.second }
        return BrewSceneState(stage, progress, index, poured, drained, next, title)
    }

    /** Arabic coffee's steps are plain pours, waits and stirs; their order says what each is. */
    internal fun arabicStages(steps: List<BrewStep>): List<Pair<SceneStage, String>> {
        var stirs = 0
        return steps.mapIndexed { index, step ->
            if (index == 0 && step.kind != StepKind.Stir) return@mapIndexed SceneStage.Heat to "سخّن الماء في الدلة"
            when (step.kind) {
                StepKind.Serve -> SceneStage.Serve to "صبّها وقدّمها"
                StepKind.Stir, StepKind.Swirl -> { stirs += 1; if (stirs == 1) SceneStage.AddCoffee to "أضف البن" else SceneStage.AddCardamom to "أضف الهيل" }
                else -> if (stirs >= 2) SceneStage.Settle to "خلّها تركد" else SceneStage.Simmer to "على نار هادية"
            }
        }
    }

    /** Espresso's target window: the recipe's shot time ± 3 s, or the usual 25–32 s. */
    fun espressoTarget(preset: BrewPreset): IntRange {
        val s = preset.steps.firstOrNull()?.seconds
        return if (s != null && s > 0) max(s - 3, 1)..(s + 3) else 25..32
    }
}
