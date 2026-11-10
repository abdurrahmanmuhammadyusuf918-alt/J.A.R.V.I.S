package uz.jarvis.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

/** Yon tugma uzoq bosilganda chaqiriladi: Jarvis ochiladi va darhol tinglay boshlaydi. */
class JarvisSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_AUTO_LISTEN, true)
        }
        context.startActivity(intent)
        hide()
    }
}
