package uz.jarvis.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import java.util.Locale

/** Kontaktni ism bo'yicha topish (lotin/kirill, imlo farqlariga chidamli). */
object ContactsHelper {

    data class Contact(val name: String, val number: String)

    private val cyr = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "j",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
        'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c",
        'ч' to "c", 'ш' to "s", 'щ' to "s", 'ъ' to "", 'ы' to "i", 'ь' to "", 'э' to "e", 'ю' to "yu",
        'я' to "ya", 'ў' to "o", 'қ' to "k", 'ғ' to "g", 'ҳ' to "h"
    )

    /** Ovoz bo'yicha qiyoslash uchun soddalashtirilgan kalit. */
    fun key(raw: String): String {
        val sb = StringBuilder()
        for (ch in raw.lowercase(Locale.ROOT)) {
            val mapped = cyr[ch]
            if (mapped != null) sb.append(mapped) else if (ch.isLetterOrDigit()) sb.append(ch)
        }
        var s = sb.toString()
        s = s.replace("sh", "s").replace("ch", "c").replace("kh", "h").replace("ts", "c")
            .replace("x", "h").replace("q", "k").replace("w", "v").replace("yo", "o")
            .replace("yu", "u").replace("ya", "a").replace("y", "i").replace("j", "z")
        // takroriy harflarni birlashtirish
        val out = StringBuilder()
        for (c in s) if (out.isEmpty() || out.last() != c) out.append(c)
        return out.toString()
    }

    private fun distance(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    fun find(ctx: Context, query: String): Contact? {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) return null
        val q = key(query)
        if (q.isEmpty()) return null

        var best: Contact? = null
        var bestScore = 0.0
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                val k = key(name)
                if (k.isEmpty()) continue
                val score = when {
                    k == q -> 4.0
                    k.startsWith(q) || q.startsWith(k) -> 3.0
                    k.contains(q) || q.contains(k) -> 2.0
                    else -> {
                        val d = distance(k, q)
                        val allowed = if (q.length >= 7) 3 else if (q.length >= 4) 2 else 0
                        if (d <= allowed) 1.5 - d * 0.1 else 0.0
                    }
                }
                if (score > bestScore) {
                    bestScore = score
                    best = Contact(name, number)
                }
            }
        }
        return best
    }
}
