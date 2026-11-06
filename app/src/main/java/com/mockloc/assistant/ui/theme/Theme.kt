package com.mockloc.assistant.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.activity.compose.LocalActivity
import com.mockloc.assistant.data.model.ThemeMode

private val BrandBlue = Color(0xFF3F6BFF)
private val BrandBlueLight = Color(0xFF7C9BFF)
private val BrandCyan = Color(0xFF33D6C3)
private val Ink = Color(0xFF0B1220)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3EBFF),
    onPrimaryContainer = Color(0xFF0B2C7A),
    secondary = Color(0xFF12B89F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD6F7F1),
    onSecondaryContainer = Color(0xFF014D43),
    tertiary = Color(0xFF7A5CFF),
    background = Color(0xFFF5F7FB),
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFF2F8),
    onSurfaceVariant = Color(0xFF5C6478),
    outline = Color(0xFFC9CFDD),
    outlineVariant = Color(0xFFE2E7F0),
    error = Color(0xFFE5484D),
    onError = Color.White,
    errorContainer = Color(0xFFFFE8E8),
    onErrorContainer = Color(0xFF7A1013),
)

private val DarkColors = darkColorScheme(
    primary = BrandBlueLight,
    onPrimary = Color(0xFF07204F),
    primaryContainer = Color(0xFF233A6E),
    onPrimaryContainer = Color(0xFFD9E3FF),
    secondary = BrandCyan,
    onSecondary = Color(0xFF00332C),
    secondaryContainer = Color(0xFF04544A),
    onSecondaryContainer = Color(0xFFC7F5EC),
    tertiary = Color(0xFFB7A6FF),
    background = Color(0xFF080D18),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF111826),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF1A2333),
    onSurfaceVariant = Color(0xFFA3ADBF),
    outline = Color(0xFF3A4559),
    outlineVariant = Color(0xFF232E41),
    error = Color(0xFFFF7A7E),
    onError = Color(0xFF3A0B0C),
    errorContainer = Color(0xFF4B1518),
    onErrorContainer = Color(0xFFFFD9D9),
)

val AppTypography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

object Dimens {
    val screenPadding = 20.dp
    val cardPadding = 18.dp
    val cardRadius = 24.dp
    val chipRadius = 999.dp
    val fieldRadius = 16.dp
    val sectionGap = 20.dp
    val itemGap = 12.dp
}

@Composable
fun MockLocTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (dark) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        val activity = LocalActivity.current
        SideEffect {
            val window = activity?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}

/** 用于 Compose Preview 的简化主题 */
@Composable
fun PreviewTheme(dark: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}

val LocalDimens = androidx.compose.runtime.staticCompositionLocalOf { Dimens }
