package uz.jarvis.app

import android.content.Context
import org.json.JSONObject

/** Serverdan kelgan buyruqni (yoki mahalliy ilova ochish buyrug'ini) bajaradi. */
object CommandRouter {

    data class Outcome(
        val message: String,
        val speak: Boolean = true,
        val close: Boolean = false,    // panelni yopish kerakmi
        val agentGoal: String? = null, // bo'sh bo'lmasa, panel yopilgach agent shu ishni bajaradi
        val afterClose: (() -> Unit)? = null, // panel yopilgandan keyin bajariladi (skrinshot kabi)
        val followUp: Boolean = false, // Jarvis savol berdi: javobni darhol tinglash kerak
    )

    private val api by lazy { JarvisApi() }

    private const val NEED_ACCESSIBILITY =
        "Buning uchun Jarvis sozlamasida \"Maxsus imkoniyatlar\"ni yoqing."

    private val callWords = setOf(
        "qongiroq", "qongirog", "qungiroq", "kongiroq", "qongroq", "qongiroqni",
        "позвони", "звони", "набери", "call", "dial", "phone"
    )
    private val callVerbs = setOf(
        "qil", "qilib", "qilgin", "qiling", "qilsang", "et", "ber", "bering", "qoy", "qoying",
        "kerak", "please", "pls"
    )
    private val filler = setOf(
        "jarvis", "jarvisga", "telefonga", "telefon", "telefondagi", "kirib", "kontaktiga", "kontaktga",
        "kontakti", "kontakt", "kontaktdagi", "raqamiga", "ga", "ni", "dagi", "ilovasiga", "ilovasi",
        "menga", "iltimos", "hozir", "bir", "to", "the", "contact", "my", "me", "в", "контакт", "контакту"
    )

    private fun clean(raw: String): String =
        raw.lowercase(java.util.Locale.ROOT)
            .replace(Regex("[\\u2018\\u2019\\u02BB\\u02BC`'\u00B4]"), "")
            .replace(Regex("[^\\p{L}\\p{N}+ ]"), " ")
            .replace(Regex("\\s+"), " ").trim()

    /** "Ayam kontaktiga qo'ng'iroq qil" kabi buyruqdan kontakt nomini ajratadi. Buyruq bo'lmasa null. */
    fun parseCall(raw: String): String? {
        val words = clean(raw).split(' ').filter { it.isNotEmpty() }
        if (words.none { it in callWords }) return null
        val name = words.filter { it !in callWords && it !in callVerbs && it !in filler }
            .joinToString(" ").trim()
        return name.ifEmpty { null }
    }

    /** Internetsiz, tezkor yo'l: "Telegramni och", "Ayamga qo'ng'iroq qil". Buyruq bo'lmasa null. */
    fun fastPath(ctx: Context, text: String): Outcome? {
        parseCall(text)?.let { target ->
            val digits = target.replace(" ", "")
            val isNumber = digits.length >= 3 && digits.all { it.isDigit() || it == '+' }
            val r = if (isNumber) Actions.call(ctx, null, digits) else Actions.call(ctx, target, null)
            return Outcome(r, close = true)
        }
        val appName = AppLauncher.parseOpenCommand(text) ?: return null
        val opened = AppLauncher.open(ctx, appName)
        return if (opened != null) Outcome("$opened ochilmoqda", close = true)
        else Outcome("\"$appName\" nomli ilova topilmadi.")
    }

