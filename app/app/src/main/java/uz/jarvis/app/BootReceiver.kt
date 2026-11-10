package uz.jarvis.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Telefon yoqilganda yoki ilova yangilanganda doimiy tinglashni qayta yoqadi. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Prefs.wakeEnabled(context)) WakeWordService.start(context)
    }
}
