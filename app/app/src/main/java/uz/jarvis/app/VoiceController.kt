package uz.jarvis.app

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** Ovozni matnga aylantirish (STT). Javobni o'qish — Speaker. Faqat asosiy oqimda ishlating. */
class VoiceController(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val onRms: (Float) -> Unit = {},
) {
    private var recognizer: SpeechRecognizer? = null
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var muted = false
    private val unmuteRunnable = Runnable { unmute() }

    // Google ovoz tanish boshlanishidagi "pik" ovozini o'chirish uchun qisqa muddat ovozsiz qilamiz
    private fun mute() {
        if (!muted) {
            try {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                muted = true
            } catch (_: Exception) {}
        }
        handler.removeCallbacks(unmuteRunnable)
        handler.postDelayed(unmuteRunnable, 3500)
    }

    private fun unmute() {
        handler.removeCallbacks(unmuteRunnable)
        if (muted) {
            try {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            } catch (_: Exception) {}
            muted = false
        }
    }

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Telefonda ovoz tanish xizmati yo'q (Google ilovasini o'rnating).")
            return
        }
        Speaker.stop()
        recognizer?.destroy()
        mute()
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = onListening(true)
                override fun onBeginningOfSpeech() = unmute()
                override fun onEndOfSpeech() = onListening(false)
                override fun onError(error: Int) {
                    unmute()
                    onListening(false)
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH,
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Unit
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            onError("Mikrofon ruxsati kerak.")
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            onError("Ovoz tanish uchun internet kerak.")
                        else -> onError("Ovozni tanib bo'lmadi (kod $error).")
                    }
                }
                override fun onResults(results: Bundle?) {
                    unmute()
                    onListening(false)
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) onResult(text)
                }
                override fun onRmsChanged(rmsdB: Float) = onRms(rmsdB)
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.LANGUAGE_TAG)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        recognizer?.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        onListening(false)
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) = Speaker.speak(context, text, onDone)

    fun destroy() {
        unmute()
        recognizer?.destroy()
        recognizer = null
    }
}
