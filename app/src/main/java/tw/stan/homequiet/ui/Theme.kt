package tw.stan.homequiet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Ink = Color(0xFF000000)
val Paper = Color(0xFFF3F3F3)
val Mute = Color(0xFF8E8E8E)
val Faint = Color(0xFF5C5C5C)
val Line = Color(0xFF242424)
val LineStrong = Color(0xFF3A3A3A)

private val Scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    secondary = Mute,
    onSecondary = Color.Black,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Color(0xFF141414),
    onSurfaceVariant = Mute,
    outline = Line,
    error = Color(0xFFFF5A45),
    onError = Color.Black,
)

private val Type = Typography(
    displayLarge = TextStyle(
        fontSize = 64.sp,
        lineHeight = 68.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-1).sp,
        color = Paper,
    ),
    headlineLarge = TextStyle(
        fontSize = 36.sp,
        lineHeight = 42.sp,
        fontWeight = FontWeight.Normal,
        color = Paper,
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Normal,
        color = Paper,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Medium,
        color = Paper,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        color = Paper,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
        color = Mute,
    ),
    labelSmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
        color = Mute,
    ),
)

@Composable
fun HomeQuietTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = Type,
        content = content,
    )
}