    fun execute(ctx: Context, cmd: JSONObject, userText: String = ""): Outcome {
        val say = cmd.optString("say")
        fun s(default: String) = say.ifBlank { default }
        val hasAccessibility = JarvisAccessibilityService.instance != null

        return when (cmd.optString("action")) {
            "chat" -> {
                val reply = cmd.optString("reply").ifBlank { "Tushunmadim, qayta ayting." }
                Outcome(reply, followUp = cmd.optBoolean("followup", false) || reply.trim().endsWith("?"))
            }

            "call" -> Outcome(
                Actions.call(ctx, cmd.optString("contact"), cmd.optString("number")), close = true
            )

            "sms" -> Outcome(
                Actions.sms(ctx, cmd.optString("contact"), cmd.optString("number"), cmd.optString("message")),
                close = true,
            )

            "open_app" -> {
                val name = cmd.optString("app")
                val opened = AppLauncher.open(ctx, name)
                if (opened != null) Outcome("$opened ochilmoqda", close = true)
                else Outcome("\"$name\" nomli ilova topilmadi.")
            }

            "app_task" -> {
                val name = cmd.optString("app")
                val goal = cmd.optString("goal")
                if (!hasAccessibility) return Outcome(NEED_ACCESSIBILITY)
                val opened = AppLauncher.open(ctx, name)
                if (opened == null) Outcome("\"$name\" nomli ilova topilmadi.")
                else Outcome(s("$opened da bajaryapman"), close = true, agentGoal = "($opened ilovasi ochilgan) $goal")
            }

            "youtube" -> {
                val q = cmd.optString("query")
                val play = cmd.optBoolean("play", false)
                if (play && !hasAccessibility) return Outcome(NEED_ACCESSIBILITY)
                if (!Actions.youtube(ctx, q)) return Outcome("YouTube'ni ochib bo'lmadi.")
                Outcome(
                    s("YouTube'da \"$q\" qidirilmoqda"),
                    close = true,
                    agentGoal = if (play) "YouTube qidiruv natijalaridagi birinchi videoni oching va ijro eting" else null,
                )
            }

            "music" -> {
                val q = cmd.optString("query")
                val app = cmd.optString("app")
                if (DeviceActions.music(ctx, q, app)) {
                    Outcome(s("\"$q\" qo'yilmoqda"), close = true)
                } else if (Actions.youtube(ctx, q)) {
                    Outcome(
                        s("YouTube'da \"$q\" qidirilmoqda"), close = true,
                        agentGoal = if (hasAccessibility) "YouTube qidiruv natijalaridagi birinchi videoni oching va ijro eting" else null,
                    )
                } else Outcome("Musiqa ilovasi topilmadi.")
            }

            "alarm" -> Outcome(
                Actions.alarm(ctx, cmd.optInt("hour"), cmd.optInt("minute"), cmd.optString("label")),
                close = true,
            )

            "timer" -> Outcome(
                Actions.timer(ctx, cmd.optInt("seconds"), cmd.optString("label")), close = true
            )

            "flashlight" -> Outcome(Actions.flashlight(ctx, cmd.optBoolean("on", true)), close = true)

            "navigate" -> {
                val q = cmd.optString("query")
                if (Actions.navigate(ctx, q)) Outcome(s("Xaritada \"$q\" ochilmoqda"), close = true)
                else Outcome("Xaritani ochib bo'lmadi.")
            }

            "info" -> Outcome(
                when (cmd.optString("kind")) {
                    "time" -> DeviceActions.time()
                    "date" -> DeviceActions.date()
                    "battery" -> DeviceActions.battery(ctx)
                    else -> "Buni aniqlay olmadim."
                }
            )

            "volume" -> Outcome(
                DeviceActions.volume(ctx, cmd.optString("mode"), cmd.optInt("level", 50), cmd.optString("stream")),
                close = true,
            )

            "brightness" -> Outcome(
                DeviceActions.brightness(ctx, cmd.optString("mode"), cmd.optInt("level", 50)), close = true
            )

            "media" -> Outcome(DeviceActions.media(ctx, cmd.optString("key")), close = true)

            "settings" -> {
                val target = cmd.optString("target")
                val msg = DeviceActions.settings(ctx, target)
                val wantOn = if (cmd.has("on") && !cmd.isNull("on")) cmd.optBoolean("on") else null
                if (wantOn != null && hasAccessibility && target in DeviceActions.toggleable) {
                    val label = DeviceActions.settingLabel(target)
                    val verb = if (wantOn) "yoqing" else "o'chiring"
                    Outcome(
                        s(msg), close = true,
                        agentGoal = "Hozir ochilgan sozlamalar ekranida \"$label\" ni $verb. " +
                            "Agar u allaqachon shu holatda bo'lsa, hech narsani bosmang. Tugagach \"done\" deng."
                    )
                } else Outcome(msg, close = true)
            }

            "camera" -> Outcome(DeviceActions.camera(ctx, cmd.optString("mode")), close = true)

            "calendar_add" -> Outcome(
                DeviceActions.addEvent(ctx, cmd.optString("title"), cmd.optString("start"), cmd.optString("end")),
                close = true,
            )

            "calendar_read" -> Outcome(DeviceActions.readEvents(ctx, cmd.optString("from"), cmd.optString("to")))

            "email" -> Outcome(
                DeviceActions.email(ctx, cmd.optString("to"), cmd.optString("subject"), cmd.optString("body")),
                close = true,
            )

            "open_url" -> {
                val url = cmd.optString("url")
                if (DeviceActions.openUrl(ctx, url)) Outcome(s("Ochilmoqda"), close = true)
                else Outcome("Havolani ochib bo'lmadi.")
            }

            "weather" -> {
                val city = cmd.optString("city").trim()
                var lat: Double? = null
                var lon: Double? = null
                if (city.isEmpty()) {
                    val loc = DeviceActions.lastLocation(ctx)
                    if (loc == null) {
                        return Outcome(
                            "Qaysi shahar ob-havosi kerak? (Yoki sozlamalarda joylashuv ruxsatini bering.)",
                            followUp = true,
                        )
                    }
                    lat = loc.first
                    lon = loc.second
                }
                val r = api.weather(city, lat, lon, userText.ifBlank { "Ob-havo qanday?" })
                r.fold({ Outcome(it) }, { Outcome(it.message ?: "Ob-havoni olib bo'lmadi.") })
            }

            "global" -> {
                val name = cmd.optString("name")
                if (!hasAccessibility) return Outcome(NEED_ACCESSIBILITY)
                val label = when (name) {
                    "screenshot" -> "Skrinshot olinmoqda"
                    "lock" -> "Ekran qulflanmoqda"
                    "home" -> "Bosh ekran"
                    "back" -> "Orqaga"
                    "recents" -> "So'nggi ilovalar"
                    "notifications" -> "Bildirishnomalar"
                    "quick_settings" -> "Tezkor sozlamalar"
                    "power" -> "O'chirish menyusi"
                    else -> "Bajarilmoqda"
                }
                Outcome(s(label), close = true, afterClose = {
                    JarvisAccessibilityService.instance?.globalAction(name)
                })
            }

            "random" -> Outcome(
                DeviceActions.random(cmd.optString("kind"), cmd.optInt("min", 1), cmd.optInt("max", 100))
            )

            "agent" -> {
                if (!hasAccessibility) return Outcome(NEED_ACCESSIBILITY)
                Outcome(s("Bajaryapman"), close = true, agentGoal = cmd.optString("goal"))
            }

            else -> Outcome(cmd.optString("reply").ifBlank { "Buni hali bajara olmayman." })
        }
    }
}
