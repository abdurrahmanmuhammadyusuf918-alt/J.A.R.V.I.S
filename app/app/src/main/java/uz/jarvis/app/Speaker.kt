package uz.jarvis.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Butun ilova uchun yagona ovozli o'qish (TTS). */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<Pair<String, (() -> Unit)?>>()

    @Volatile private var doneCb: (() -> Unit)? = null

    private fun fireDone() {
        val cb = doneCb
        doneCb = null
        cb?.invoke()
    }

    @Synchronized
    private fun init(ctx: Context) {
        if (tts != null) return
        tts = TextToSpeech(ctx.applicationContext) { status ->
            synchronized(this) {
                if (status == TextToSpeech.SUCCESS) {
                    val r = tts?.setLanguage(Locale.forLanguageTag(Prefs.LANGUAGE_TAG))
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.language = Locale.getDefault()
                    }
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) = fireDone()
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) = fireDone()
                    })
                    ready = true
                    pending.forEach { (t, cb) -> speakNow(t, cb) }
                    pending.clear()
                }
            }
        }
    }

    private fun speakNow(text: String, onDone: (() -> Unit)?) {
        doneCb = onDone
        tts?.speak(text.take(500), TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    /** onDone — o'qish tugagach chaqiriladi (suhbatni davom ettirish uchun). */
    @Synchronized
    fun speak(ctx: Context, text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) { onDone?.invoke(); return }
        init(ctx)
        if (ready) speakNow(text, onDone) else pending.add(text to onDone)
    }

    @Synchronized
    fun stop() {
        pending.clear()
        doneCb = null
        tts?.stop()
    }
}
