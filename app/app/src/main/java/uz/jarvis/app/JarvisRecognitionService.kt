package uz.jarvis.app

import android.content.Intent
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/** Yordamchi sifatida ro'yxatdan o'tish uchun kerak bo'lgan bo'sh xizmat (haqiqiy tanishni Google bajaradi). */
class JarvisRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        runCatching { listener?.error(SpeechRecognizer.ERROR_CLIENT) }
    }
    override fun onStopListening(listener: Callback?) {}
    override fun onCancel(listener: Callback?) {}
}
