package uz.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * Agent: ekranni o'qiydi → serverdagi AI keyingi qadamni aytadi → bajaradi. Maqsad bajarilguncha takrorlaydi.
 */
object AgentRunner {

    private const val MAX_STEPS = 20
    private val api = JarvisApi()
    private val main = Handler(Looper.getMainLooper())

    @Volatile private var thread: Thread? = null
    @Volatile private var cancelled = false

    fun isRunning() = thread?.isAlive == true

    fun stop() { cancelled = true }

    fun run(ctx: Context, goal: String) {
        val app = ctx.applicationContext
        if (isRunning()) return
        cancelled = false
        thread = Thread {
            main.post { OverlayController.showStatus(app, "Jarvis ishlayapti…") { stop() } }
            val msg = try {
                loop(app, goal)
            } catch (e: InterruptedException) {
                "To'xtatildi"
            } catch (e: Exception) {
                e.message ?: "Xatolik yuz berdi"
            }
            main.post {
                OverlayController.showStatus(app, msg, null)
                OverlayController.hideStatus(3500)
            }
            Speaker.speak(app, msg)
        }.also { it.start() }
    }

    private fun loop(app: Context, goal: String): String {
        val history = mutableListOf<String>()
        Thread.sleep(1500) // ilova ochilib bo'lishini kutamiz
        var emptyScreens = 0
        for (step in 1..MAX_STEPS) {
            if (cancelled) return "To'xtatildi"
            val svc = JarvisAccessibilityService.instance
                ?: return "Maxsus imkoniyatlar (Accessibility) yoqilmagan."
            val snap = svc.snapshot()
            if (snap == null) {
                if (++emptyScreens > 4) return "Ekranni o'qib bo'lmadi."
                Thread.sleep(900)
                continue
            }
            emptyScreens = 0

            val obj = api.agent(goal, snap.text, history, snap.pkg).getOrElse { return it.message ?: "Server xatosi" }
            val action = obj.optString("action")
            val id = obj.optInt("id", -1)
            val node = snap.nodes[id]
            val label = snap.labels[id].orEmpty()

            val entry: String = when (action) {
                "done" -> return obj.optString("say").ifBlank { "Tayyor" }
                "fail" -> return obj.optString("say").ifBlank { "Bajara olmadim" }
                "click" -> {
                    val ok = node != null && svc.click(node)
                    "$step) click [$id] \"$label\" -> ${if (ok) "ok" else "xato"}"
                }
                "type" -> {
                    val t = obj.optString("text")
                    val ok = node != null && svc.type(node, t)
                    "$step) type [$id] \"$t\" -> ${if (ok) "ok" else "xato"}"
                }
                "scroll" -> {
                    val down = obj.optString("direction", "down") != "up"
                    val ok = svc.scroll(node?.takeIf { it.isScrollable } ?: snap.scrollable, down)
                    "$step) scroll ${if (down) "down" else "up"} -> ${if (ok) "ok" else "xato"}"
                }
                "back" -> {
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    "$step) back"
                }
                "home" -> {
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    "$step) home"
                }
                "notifications", "quick_settings", "recents", "screenshot", "lock" -> {
                    val ok = svc.globalAction(action)
                    "$step) $action -> ${if (ok) "ok" else "xato"}"
                }
                "open_app" -> {
                    val name = obj.optString("text")
                    val opened = AppLauncher.open(app, name)
                    "$step) open_app \"$name\" -> ${opened ?: "topilmadi"}"
                }
                else -> "$step) wait"
            }
            history.add(entry)

            // Bir xil amal ketma-ket 3 marta takrorlansa — to'xtaymiz
            if (history.size >= 3) {
                val last = history.takeLast(3).map { it.substringAfter(") ") }
                if (last.distinct().size == 1 && !last[0].startsWith("wait")) {
                    return "Bir joyda qotib qoldim, to'xtadim."
                }
            }
            Thread.sleep(1100)
        }
        return "Juda uzoq davom etdi, to'xtadim."
    }
}
