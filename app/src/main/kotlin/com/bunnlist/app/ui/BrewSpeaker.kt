package com.bunnlist.app.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
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
 * line in Arabic, three-two-one before the next, and the finish. Lines with a recording
 * packaged in assets/brewvoice play in the recorded voice; the rest are read by the
 * phone's own Arabic voice. Other audio is lowered only while a cue plays.
 */
class BrewSpeaker(context: Context) {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val tracks = HashMap<BrewVoice.Mood, AudioTrack>()
    private var tts: TextToSpeech? = null
    /** The recorded clips that ship with the app, by name. */
    private val recorded: Set<String> = runCatching { app.assets.list("brewvoice")?.map { it.removeSuffix(".mp3") }?.toSet() }.getOrNull().orEmpty()
    private var player: MediaPlayer? = null
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

    /** [clips] are the recorded files for this cue (see BrewVoice.clips); when any is missing the cue's text is spoken instead. */
    fun play(cue: BrewVoice.Cue, clips: List<String> = emptyList()) {
        handler.removeCallbacksAndMessages(null)
        stopClips()
        runCatching { audio?.requestAudioFocus(focus) }
        val useClips = clips.isNotEmpty() && clips.all { it in recorded }
        fun voice(text: String, delay: Long) {
            if (useClips) handler.postDelayed({ playClips(clips) }, delay) else if (delay == 0L) say(text) else handler.postDelayed({ say(text) }, delay)
        }
        when (cue) {
            is BrewVoice.Cue.Step -> { tone(cue.mood); voice(cue.line, 400) }
            is BrewVoice.Cue.Count -> voice(BrewVoice.countWord(cue.n), 0)
            is BrewVoice.Cue.Finished -> { tone(BrewVoice.Mood.Done); voice(cue.line, 600) }
        }
        // Give the audio back once the cue has had its time (the countdown re-takes it each second).
        handler.postDelayed({ runCatching { audio?.abandonAudioFocusRequest(focus) } }, 5500)
    }

    /** Plays the clips one after another. */
    private fun playClips(names: List<String>) {
        val name = names.firstOrNull() ?: return
        runCatching {
            val fd = app.assets.openFd("brewvoice/$name.mp3")
            val mp = MediaPlayer()
            mp.setAudioAttributes(attributes(AudioAttributes.CONTENT_TYPE_SPEECH))
            mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            fd.close()
            mp.setOnCompletionListener { it.release(); if (player === it) player = null; playClips(names.drop(1)) }
            mp.setOnErrorListener { p, _, _ -> p.release(); if (player === p) player = null; true }
            mp.prepare()
            player = mp
            mp.start()
        }
    }

    private fun stopClips() {
        player?.let { p -> runCatching { p.setOnCompletionListener(null); p.stop() }; runCatching { p.release() } }
        player = null
    }

    /** Stops mid-sentence (pause, reset, muting). */
    fun stop() {
        handler.removeCallbacksAndMessages(null)
        stopClips()
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
