package com.bunnlist.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bunnlist.core.BrewTone
import com.bunnlist.core.BrewVoice
import java.util.Locale

/**
 * Speaks and sounds the brew timer's cues (as on iOS): a tone at each step, the step's
 * line in Arabic, three-two-one before the next, and the finish. Other audio is lowered
 * only while a cue plays. Without an Arabic voice on the phone, the tones still play.
 */
class BrewSpeaker(context: Context) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val tracks = HashMap<BrewVoice.Mood, AudioTrack>()
    private var tts: TextToSpeech? = null
    private var ready = false

    /** Null while the engine loads; false when the phone has no Arabic voice. */
    var hasArabicVoice by mutableStateOf<Boolean?>(null)
        private set

    private fun attributes(content: Int) = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(content).build()

    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes(AudioAttributes.CONTENT_TYPE_SPEECH)).build()

    init {
        tts = runCatching {
            TextToSpeech(app) { status ->
                val engine = tts
                val ok = status == TextToSpeech.SUCCESS && engine != null && runCatching {
                    engine.setAudioAttributes(attributes(AudioAttributes.CONTENT_TYPE_SPEECH))
                    engine.setLanguage(Locale("ar")) >= TextToSpeech.LANG_AVAILABLE
                }.getOrDefault(false)
                ready = ok
                hasArabicVoice = ok
            }
        }.getOrNull()
        if (tts == null) hasArabicVoice = false
    }

    fun play(cue: BrewVoice.Cue) {
        handler.removeCallbacksAndMessages(null)
        runCatching { audio?.requestAudioFocus(focus) }
        when (cue) {
            is BrewVoice.Cue.Step -> { tone(cue.mood); handler.postDelayed({ say(cue.line) }, 400) }
            is BrewVoice.Cue.Count -> say(BrewVoice.countWord(cue.n))
            is BrewVoice.Cue.Finished -> { tone(BrewVoice.Mood.Done); handler.postDelayed({ say(cue.line) }, 600) }
        }
        // Give the audio back once the cue has had its time (the countdown re-takes it each second).
        handler.postDelayed({ runCatching { audio?.abandonAudioFocusRequest(focus) } }, 4500)
    }

    /** Stops mid-sentence (pause, reset, muting). */
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        runCatching { tts?.stop() }
        tracks.values.forEach { runCatching { it.pause() } }
        runCatching { audio?.abandonAudioFocusRequest(focus) }
    }

    fun release() {
        stop()
        runCatching { tts?.shutdown() }
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
    }

    private fun say(text: String) {
        if (ready) runCatching { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "brew") }
    }

    private fun tone(mood: BrewVoice.Mood) {
        runCatching {
            val track = tracks.getOrPut(mood) {
                val pcm = BrewTone.samples(mood)
                AudioTrack.Builder()
                    .setAudioAttributes(attributes(AudioAttributes.CONTENT_TYPE_SONIFICATION))
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(BrewTone.SAMPLE_RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.size * 2)
                    .build().also { it.write(pcm, 0, pcm.size) }
            }
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING || track.playState == AudioTrack.PLAYSTATE_PAUSED) track.stop()
            track.reloadStaticData()
            track.play()
        }
    }
}
