package com.silentlink.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.silentlink.app.model.AppTheme

// 공통 색상
val AccentBlue = Color(0xFF5B8DEE)
val DangerRed = Color(0xFFF87171)
val SuccessGreen = Color(0xFF4ADE80)

// 다크 테마 (기본)
object DarkColors {
    val Background = Color(0xFF0D0F14)
    val Surface = Color(0xFF161922)
    val Card = Color(0xFF1E2230)
    val OnBackground = Color(0xFFEEF0F6)
    val OnSurface = Color(0xFFCDD0DA)
    val Outline = Color(0xFF2E3347)
}

// 라이트 테마
object LightColors {
    val Background = Color(0xFFF4F6FA)
    val Surface = Color(0xFFFFFFFF)
    val Card = Color(0xFFEDF0F7)
    val OnBackground = Color(0xFF1A1D26)
    val OnSurface = Color(0xFF3A3E4E)
    val Outline = Color(0xFFD0D4E0)
}

// 퍼플 테마
object PurpleColors {
    val Background = Color(0xFF0F0D1A)
    val Surface = Color(0xFF1A1628)
    val Card = Color(0xFF241E38)
    val OnBackground = Color(0xFFEDE8FF)
    val OnSurface = Color(0xFFCEC7F0)
    val Outline = Color(0xFF3A2E5C)
}

// 포레스트 테마
object ForestColors {
    val Background = Color(0xFF0D140F)
    val Surface = Color(0xFF141F16)
    val Card = Color(0xFF1C2B1E)
    val OnBackground = Color(0xFFE8F4EA)
    val OnSurface = Color(0xFFC6DEC9)
    val Outline = Color(0xFF2C4430)
}

// 선셋 테마
object SunsetColors {
    val Background = Color(0xFF140D0A)
    val Surface = Color(0xFF201410)
    val Card = Color(0xFF2E1C17)
    val OnBackground = Color(0xFFF4EDE8)
    val OnSurface = Color(0xFFDEC8C0)
    val Outline = Color(0xFF4A2E26)
}

// 핑크 테마
object PinkColors {
    val Background = Color(0xFF14090F)
    val Surface = Color(0xFF200F18)
    val Card = Color(0xFF2E1622)
    val OnBackground = Color(0xFFF4E8EF)
    val OnSurface = Color(0xFFDEC0CC)
    val Outline = Color(0xFF4A2636)
}

data class SilentLinkColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val onBackground: Color,
    val onSurface: Color,
    val outline: Color,
    val accent: Color = AccentBlue,
    val danger: Color = DangerRed,
    val success: Color = SuccessGreen
)

fun AppTheme.toSilentLinkColors(): SilentLinkColors = when (this) {
    AppTheme.DARK -> SilentLinkColors(
        DarkColors.Background, DarkColors.Surface, DarkColors.Card,
        DarkColors.OnBackground, DarkColors.OnSurface, DarkColors.Outline
    )
    AppTheme.LIGHT -> SilentLinkColors(
        LightColors.Background, LightColors.Surface, LightColors.Card,
        LightColors.OnBackground, LightColors.OnSurface, LightColors.Outline
    )
    AppTheme.PURPLE -> SilentLinkColors(
        PurpleColors.Background, PurpleColors.Surface, PurpleColors.Card,
        PurpleColors.OnBackground, PurpleColors.OnSurface, PurpleColors.Outline,
        accent = Color(0xFF9B8DEE)
    )
    AppTheme.FOREST -> SilentLinkColors(
        ForestColors.Background, ForestColors.Surface, ForestColors.Card,
        ForestColors.OnBackground, ForestColors.OnSurface, ForestColors.Outline,
        accent = Color(0xFF4ADE80)
    )
    AppTheme.SUNSET -> SilentLinkColors(
        SunsetColors.Background, SunsetColors.Surface, SunsetColors.Card,
        SunsetColors.OnBackground, SunsetColors.OnSurface, SunsetColors.Outline,
        accent = Color(0xFFFB8C6A)
    )
    AppTheme.PINK -> SilentLinkColors(
        PinkColors.Background, PinkColors.Surface, PinkColors.Card,
        PinkColors.OnBackground, PinkColors.OnSurface, PinkColors.Outline,
        accent = Color(0xFFF06BB0)
    )
}

fun SilentLinkColors.toMaterialColorScheme(): ColorScheme = darkColorScheme(
    background = background,
    surface = surface,
    surfaceVariant = card,
    onBackground = onBackground,
    onSurface = onSurface,
    primary = accent,
    error = danger,
    outline = outline
)

@Composable
fun SilentLinkTheme(
    appTheme: AppTheme = AppTheme.DARK,
    content: @Composable () -> Unit
) {
    val colors = appTheme.toSilentLinkColors()
    MaterialTheme(
        colorScheme = colors.toMaterialColorScheme(),
        content = content
    )
}
