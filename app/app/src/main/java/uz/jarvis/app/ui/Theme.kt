package uz.jarvis.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object JarvisColors {
    val Bg = Color(0xFF07080F)
    val BgTop = Color(0xFF0B1030)
    val Surface = Color(0xFF111426)
    val Blue = Color(0xFF3D8BFF)
    val Cyan = Color(0xFF38BDF8)
    val Pink = Color(0xFFFF4FA3)
    val TextMain = Color(0xFFEAF0FF)
    val TextDim = Color(0xFF8E98B8)
    val Error = Color(0xFFFF6B8A)

    val Background = Brush.verticalGradient(listOf(BgTop, Bg, Color(0xFF12071A)))
    val Accent = Brush.linearGradient(listOf(Blue, Pink))
}

private val scheme = darkColorScheme(
    primary = JarvisColors.Blue,
    secondary = JarvisColors.Pink,
    background = JarvisColors.Bg,
    surface = JarvisColors.Surface,
    onPrimary = Color.White,
    onBackground = JarvisColors.TextMain,
    onSurface = JarvisColors.TextMain,
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
