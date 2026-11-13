package com.bunnlist.core

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test

class BrewVoiceTest {
    private val timeline = BrewTimeline(listOf(
        BrewStep(StepKind.Bloom, addedWaterGrams = 45.0, seconds = 40),
        BrewStep(StepKind.Swirl, seconds = 5),
        BrewStep(StepKind.Pour, addedWaterGrams = 105.0, seconds = 30),
        BrewStep(StepKind.Wait, seconds = 75),
    ))

    @Test fun stepLinesCarryTheScaleReading() {
        assertEquals(BrewVoice.Cue.Step(0, "تفتيح. صبّ لين 45 غرام", BrewVoice.Mood.Pour), BrewVoice.cue(timeline, 0))
        assertEquals(BrewVoice.Cue.Step(1, "حرّك القمع", BrewVoice.Mood.Act), BrewVoice.cue(timeline, 40))
        assertEquals(BrewVoice.Cue.Step(2, "صبّ لين 150 غرام", BrewVoice.Mood.Pour), BrewVoice.cue(timeline, 45))
        assertEquals(BrewVoice.Cue.Step(3, "انتظر دقيقة و 15 ثانية", BrewVoice.Mood.Wait), BrewVoice.cue(timeline, 75))
    }

    @Test fun countdownInTheLastThreeSecondsOfLongSteps() {
        assertEquals(BrewVoice.Cue.Count(3), BrewVoice.cue(timeline, 37))
        assertEquals(BrewVoice.Cue.Count(2), BrewVoice.cue(timeline, 38))
        assertEquals(BrewVoice.Cue.Count(1), BrewVoice.cue(timeline, 39))
        assertNull(BrewVoice.cue(timeline, 36))
        for (s in 41..44) assertNull(BrewVoice.cue(timeline, s))
        assertEquals(BrewVoice.Cue.Count(1), BrewVoice.cue(timeline, 149))
    }

    @Test fun finishesOnceAtTheEnd() {
        assertEquals(BrewVoice.Cue.Finished("خلصت، بالعافية"), BrewVoice.cue(timeline, 150))
        assertNull(BrewVoice.cue(timeline, 151))
        assertNull(BrewVoice.cue(BrewTimeline(emptyList()), 0))
    }

    @Test fun spokenDurations() {
        assertEquals("30 ثانية", BrewVoice.spokenDuration(30))
        assertEquals("5 ثواني", BrewVoice.spokenDuration(5))
        assertEquals("دقيقة", BrewVoice.spokenDuration(60))
        assertEquals("دقيقتين", BrewVoice.spokenDuration(120))
        assertEquals("4 دقايق", BrewVoice.spokenDuration(240))
        assertEquals("دقيقتين و ثانيتين", BrewVoice.spokenDuration(122))
    }

    @Test fun additionsAndPlainServe() {
        assertEquals("أضف 90 غرام ثلج", BrewVoice.line(BrewStep(StepKind.Serve, addedWaterGrams = 90.0, addition = "ice"), null))
        assertEquals("قدّم القهوة", BrewVoice.line(BrewStep(StepKind.Serve), null))
        assertEquals("اضغط بهدوء", BrewVoice.line(BrewStep(StepKind.Press, seconds = 30), null))
    }

    @Test fun arabicCoffeeSpeaksItsStages() {
        val preset = BrewPreset.all.first { it.methodCode == "ARABIC" }
        val titles = BrewVoice.titles(preset)
        assertEquals(preset.steps.size, titles.size)
        val cue = BrewVoice.cue(BrewTimeline(preset.steps), 0, titles) as BrewVoice.Cue.Step
        assertTrue(cue.line.startsWith("سخّن الماء في الدلة"))
        assertNotEquals(BrewVoice.Mood.Pour, cue.mood)
        assertTrue(BrewVoice.titles(BrewPreset.all.first { it.methodCode == "V60" }).all { it == null })
    }

