package uz.jarvis.app.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import uz.jarvis.app.Perms
import uz.jarvis.app.Prefs
import uz.jarvis.app.WakeWordService

private class Step(
    val id: String,
    val title: String,
    val desc: String,
    val granted: Boolean,
    val request: () -> Unit,
)

@Composable
fun SetupScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var auto by remember { mutableStateOf(false) }
    var wake by remember { mutableStateOf(Prefs.wakeEnabled(ctx)) }
    val attempted = remember { mutableSetOf<String>() }

    // Sozlamalardan qaytganda holatni qayta tekshiramiz
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }

    fun open(i: Intent) {
        try { settings.launch(i) } catch (e: Exception) {
            try { settings.launch(Perms.appDetailsIntent(ctx)) } catch (_: Exception) {}
        }
    }

    val refresh = tick // holat o'zgarganda qayta chizish uchun
    val steps = buildList {
        fun rt(id: String, title: String, desc: String, perm: String) =
            add(Step(id, title, desc, Perms.granted(ctx, perm)) { runtime.launch(perm) })
        rt("mic", "Mikrofon", "Ovozingizni eshitish uchun", Manifest.permission.RECORD_AUDIO)
        rt("cam", "Kamera", "Fonarni yoqish uchun", Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= 33) {
            rt("notif", "Bildirishnomalar", "Jarvis ishlayotganini ko'rsatish uchun", Manifest.permission.POST_NOTIFICATIONS)
        }
        rt("contacts", "Kontaktlar", "Ism bo'yicha kontaktni topish uchun", Manifest.permission.READ_CONTACTS)
        rt("call", "Qo'ng'iroq", "Qo'ng'iroq qilish uchun", Manifest.permission.CALL_PHONE)
        rt("sms", "SMS", "SMS yuborish uchun", Manifest.permission.SEND_SMS)
        add(Step("overlay", "Boshqa ilovalar ustida ko'rsatish", "Panel istalgan ekran ustida chiqishi uchun",
            Perms.overlay(ctx)) { open(Perms.overlayIntent(ctx)) })
        add(Step("access", "Maxsus imkoniyatlar", "Boshqa ilovalar ichida bosish va yozish uchun (Plus, YouTube...). Ro'yxatdan Jarvis'ni toping va yoqing",
            Perms.accessibility(ctx)) { open(Perms.accessibilityIntent()) })
        add(Step("assistant", "Standart yordamchi ilova", "Yon tugmani uzoq bosganda Jarvis chiqishi uchun",
            Perms.assistant(ctx)) { open(Perms.assistantIntent(ctx)) })
        add(Step("battery", "Batareya cheklovisiz", "Fonda doim tinglashi uchun",
            Perms.battery(ctx)) { open(Perms.batteryIntent(ctx)) })
    }

    // "Hammasini ruxsat berish": navbatma-navbat so'raydi
    LaunchedEffect(tick, auto) {
        if (!auto) return@LaunchedEffect
        val next = steps.firstOrNull { !it.granted && it.id !in attempted }
        if (next == null) auto = false else {
            attempted.add(next.id)
            next.request()
        }
    }

    val allDone = steps.all { it.granted }

    Box(Modifier.fillMaxSize().background(JarvisColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text("Jarvis'ni sozlash", color = JarvisColors.TextMain, fontSize = 24.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp))
            Text("Jarvis to'liq ishlashi uchun quyidagi ruxsatlar kerak.",
                color = JarvisColors.TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(steps) { StepRow(it) }
                item {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(JarvisColors.Surface)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Doim tinglash", color = JarvisColors.TextMain, fontWeight = FontWeight.SemiBold)
                            Text("\"Jarvis\" desangiz panel ochiladi", color = JarvisColors.TextDim, fontSize = 12.sp)
                        }
                        Switch(
                            checked = wake,
                            onCheckedChange = {
                                wake = it
                                Prefs.setWakeEnabled(ctx, it)
                                if (it) WakeWordService.start(ctx) else WakeWordService.stop(ctx)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = JarvisColors.Blue),
                        )
                    }
                }
                item {
                    Text(
                        "Agar \"Maxsus imkoniyatlar\" yoqilmasa va \"Cheklangan sozlama\" chiqsa: Sozlamalar → Ilovalar → Jarvis → ⋮ (yuqori o'ng burchak) → \"Cheklangan sozlamalarga ruxsat berish\", so'ng qayta urinib ko'ring.",
                        color = JarvisColors.TextDim, fontSize = 12.sp, modifier = Modifier.padding(4.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            if (!allDone) {
                BigButton("Hammasini ruxsat berish", filled = true) { attempted.clear(); auto = true; tick++ }
                Spacer(Modifier.height(8.dp))
            }
            BigButton(if (allDone) "Boshlash" else "Keyinroq", filled = allDone) {
                Prefs.setSetupDone(ctx, true)
                if (Prefs.wakeEnabled(ctx)) WakeWordService.start(ctx)
                onDone()
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StepRow(s: Step) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(JarvisColors.Surface)
            .border(BorderStroke(1.dp, if (s.granted) JarvisColors.Blue.copy(alpha = 0.5f) else Color.Transparent),
                RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(s.title, color = JarvisColors.TextMain, fontWeight = FontWeight.SemiBold)
            Text(s.desc, color = JarvisColors.TextDim, fontSize = 12.sp)
        }
        if (s.granted) {
            Text("✓", color = JarvisColors.Cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        } else {
            Text(
                "Berish", color = Color.White, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(JarvisColors.Accent)
                    .clickable { s.request() }.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun BigButton(text: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .then(if (filled) Modifier.background(JarvisColors.Accent)
                  else Modifier.border(BorderStroke(1.dp, JarvisColors.Blue.copy(alpha = 0.5f)), RoundedCornerShape(26.dp)))
            .clickable(onClick = onClick).padding(vertical = 15.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp) }
}
