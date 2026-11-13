package uz.jarvis.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import uz.jarvis.app.Chat
import uz.jarvis.app.JarvisViewModel
import uz.jarvis.app.Msg
import uz.jarvis.app.Speaker
import uz.jarvis.app.VoiceController

private val suggestions = listOf(
    "Telegramni och",
    "Ayamga qo'ng'iroq qil",
    "Ertaga soat 6:30 ga budilnik qo'y",
    "Menga qiziq fakt ayt",
)

@Composable
fun ChatScreen(vm: JarvisViewModel = viewModel(), autoListenTrigger: Int = 0, onOpenSetup: () -> Unit = {}) {
    val ctx = LocalContext.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val chats by vm.chats.collectAsStateWithLifecycle()
    val currentId by vm.currentId.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var showSaved by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

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

    // Yon tugma yoki "Jarvis" orqali chaqirilganda darhol tinglaydi
    LaunchedEffect(autoListenTrigger) {
        if (autoListenTrigger > 0 && !listening) onMicClick()
    }

    fun submit() {
        if (input.isNotBlank()) {
            vm.send(input)
            input = ""
        }
    }

    LaunchedEffect(messages.size, loading, currentId) {
        val last = messages.size + (if (loading) 1 else 0) - 1
        if (last >= 0) listState.scrollToItem(last)
    }

    BackHandler(enabled = drawerState.isOpen || showSaved) {
        if (drawerState.isOpen) scope.launch { drawerState.close() } else showSaved = false
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            DrawerContent(
                chats = chats,
                currentId = currentId,
                savedCount = saved.size,
                onNewChat = {
                    vm.newChat(); showSaved = false
                    scope.launch { drawerState.close() }
                },
                onOpenChat = {
                    vm.openChat(it); showSaved = false
                    scope.launch { drawerState.close() }
                },
                onDelete = { vm.deleteChat(it) },
                onSaved = {
                    showSaved = true
                    scope.launch { drawerState.close() }
                },
                onSettings = {
                    scope.launch { drawerState.close() }
                    onOpenSetup()
                },
            )
        },
    ) {
        Box(Modifier.fillMaxSize().background(JarvisColors.Background)) {
            if (showSaved) {
                SavedScreen(
                    items = saved,
                    onBack = { showSaved = false },
                    onCopy = { copyText(ctx, it.text) },
                    onShare = { shareText(ctx, it.text) },
                    onUnsave = { vm.toggleSave(it.id) },
                )
            } else {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                    TopBar(
                        title = chats.firstOrNull { it.id == currentId }?.title ?: "Jarvis",
                        listening = listening,
                        onMenu = { scope.launch { drawerState.open() } },
                        onNew = { vm.newChat() },
                    )

                    if (messages.isEmpty() && !loading) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            EmptyState(onPick = { vm.send(it) })
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(messages, key = { it.id }) { m ->
                                if (m.fromUser) UserBubble(m)
                                else AssistantMessage(
                                    m = m,
                                    onCopy = { copyText(ctx, m.text) },
                                    onShare = { shareText(ctx, m.text) },
                                    onSave = { vm.toggleSave(m.id) },
                                    onLike = { vm.react(m.id, 1) },
                                    onDislike = { vm.react(m.id, -1) },
                                    onSpeak = { Speaker.speak(ctx, m.text) },
                                )
                            }
                            if (loading) item { TypingBubble() }
                        }
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
    }
}

// ---------------------------------------------------------------- yuqori panel

@Composable
private fun TopBar(title: String, listening: Boolean, onMenu: () -> Unit, onNew: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Icons.Filled.Menu, "Menyu", onClick = onMenu)
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(
                title, color = JarvisColors.TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (listening) "Eshityapman…" else "Shaxsiy AI yordamchi",
                color = if (listening) JarvisColors.Pink else JarvisColors.TextDim,
                fontSize = 12.sp,
            )
        }
        IconBtn(Icons.Outlined.Edit, "Yangi suhbat", onClick = onNew)
    }
}

@Composable
private fun IconBtn(
    icon: ImageVector,
    desc: String,
    size: Dp = 44.dp,
    tint: Color = JarvisColors.TextMain,
    onClick: () -> Unit,
) {
    Box(Modifier.size(size).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------- bosh ekran

@Composable
private fun GlowOrb(size: Dp) {
    val pulse = rememberInfiniteTransition(label = "glow")
    val s by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "glowScale",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 1.6f).scale(s).alpha(0.16f).clip(CircleShape).background(JarvisColors.Accent))
        Box(Modifier.size(size * 1.25f).alpha(0.30f).clip(CircleShape).background(JarvisColors.Accent))
        Box(Modifier.size(size).clip(CircleShape).background(JarvisColors.Accent))
    }
}

