package com.example.themeengine

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.SurfaceThemeConfig

@Immutable
data class NothingPalette(
    val background: Color,
    val surface: Color,
    val accent: Color,
    val glyphHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val drawerBg: Color,
    val widgetBg: Color,
    val folderBg: Color,
    val borderSubtle: Color,
    val isLightSurface: Boolean
)

val LocalLauncherSettings = staticCompositionLocalOf { LauncherSettings() }
val LocalNothingPalette = staticCompositionLocalOf {
    NothingPalette(
        background = Color(0xFF000000),
        surface = Color(0xFF111114),
        accent = Color(0xFFD71921),
        glyphHighlight = Color(0xFFD71921),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFF9A9AA2),
        drawerBg = Color(0xFF0A0A0C),
        widgetBg = Color(0xFF121215),
        folderBg = Color(0xFF151519),
        borderSubtle = Color(0x28FFFFFF),
        isLightSurface = false
    )
}

fun parseHexColor(hex: String, fallback: Color): Color {
    return runCatching {
        val clean = hex.trim().removePrefix("#")
        val parsed = when (clean.length) {
            6 -> (0xFF000000L or clean.toLong(16)).toInt()
            8 -> clean.toLong(16).toInt()
            else -> return fallback
        }
        Color(parsed)
    }.getOrElse { fallback }
}

fun resolveNothingPalette(theme: SurfaceThemeConfig, deepFocusActive: Boolean): NothingPalette {
    val bg = if (theme.oledPureBlack) Color(0xFF000000) else parseHexColor(theme.backgroundHex, Color(0xFF000000))
    val surface = parseHexColor(theme.surfaceHex, Color(0xFF111114))
    val rawAccent = parseHexColor(theme.accentHex, Color(0xFFD71921))
    val rawGlyph = parseHexColor(theme.glyphHighlightHex, rawAccent)
    val accent = if (deepFocusActive) Color(0xFF8A8A92) else rawAccent
    val glyph = if (deepFocusActive) Color(0xFF707078) else rawGlyph
    val textPrimary = parseHexColor(theme.textPrimaryHex, Color(0xFFFFFFFF))
    val textSecondary = parseHexColor(theme.textSecondaryHex, Color(0xFF9A9AA2))
    val isLight = bg.luminance() > 0.5f
    val border = if (isLight) Color(0x26000000) else Color(0x2EFFFFFF)

    return NothingPalette(
        background = bg,
        surface = surface,
        accent = accent,
        glyphHighlight = glyph,
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        drawerBg = parseHexColor(theme.drawerBgHex, surface).copy(alpha = theme.drawerOpacity.coerceIn(0.05f, 1f)),
        widgetBg = parseHexColor(theme.widgetBgHex, surface).copy(alpha = theme.widgetOpacity.coerceIn(0.05f, 1f)),
        folderBg = parseHexColor(theme.folderBgHex, surface).copy(alpha = theme.folderOpacity.coerceIn(0.05f, 1f)),
        borderSubtle = border,
        isLightSurface = isLight
    )
}

@Composable
fun DotLauncherTheme(
    settings: LauncherSettings,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val palette = remember(settings.theme, settings.deepFocusActive) {
        resolveNothingPalette(settings.theme, settings.deepFocusActive)
    }

    val colorScheme: ColorScheme = remember(palette, settings.theme.useMaterialYou) {
        if (settings.theme.useMaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = if (palette.isLightSurface) dynamicLightColorScheme(context) else dynamicDarkColorScheme(context)
            base.copy(
                background = palette.background,
                surface = palette.surface
            )
        } else if (palette.isLightSurface) {
            lightColorScheme(
                primary = palette.accent,
                onPrimary = Color.White,
                secondary = palette.textSecondary,
                background = palette.background,
                onBackground = palette.textPrimary,
                surface = palette.surface,
                onSurface = palette.textPrimary,
                surfaceVariant = palette.widgetBg,
                onSurfaceVariant = palette.textSecondary,
                outline = palette.borderSubtle
            )
        } else {
            darkColorScheme(
                primary = palette.accent,
                onPrimary = Color.White,
                secondary = palette.textSecondary,
                background = palette.background,
                onBackground = palette.textPrimary,
                surface = palette.surface,
                onSurface = palette.textPrimary,
                surfaceVariant = palette.widgetBg,
                onSurfaceVariant = palette.textSecondary,
                outline = palette.borderSubtle
            )
        }
    }

    val m3Typography = remember(settings.typographyWidgetTitle, settings.typographyDrawer) {
        Typography(
            displaySmall = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetData),
            titleLarge = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle).copy(fontSize = 18.sp),
            titleMedium = NothingTypographyEngine.resolveTextStyle(settings.typographyWidgetTitle).copy(fontSize = 14.sp),
            bodyLarge = TextStyle(
                fontFamily = NothingTypographyEngine.cleanSansFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 15.sp,
                lineHeight = 22.sp
            ),
            bodyMedium = NothingTypographyEngine.resolveTextStyle(settings.typographyDrawer),
            labelMedium = NothingTypographyEngine.resolveTextStyle(settings.typographyHome)
        )
    }

    CompositionLocalProvider(
        LocalLauncherSettings provides settings,
        LocalNothingPalette provides palette
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = m3Typography,
            content = content
        )
    }
}