    @Test fun everyTimedPresetStepHasALine() {
        for (preset in BrewPreset.all) {
            val tl = BrewTimeline(preset.steps)
            val titles = BrewVoice.titles(preset)
            for (i in preset.steps.indices) if ((preset.steps[i].seconds ?: 0) > 0) {
                val cue = BrewVoice.cue(tl, tl.start(i), titles) as? BrewVoice.Cue.Step ?: error("${preset.id} step $i has no cue")
                assertEquals(i, cue.index)
                assertFalse(cue.line.isEmpty())
            }
        }
    }

    @Test fun tonesAreDistinct() {
        val pour = BrewTone.samples(BrewVoice.Mood.Pour); val stop = BrewTone.samples(BrewVoice.Mood.Wait)
        assertFalse(pour.contentEquals(stop))
        assertEquals(0, pour.first().toInt())
        assertTrue(abs(pour.last().toInt()) < 400)
    }

    @Test fun espressoSpeaksAtTheWindowOnly() {
        assertEquals("وصلت 25 ثانية، تقدر توقّف", BrewVoice.espressoLine(25, 25..32))
        assertEquals("وقّف الحين", BrewVoice.espressoLine(32, 25..32))
        assertNull(BrewVoice.espressoLine(10, 25..32))
        assertNull(BrewVoice.espressoLine(28, 25..32))
    }

    @Test fun recordedClipsFollowTheCues() {
        assertEquals(listOf("bloom", "pour_45"), BrewVoice.clips(timeline, 0))
        assertEquals(listOf("swirl"), BrewVoice.clips(timeline, 40))
        assertEquals(listOf("pour_150"), BrewVoice.clips(timeline, 45))
        assertEquals(listOf("wait_75"), BrewVoice.clips(timeline, 75))
        assertEquals(listOf("count_2"), BrewVoice.clips(timeline, 38))
        assertEquals(listOf("done"), BrewVoice.clips(timeline, 150))
        for (s in 0..151) if (BrewVoice.cue(timeline, s) == null) assertTrue(BrewVoice.clips(timeline, s).isEmpty())
    }

    @Test fun linesWithoutARecordingFallBackToSpeech() {
        assertTrue(BrewVoice.clips(BrewTimeline(listOf(BrewStep(StepKind.Pour, addedWaterGrams = 37.5, seconds = 20))), 0).isEmpty())
        assertTrue(BrewVoice.clips(BrewTimeline(listOf(BrewStep(StepKind.Serve, addedWaterGrams = 90.0, seconds = 10, addition = "ice"))), 0).isEmpty())
        val arabic = BrewPreset.all.first { it.methodCode == "ARABIC" }
        assertTrue(BrewVoice.clips(BrewTimeline(arabic.steps), 0, BrewVoice.titles(arabic)).isEmpty())
        assertEquals(listOf("esp_25"), BrewVoice.espressoClips(25, 25..32))
        assertEquals(listOf("esp_stop"), BrewVoice.espressoClips(32, 25..32))
        assertTrue(BrewVoice.espressoClips(28, 25..32).isEmpty())
    }

    @Test fun scriptedRecipesPlayTheirOwnRecordings() {
        val arabic = BrewPreset.all.first { it.id == "arabic-saudi" }
        val tl = BrewTimeline(arabic.steps)
        assertEquals(listOf("arabic-saudi_0"), BrewVoice.clips(tl, 0, BrewVoice.titles(arabic), arabic.id))
        assertEquals(listOf("arabic-saudi_2"), BrewVoice.clips(tl, tl.start(2), BrewVoice.titles(arabic), arabic.id))
        assertEquals(listOf("cold-brew_3"), BrewVoice.stepClips("cold-brew", 3))
        assertTrue(BrewVoice.stepClips("v60-classic", 0).isEmpty())
        assertEquals(listOf("pour_150"), BrewVoice.clips(timeline, 45, emptyList(), "v60-classic"))
        assertFalse(BrewVoice.checklistLine(BrewPreset.all.first { it.id == "cold-brew" }.steps[0]).isEmpty())
    }

    @Test fun shotResult() {
        assertEquals(BrewVoice.Shot.Fast, BrewVoice.shot(20, 25..32))
        assertEquals(BrewVoice.Shot.Good, BrewVoice.shot(28, 25..32))
        assertEquals(BrewVoice.Shot.Slow, BrewVoice.shot(40, 25..32))
    }
}