@Composable
private fun EmptyState(onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlowOrb(72.dp)
        Spacer(Modifier.height(36.dp))
        Text("Salom, men Jarvis", color = JarvisColors.TextMain, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Yozing yoki mikrofonni bosib gapiring.\nYoki \"Jarvis\" deb chaqiring.",
            color = JarvisColors.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(28.dp))
        suggestions.forEach { s ->
            Text(
                s, color = JarvisColors.TextMain, fontSize = 14.sp,
                modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(JarvisColors.Card)
                    .border(BorderStroke(1.dp, JarvisColors.Line), RoundedCornerShape(16.dp))
                    .clickable { onPick(s) }
                    .padding(horizontal = 16.dp, vertical = 13.dp),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

// ---------------------------------------------------------------- xabarlar

@Composable
private fun UserBubble(m: Msg) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        SelectionContainer {
            Text(
                m.text,
                modifier = Modifier.widthIn(max = 310.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 5.dp))
                    .background(JarvisColors.Accent)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                color = Color.White, fontSize = 15.sp, lineHeight = 21.sp,
            )
        }
    }
}

@Composable
private fun AssistantMessage(
    m: Msg,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onSpeak: () -> Unit,
) {
    val shape = RoundedCornerShape(topStart = 5.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    Column(Modifier.fillMaxWidth()) {
        SelectionContainer {
            Text(
                m.text,
                modifier = Modifier.widthIn(max = 340.dp).clip(shape)
                    .background(JarvisColors.Card)
                    .border(
                        BorderStroke(1.dp, if (m.isError) JarvisColors.Error else JarvisColors.Line),
                        shape,
                    )
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                color = if (m.isError) JarvisColors.Error else JarvisColors.TextMain,
                fontSize = 15.sp, lineHeight = 22.sp,
            )
        }
        if (!m.isError) {
            Row(Modifier.padding(top = 4.dp, start = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                ActionIcon(Icons.Outlined.ContentCopy, "Nusxa olish", onClick = onCopy)
                ActionIcon(Icons.Outlined.Share, "Ulashish", onClick = onShare)
                ActionIcon(
                    if (m.saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    "Saqlash", active = m.saved, onClick = onSave,
                )
                ActionIcon(
                    if (m.reaction == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    "Yoqdi", active = m.reaction == 1, onClick = onLike,
                )
                ActionIcon(
                    if (m.reaction == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    "Yoqmadi", active = m.reaction == -1, onClick = onDislike,
                )
                ActionIcon(Icons.Outlined.VolumeUp, "Ovozda o'qish", onClick = onSpeak)
            }
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, desc: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, desc,
            tint = if (active) JarvisColors.Cyan else JarvisColors.TextDim,
            modifier = Modifier.size(18.dp),
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
            Modifier.clip(RoundedCornerShape(20.dp)).background(JarvisColors.Card)
                .border(BorderStroke(1.dp, JarvisColors.Line), RoundedCornerShape(20.dp))
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

// ---------------------------------------------------------------- yozish paneli

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
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f)
                .border(BorderStroke(1.dp, JarvisColors.Line), RoundedCornerShape(26.dp)),
            placeholder = { Text("Jarvisga yozing…", color = JarvisColors.TextDim) },
            shape = RoundedCornerShape(26.dp),
            maxLines = 5,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = JarvisColors.Card,
                unfocusedContainerColor = JarvisColors.Card,
                focusedTextColor = JarvisColors.TextMain,
                unfocusedTextColor = JarvisColors.TextMain,
                cursorColor = JarvisColors.Pink,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )

        if (value.isNotBlank()) {
            RoundButton(onClick = onSend) {
                Icon(Icons.AutoMirrored.Filled.Send, "Yuborish", tint = Color.White)
            }
        } else {
            val pulse = rememberInfiniteTransition(label = "mic")
            val s by pulse.animateFloat(
                initialValue = 1f, targetValue = 1.15f,
                animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "micScale",
            )
            RoundButton(onClick = onMic, modifier = Modifier.scale(if (listening) s else 1f)) {
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
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier.size(52.dp).clip(CircleShape)
            .background(JarvisColors.Accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------------------------------------------------------------- yon panel

@Composable
private fun DrawerContent(
    chats: List<Chat>,
    currentId: String?,
    savedCount: Int,
    onNewChat: () -> Unit,
    onOpenChat: (String) -> Unit,
    onDelete: (String) -> Unit,
    onSaved: () -> Unit,
    onSettings: () -> Unit,
) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val shown = chats
        .sortedByDescending { it.updatedAt }
        .filter { c ->
            query.isBlank() || c.title.contains(query, ignoreCase = true) ||
                c.messages.any { it.text.contains(query, ignoreCase = true) }
        }

    ModalDrawerSheet(
        modifier = Modifier.width(320.dp),
        drawerContainerColor = JarvisColors.Drawer,
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (searching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).border(BorderStroke(1.dp, JarvisColors.Line), RoundedCornerShape(22.dp)),
                        placeholder = { Text("Suhbatlardan qidirish", color = JarvisColors.TextDim) },
                        singleLine = true,
                        shape = RoundedCornerShape(22.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = JarvisColors.Card,
                            unfocusedContainerColor = JarvisColors.Card,
                            focusedTextColor = JarvisColors.TextMain,
                            unfocusedTextColor = JarvisColors.TextMain,
                            cursorColor = JarvisColors.Pink,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                    IconBtn(Icons.Filled.Close, "Yopish") { searching = false; query = "" }
                } else {
                    Text(
                        "Jarvis", color = JarvisColors.TextMain, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    IconBtn(Icons.Filled.Search, "Qidirish") { searching = true }
                }
            }

            Spacer(Modifier.height(10.dp))
            DrawerItem(Icons.Outlined.Edit, "Yangi suhbat", onClick = onNewChat)
            DrawerItem(
                Icons.Outlined.BookmarkBorder, "Saqlanganlar",
                trailing = if (savedCount > 0) savedCount.toString() else null, onClick = onSaved,
            )
            DrawerItem(Icons.Outlined.Settings, "Sozlamalar va ruxsatlar", onClick = onSettings)

            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = JarvisColors.Line)
            Text(
                "So'nggi suhbatlar", color = JarvisColors.TextDim, fontSize = 13.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )

            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (shown.isEmpty()) {
                    item {
                        Text(
                            if (query.isBlank()) "Hozircha suhbat yo'q" else "Hech narsa topilmadi",
                            color = JarvisColors.TextDim, fontSize = 14.sp,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                }
                items(shown, key = { it.id }) { c ->
                    ChatRow(c, selected = c.id == currentId, onClick = { onOpenChat(c.id) }, onDelete = { onDelete(c.id) })
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 14.dp).navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(JarvisColors.Accent).clickable(onClick = onSettings),
                    contentAlignment = Alignment.Center,
                ) { Text("J", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(26.dp)).background(JarvisColors.Accent)
                        .clickable(onClick = onNewChat).padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Yangi suhbat", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = JarvisColors.TextMain, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = JarvisColors.TextMain, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = JarvisColors.TextDim, fontSize = 14.sp)
    }
}

@Composable
private fun ChatRow(chat: Chat, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (selected) JarvisColors.Selected else Color.Transparent)
            .clickable(onClick = onClick).padding(start = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ChatBubbleOutline, null, tint = JarvisColors.TextDim, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            chat.title, color = JarvisColors.TextMain, fontSize = 15.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        Box {
            IconBtn(Icons.Filled.MoreVert, "Ko'proq", size = 40.dp, tint = JarvisColors.TextDim) { menu = true }
            DropdownMenu(
                expanded = menu, onDismissRequest = { menu = false },
                modifier = Modifier.background(JarvisColors.Card),
            ) {
                DropdownMenuItem(
                    text = { Text("O'chirish", color = JarvisColors.Error) },
                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = JarvisColors.Error) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- saqlanganlar

@Composable
private fun SavedScreen(
    items: List<Msg>,
    onBack: () -> Unit,
    onCopy: (Msg) -> Unit,
    onShare: (Msg) -> Unit,
    onUnsave: (Msg) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Orqaga", onClick = onBack)
            Text(
                "Saqlanganlar", color = JarvisColors.TextMain, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (items.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.BookmarkBorder, null, tint = JarvisColors.TextDim, modifier = Modifier.size(44.dp))
                Spacer(Modifier.height(12.dp))
                Text("Hali hech narsa saqlanmagan", color = JarvisColors.TextMain, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Javob ostidagi xatcho'p tugmasini bosing.",
                    color = JarvisColors.TextDim, fontSize = 14.sp, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.id }) { m ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(JarvisColors.Card)
                            .border(BorderStroke(1.dp, JarvisColors.Line), RoundedCornerShape(18.dp))
                            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
                    ) {
                        SelectionContainer {
                            Text(m.text, color = JarvisColors.TextMain, fontSize = 15.sp, lineHeight = 22.sp,
                                modifier = Modifier.padding(end = 8.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            ActionIcon(Icons.Outlined.ContentCopy, "Nusxa olish") { onCopy(m) }
                            ActionIcon(Icons.Outlined.Share, "Ulashish") { onShare(m) }
                            ActionIcon(Icons.Filled.Bookmark, "Saqlanganlardan olib tashlash", active = true) { onUnsave(m) }
                        }
                    }
                }
            }
        }
    }
}
