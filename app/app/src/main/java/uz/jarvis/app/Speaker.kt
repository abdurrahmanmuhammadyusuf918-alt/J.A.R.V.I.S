package uz.jarvis.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Butun ilova uchun yagona ovozli o'qish (TTS). */
object Speaker {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pending = mutableListOf<String>()

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
                    ready = true
                    pending.forEach { tts?.speak(it.take(500), TextToSpeech.QUEUE_ADD, null, "jarvis") }
                    pending.clear()
                }
            }
        }
    }

    @Synchronized
    fun speak(ctx: Context, text: String) {
        if (text.isBlank()) return
        init(ctx)
        if (ready) tts?.speak(text.take(500), TextToSpeech.QUEUE_FLUSH, null, "jarvis")
        else pending.add(text)
    }

    @Synchronized
    fun stop() {
        pending.clear()
        tts?.stop()
    }
}
