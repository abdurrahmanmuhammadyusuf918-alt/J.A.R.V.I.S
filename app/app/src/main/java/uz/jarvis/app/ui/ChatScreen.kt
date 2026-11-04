package uz.jarvis.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import uz.jarvis.app.JarvisViewModel
import uz.jarvis.app.Msg
import uz.jarvis.app.VoiceController

@Composable
fun ChatScreen(vm: JarvisViewModel = viewModel()) {
    val ctx = LocalContext.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val voice = remember {
        VoiceController(
            context = ctx.applicationContext,
            onResult = { vm.send(it, fromVoice = true) },
            onListening = { listening = it },
            onError = { vm.showError(it) },
        )
    }
    DisposableEffect(Unit) { onDispose { voice.destroy() } }
    LaunchedEffect(Unit) { vm.speakEvents.collect { voice.speak(it) } }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) voice.startListening() else vm.showError("Ovozli buyruq uchun mikrofon ruxsati kerak.")
    }

    fun onMicClick() {
        when {
            listening -> voice.stop()
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> voice.startListening()
            else -> permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun submit() {
        if (input.isNotBlank()) {
            vm.send(input)
            input = ""
        }
    }

    LaunchedEffect(messages.size, loading) {
        val last = messages.size + (if (loading) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Box(Modifier.fillMaxSize().background(JarvisColors.Background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Header(listening)

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages) { Bubble(it) }
                if (loading) item { TypingBubble() }
            }

            InputBar(
                value = input,
                onValueChange = { input = it },
                listening = listening,
                onSend = ::submit,
                onMic = ::onMicClick,
            )
        }
    }
}

@Composable
private fun Header(listening: Boolean) {
    val pulse = rememberInfiniteTransition(label = "orb")
    val s by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.25f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "orbScale",
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(34.dp).scale(if (listening) s else 1f).alpha(0.35f)
                    .clip(CircleShape).background(JarvisColors.Accent)
            )
            Box(Modifier.size(20.dp).clip(CircleShape).background(JarvisColors.Accent))
        }
        Spacer(Modifier.size(14.dp))
        Column {
            Text("JARVIS", color = JarvisColors.TextMain, fontSize = 22.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Text(
                if (listening) "Eshityapman…" else "Shaxsiy AI yordamchi",
                color = if (listening) JarvisColors.Pink else JarvisColors.TextDim,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun Bubble(m: Msg) {
    val shape = RoundedCornerShape(
        topStart = 20.dp, topEnd = 20.dp,
        bottomStart = if (m.fromUser) 20.dp else 4.dp,
        bottomEnd = if (m.fromUser) 4.dp else 20.dp,
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        val base = Modifier.widthIn(max = 300.dp).clip(shape)
        val styled = when {
            m.fromUser -> base.background(JarvisColors.Accent)
            m.isError -> base.background(JarvisColors.Surface)
                .border(BorderStroke(1.dp, JarvisColors.Error), shape)
            else -> base.background(JarvisColors.Surface)
                .border(BorderStroke(1.dp, JarvisColors.Blue.copy(alpha = 0.35f)), shape)
        }
        Text(
            m.text,
            modifier = styled.padding(horizontal = 14.dp, vertical = 10.dp),
            color = if (m.isError) JarvisColors.Error else Color.White,
            fontSize = 15.sp, lineHeight = 21.sp,
        )
    }
}

@Composable
private fun TypingBubble() {
    val t = rememberInfiniteTransition(label = "typing")
    val a by t.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dots",
    )
    Row(Modifier.fillMaxWidth()) {
        Row(
            Modifier.clip(RoundedCornerShape(20.dp)).background(JarvisColors.Surface)
                .border(BorderStroke(1.dp, JarvisColors.Blue.copy(alpha = 0.35f)), RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { i ->
                Box(
                    Modifier.size(8.dp).alpha(if (i == 1) 1.3f - a else a).clip(CircleShape)
                        .background(if (i == 1) JarvisColors.Pink else JarvisColors.Cyan)
                )
            }
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    listening: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f)
                .border(BorderStroke(1.dp, JarvisColors.Blue.copy(alpha = 0.4f)), RoundedCornerShape(28.dp)),
            placeholder = { Text("Jarvisga yozing…", color = JarvisColors.TextDim) },
            shape = RoundedCornerShape(28.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = JarvisColors.Surface,
                unfocusedContainerColor = JarvisColors.Surface,
                focusedTextColor = JarvisColors.TextMain,
                unfocusedTextColor = JarvisColors.TextMain,
                cursorColor = JarvisColors.Pink,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        if (value.isNotBlank()) {
            RoundButton(onClick = onSend, filled = true) {
                Icon(Icons.AutoMirrored.Filled.Send, "Yuborish", tint = Color.White)
            }
        } else {
            val pulse = rememberInfiniteTransition(label = "mic")
            val s by pulse.animateFloat(
                initialValue = 1f, targetValue = 1.15f,
                animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "micScale",
            )
            RoundButton(onClick = onMic, filled = true, modifier = Modifier.scale(if (listening) s else 1f)) {
                Icon(
                    if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
                    if (listening) "To'xtatish" else "Gapirish",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun RoundButton(
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(52.dp).clip(CircleShape)
            .background(if (filled) JarvisColors.Accent else Brush.linearGradient(listOf(JarvisColors.Surface, JarvisColors.Surface)))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}
