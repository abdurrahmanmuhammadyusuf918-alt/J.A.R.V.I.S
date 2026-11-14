package uz.jarvis.app

import android.Manifest
import android.app.SearchManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/** Siri kabi qurilma amallari: vaqt, batareya, ovoz, yorqinlik, media, sozlamalar, kamera, kalendar... */
object DeviceActions {

    private fun has(ctx: Context, p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    private fun start(ctx: Context, i: Intent): Boolean = try {
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    private fun startFirst(ctx: Context, vararg intents: Intent): Boolean {
        for (i in intents) if (start(ctx, i)) return true
        return false
    }

    // ------------------------------------------------------------ vaqt, sana, batareya

    private val weekdays = arrayOf("yakshanba", "dushanba", "seshanba", "chorshanba", "payshanba", "juma", "shanba")
    private val months = arrayOf(
        "yanvar", "fevral", "mart", "aprel", "may", "iyun",
        "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr"
    )

    private fun hm(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun whenText(millis: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        return "${c.get(Calendar.DAY_OF_MONTH)}-${months[c.get(Calendar.MONTH)]}, ${hm(millis)}"
    }

    fun time(): String = "Soat " + hm(System.currentTimeMillis())

    fun date(): String {
        val c = Calendar.getInstance()
        return "Bugun ${weekdays[c.get(Calendar.DAY_OF_WEEK) - 1]}, " +
            "${c.get(Calendar.YEAR)}-yil ${c.get(Calendar.DAY_OF_MONTH)}-${months[c.get(Calendar.MONTH)]}"
    }

    fun battery(ctx: Context): String {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return "Batareya holatini aniqlab bo'lmadi."
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
        if (level < 0 || scale <= 0) return "Batareya holatini aniqlab bo'lmadi."
        val pct = level * 100 / scale
        return "Batareya $pct foiz" + if (charging) ", quvvat olmoqda" else ""
    }

    fun random(kind: String, min: Int, max: Int): String = when (kind) {
        "coin" -> "Tanga tashlandi: " + if (Random.nextBoolean()) "gerb" else "raqam"
        "dice" -> "Kubik: " + Random.nextInt(1, 7)
        else -> {
            val lo = minOf(min, max)
            val hi = maxOf(min, max)
            "Tasodifiy son: " + Random.nextInt(lo, hi + 1)
        }
    }

    // ------------------------------------------------------------ ovoz, yorqinlik, media

    fun volume(ctx: Context, mode: String, level: Int, stream: String): String {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val st = if (stream == "ring") AudioManager.STREAM_RING else AudioManager.STREAM_MUSIC
        val maxV = am.getStreamMaxVolume(st)
        return try {
            when (mode) {
                "up", "down" -> {
                    val steps = max(1, (maxV * 0.2f).roundToInt())
                    val dir = if (mode == "up") AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                    repeat(steps) { am.adjustStreamVolume(st, dir, 0) }
                    am.adjustStreamVolume(st, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
                }
                "mute" -> am.adjustStreamVolume(st, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                "unmute" -> am.adjustStreamVolume(st, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                "max" -> am.setStreamVolume(st, maxV, AudioManager.FLAG_SHOW_UI)
                else -> {
                    val v = (maxV * level.coerceIn(0, 100) / 100f).roundToInt()
                    am.setStreamVolume(st, v, AudioManager.FLAG_SHOW_UI)
                }
            }
            if (mode == "mute") "Ovoz o'chirildi"
            else "Ovoz balandligi ${(am.getStreamVolume(st) * 100f / maxV).roundToInt()} foiz"
        } catch (e: SecurityException) {
            "Bu rejimda ovozni o'zgartirib bo'lmaydi (\"Bezovta qilmang\" yoqilgan bo'lishi mumkin)."
        }
    }

    fun brightness(ctx: Context, mode: String, level: Int): String {
        if (!Settings.System.canWrite(ctx)) {
            start(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            return "Yorqinlikni boshqarish uchun Jarvis'ga ruxsat bering, so'ng qayta ayting."
        }
        return try {
            val cr = ctx.contentResolver
            val cur = try {
                Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Exception) {
                128
            }
            val curPct = (cur * 100f / 255f).roundToInt()
            val target = when (mode) {
                "up" -> curPct + 20
                "down" -> curPct - 20
                "max" -> 100
                "min" -> 5
                else -> level
            }.coerceIn(1, 100)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (target * 255f / 100f).roundToInt())
            "Yorqinlik $target foiz"
        } catch (e: Exception) {
            "Yorqinlikni o'zgartirib bo'lmadi."
        }
    }

    fun media(ctx: Context, key: String): String {
        val code = when (key) {
            "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
            "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        }
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return when (key) {
            "play" -> "Davom ettirildi"
            "pause" -> "To'xtatildi"
            "next" -> "Keyingisi"
            "previous" -> "Oldingisi"
            "stop" -> "To'xtatildi"
            else -> "Bajarildi"
        }
    }

    // ------------------------------------------------------------ sozlamalar

    /** Sozlamalar sahifasining nomi (ovozda aytish va agent uchun). */
    fun settingLabel(target: String): String = when (target) {
        "wifi" -> "Wi-Fi"
        "bluetooth" -> "Bluetooth"
        "airplane" -> "Parvoz rejimi"
        "hotspot" -> "Hotspot (modem rejimi)"
        "location" -> "Joylashuv"
        "dnd" -> "Bezovta qilmang rejimi"
        "display" -> "Ekran"
        "battery" -> "Quvvat tejash"
        "nfc" -> "NFC"
        "sound" -> "Ovoz"
        "data" -> "Mobil internet"
        "storage" -> "Xotira"
        "apps" -> "Ilovalar"
        "accessibility" -> "Maxsus imkoniyatlar"
        "date" -> "Sana va vaqt"
        "language" -> "Til"
        "security" -> "Xavfsizlik"
        "about" -> "Telefon haqida"
        else -> "Sozlamalar"
    }

    val toggleable = setOf("wifi", "bluetooth", "airplane", "hotspot", "location", "nfc", "dnd", "battery", "data")

    fun settings(ctx: Context, target: String): String {
        val s = Settings.ACTION_SETTINGS
        val ok = when (target) {
            "wifi" -> startFirst(ctx, Intent(Settings.ACTION_WIFI_SETTINGS))
            "bluetooth" -> startFirst(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "airplane" -> startFirst(ctx, Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
            "hotspot" -> startFirst(
                ctx, Intent("android.settings.TETHER_SETTINGS"), Intent(Settings.ACTION_WIRELESS_SETTINGS)
            )
            "location" -> startFirst(ctx, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            "dnd" -> startFirst(
                ctx, Intent("android.settings.ZEN_MODE_SETTINGS"), Intent(Settings.ACTION_SOUND_SETTINGS)
            )
            "display" -> startFirst(ctx, Intent(Settings.ACTION_DISPLAY_SETTINGS))
            "battery" -> startFirst(ctx, Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
            "nfc" -> startFirst(ctx, Intent(Settings.ACTION_NFC_SETTINGS))
            "sound" -> startFirst(ctx, Intent(Settings.ACTION_SOUND_SETTINGS))
            "data" -> startFirst(
                ctx, Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS), Intent(Settings.ACTION_WIRELESS_SETTINGS)
            )
            "storage" -> startFirst(ctx, Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
            "apps" -> startFirst(ctx, Intent(Settings.ACTION_APPLICATION_SETTINGS))
            "accessibility" -> startFirst(ctx, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "date" -> startFirst(ctx, Intent(Settings.ACTION_DATE_SETTINGS))
            "language" -> startFirst(ctx, Intent(Settings.ACTION_LOCALE_SETTINGS))
            "security" -> startFirst(ctx, Intent(Settings.ACTION_SECURITY_SETTINGS))
            "about" -> startFirst(ctx, Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
            else -> false
        } || startFirst(ctx, Intent(s))
        return if (ok) "${settingLabel(target)} sozlamalari ochildi" else "Sozlamalarni ochib bo'lmadi."
    }

    // ------------------------------------------------------------ kamera

    fun camera(ctx: Context, mode: String): String {
        val intent = when (mode) {
            "video" -> Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA)
            "selfie" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                .putExtra("android.intent.extras.CAMERA_FACING", 1)
                .putExtra("android.intent.extras.LENS_FACING_FRONT", 1)
                .putExtra("android.intent.extra.USE_FRONT_CAMERA", true)
            else -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        }
        return if (start(ctx, intent)) "Kamera ochildi" else "Kamerani ochib bo'lmadi."
    }

    // ------------------------------------------------------------ kalendar

    private val formats = listOf("yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm", "yyyy-MM-dd")

    fun parseTime(s: String): Long? {
        val t = s.trim()
        if (t.isEmpty()) return null
        for (f in formats) {
            try {
                val d = SimpleDateFormat(f, Locale.US).parse(t)
                if (d != null) return d.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun writableCalendarId(ctx: Context): Long? {
        val proj = arrayOf(CalendarContract.Calendars._ID)
        val sel = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        for (order in listOf("${CalendarContract.Calendars.IS_PRIMARY} DESC", null)) {
            try {
                ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, sel, args, order)?.use { c ->
                    if (c.moveToFirst()) return c.getLong(0)
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun addEvent(ctx: Context, title: String, startS: String, endS: String): String {
        val begin = parseTime(startS) ?: return "Vaqtni tushunmadim, qayta ayting."
        val end = parseTime(endS)?.takeIf { it > begin } ?: (begin + 60 * 60 * 1000L)
        val name = title.ifBlank { "Eslatma" }

        if (has(ctx, Manifest.permission.READ_CALENDAR) && has(ctx, Manifest.permission.WRITE_CALENDAR)) {
            try {
                val calId = writableCalendarId(ctx)
                if (calId != null) {
                    val v = ContentValues().apply {
                        put(CalendarContract.Events.CALENDAR_ID, calId)
                        put(CalendarContract.Events.TITLE, name)
                        put(CalendarContract.Events.DTSTART, begin)
                        put(CalendarContract.Events.DTEND, end)
                        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                        put(CalendarContract.Events.HAS_ALARM, 1)
                    }
                    val uri = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)
                    val id = uri?.lastPathSegment?.toLongOrNull()
                    if (id != null) {
                        val r = ContentValues().apply {
                            put(CalendarContract.Reminders.EVENT_ID, id)
                            put(CalendarContract.Reminders.MINUTES, 0)
                            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                        }
                        ctx.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, r)
                        return "\"$name\" kalendarga qo'shildi: ${whenText(begin)}"
                    }
                }
            } catch (_: Exception) {
            }
        }
        // Ruxsat yo'q yoki xato: kalendar ilovasini tayyor ma'lumot bilan ochamiz
        val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, name)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        return if (start(ctx, i)) "Kalendar ochildi, \"Saqlash\"ni bosing: ${whenText(begin)}"
        else "Kalendarga qo'sha olmadim."
    }

    fun readEvents(ctx: Context, fromS: String, toS: String): String {
        if (!has(ctx, Manifest.permission.READ_CALENDAR)) {
            return "Kalendarni o'qish uchun ruxsat kerak (Jarvis ilovasi → ⚙ → Kalendar)."
        }
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val from = parseTime(fromS) ?: cal.timeInMillis
        val to = parseTime(toS)?.takeIf { it > from } ?: (from + 24 * 60 * 60 * 1000L)
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, from)
        ContentUris.appendId(b, to)
        val proj = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
        )
        val lines = mutableListOf<String>()
        try {
            ctx.contentResolver.query(b.build(), proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext() && lines.size < 12) {
                    val t = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(nomsiz)"
                    val allDay = c.getInt(2) == 1
                    lines.add(if (allDay) "Kun bo'yi: $t" else "${hm(c.getLong(1))} $t")
                }
            }
        } catch (e: Exception) {
            return "Kalendarni o'qib bo'lmadi."
        }
        return if (lines.isEmpty()) "Bu vaqtda rejalashtirilgan voqea yo'q."
        else "Rejalaringiz:\n" + lines.joinToString("\n")
    }

    // ------------------------------------------------------------ email, veb, musiqa

    fun email(ctx: Context, to: String, subject: String, body: String): String {
        val address = if (to.contains("@")) to.trim() else ContactsHelper.findEmail(ctx, to).orEmpty()
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
        if (address.isNotBlank()) i.putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
        return if (start(ctx, i)) {
            if (address.isBlank() && to.isNotBlank()) "Xat tayyorlandi, \"$to\" ning emaili topilmadi. Manzilni o'zingiz kiriting."
            else "Xat tayyor, yuborish tugmasini bosing."
        } else "Email ilovasi topilmadi."
    }

    fun openUrl(ctx: Context, raw: String): Boolean {
        var u = raw.trim()
        if (u.isEmpty()) return false
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        return start(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(u)))
    }

    fun music(ctx: Context, query: String, app: String): Boolean {
        val pkg = when (app.trim().lowercase(Locale.ROOT)) {
            "spotify" -> "com.spotify.music"
            "youtube music", "yt music", "ytmusic" -> "com.google.android.apps.youtube.music"
            else -> null
        }
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
        if (pkg != null) i.setPackage(pkg)
        return start(ctx, i)
    }

    // ------------------------------------------------------------ joylashuv (ob-havo uchun)

    fun lastLocation(ctx: Context): Pair<Double, Double>? {
        if (!has(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) && !has(ctx, Manifest.permission.ACCESS_FINE_LOCATION)) {
            return null
        }
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (p in lm.getProviders(true)) {
            val l = try {
                lm.getLastKnownLocation(p)
            } catch (e: SecurityException) {
                null
            } ?: continue
            val b = best
            if (b == null || l.time > b.time) best = l
        }
        val r = best ?: return null
        return r.latitude to r.longitude
    }
}
