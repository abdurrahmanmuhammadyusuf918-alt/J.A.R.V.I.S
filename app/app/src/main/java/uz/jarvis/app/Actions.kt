package uz.jarvis.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.AlarmClock
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

/** Telefon bilan bevosita amallar. Har biri foydalanuvchiga ko'rsatiladigan natija matnini qaytaradi. */
object Actions {

    private fun has(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun start(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    private fun resolve(ctx: Context, contact: String?, number: String?): Pair<String, String>? {
        if (!number.isNullOrBlank()) return number to number
        if (!contact.isNullOrBlank()) {
            val c = ContactsHelper.find(ctx, contact) ?: return null
            return c.name to c.number
        }
        return null
    }

    fun call(ctx: Context, contact: String?, number: String?): String {
        if (number.isNullOrBlank() && !has(ctx, Manifest.permission.READ_CONTACTS))
            return "Kontaktlarga ruxsat berilmagan. Jarvis ilovasi → ⚙ → Kontaktlar."
        val (name, num) = resolve(ctx, contact, number)
            ?: return "\"${contact ?: ""}\" nomli kontakt topilmadi."
        val canCall = has(ctx, Manifest.permission.CALL_PHONE)
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val ok = start(ctx, Intent(action, Uri.fromParts("tel", num, null)))
        return when {
            !ok -> "Qo'ng'iroq qilib bo'lmadi."
            canCall -> "$name ga qo'ng'iroq qilinmoqda"
            else -> "$name raqami terildi. Qo'ng'iroq ruxsati yo'q, yashil tugmani bosing."
        }
    }

    fun sms(ctx: Context, contact: String?, number: String?, message: String): String {
        val (name, num) = resolve(ctx, contact, number)
            ?: return if (!has(ctx, Manifest.permission.READ_CONTACTS))
                "Kontaktlarga ruxsat kerak."
            else "\"${contact ?: ""}\" nomli kontakt topilmadi."
        if (message.isBlank()) return "SMS matni bo'sh."
        if (!has(ctx, Manifest.permission.SEND_SMS)) {
            // Ruxsat yo'q bo'lsa, SMS ilovasini tayyor matn bilan ochamiz
            start(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).putExtra("sms_body", message))
            return "SMS ruxsati yo'q, xabarni o'zingiz yuboring."
        }
        return try {
            @Suppress("DEPRECATION")
            val sm = if (android.os.Build.VERSION.SDK_INT >= 31)
                ctx.getSystemService(SmsManager::class.java) else SmsManager.getDefault()
            val parts = sm.divideMessage(message)
            sm.sendMultipartTextMessage(num, null, parts, null, null)
            "$name ga SMS yuborildi"
        } catch (e: Exception) {
            "SMS yuborib bo'lmadi."
        }
    }

    fun youtube(ctx: Context, query: String): Boolean {
        val yt = Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube")
            .putExtra("query", query)
        if (start(ctx, yt)) return true
        return start(
            ctx,
            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)))
        )
    }

    fun alarm(ctx: Context, hour: Int, minute: Int, label: String): String {
        val i = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        val t = "%02d:%02d".format(hour, minute)
        return if (start(ctx, i)) "Budilnik $t ga qo'yildi" else "Budilnik qo'yib bo'lmadi."
    }

    fun timer(ctx: Context, seconds: Int, label: String): String {
        val i = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return if (start(ctx, i)) "Taymer qo'yildi" else "Taymer qo'yib bo'lmadi."
    }

    fun flashlight(ctx: Context, on: Boolean): String = try {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        if (id == null) "Fonar topilmadi."
        else {
            cm.setTorchMode(id, on)
            if (on) "Fonar yoqildi" else "Fonar o'chirildi"
        }
    } catch (e: Exception) {
        "Fonarni boshqarib bo'lmadi."
    }

    fun navigate(ctx: Context, query: String): Boolean =
        start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query))))
}
