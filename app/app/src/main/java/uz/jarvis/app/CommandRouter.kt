package uz.jarvis.app

import android.content.Context
import org.json.JSONObject

/** Serverdan kelgan buyruqni (yoki mahalliy ilova ochish buyrug'ini) bajaradi. */
object CommandRouter {

    data class Outcome(
        val message: String,
        val speak: Boolean = true,
        val close: Boolean = false,   // panelni yopish kerakmi
        val agentGoal: String? = null, // bo'sh bo'lmasa, panel yopilgach agent shu ishni bajaradi
    )

    private const val NEED_ACCESSIBILITY =
        "Buning uchun Jarvis sozlamasida \"Maxsus imkoniyatlar\"ni yoqing."

    /** Internetsiz, tezkor yo'l: "Telegramni och". Buyruq bo'lmasa null. */
    fun fastPath(ctx: Context, text: String): Outcome? {
        val appName = AppLauncher.parseOpenCommand(text) ?: return null
        val opened = AppLauncher.open(ctx, appName)
        return if (opened != null) Outcome("$opened ochilmoqda", close = true)
        else Outcome("\"$appName\" nomli ilova topilmadi.")
    }

    fun execute(ctx: Context, cmd: JSONObject): Outcome {
        val say = cmd.optString("say")
        fun s(default: String) = say.ifBlank { default }
        val hasAccessibility = JarvisAccessibilityService.instance != null

        return when (cmd.optString("action")) {
            "chat" -> Outcome(cmd.optString("reply").ifBlank { "Tushunmadim, qayta ayting." })

            "call" -> {
                val r = Actions.call(ctx, cmd.optString("contact"), cmd.optString("number"))
                Outcome(r, close = true)
            }

            "sms" -> {
                val r = Actions.sms(
                    ctx, cmd.optString("contact"), cmd.optString("number"), cmd.optString("message")
                )
                Outcome(r, close = true)
            }

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

            "alarm" -> Outcome(
                Actions.alarm(ctx, cmd.optInt("hour"), cmd.optInt("minute"), cmd.optString("label")),
                close = true,
            )

            "timer" -> Outcome(
                Actions.timer(ctx, cmd.optInt("seconds"), cmd.optString("label")),
                close = true,
            )

            "flashlight" -> Outcome(Actions.flashlight(ctx, cmd.optBoolean("on", true)), close = true)

            "navigate" -> {
                val q = cmd.optString("query")
                if (Actions.navigate(ctx, q)) Outcome(s("Xaritada \"$q\" ochilmoqda"), close = true)
                else Outcome("Xaritani ochib bo'lmadi.")
            }

            "agent" -> {
                if (!hasAccessibility) return Outcome(NEED_ACCESSIBILITY)
                Outcome(s("Bajaryapman"), close = true, agentGoal = cmd.optString("goal"))
            }

            else -> Outcome(cmd.optString("reply").ifBlank { "Buni hali bajara olmayman." })
        }
    }
}
