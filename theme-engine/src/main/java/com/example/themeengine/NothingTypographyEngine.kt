package com.example.themeengine

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.settingsstore.model.FontFamilyChoice
import com.example.settingsstore.model.TextCaseMode
import com.example.settingsstore.model.TypographyElementConfig
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object NothingTypographyEngine {
    val ndot55Family: FontFamily = FontFamily(Font(R.font.dotgothic16, FontWeight.Normal))
    val ndot77Family: FontFamily = FontFamily(Font(R.font.orbitron, FontWeight.Bold))
    val ntype82Family: FontFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Medium))
    val cleanSansFamily: FontFamily = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))
    val bodyInterFamily: FontFamily = FontFamily(Font(R.font.inter, FontWeight.Normal))

    private val customFontCache = ConcurrentHashMap<String, FontFamily>()

    fun resolveFontFamily(choice: FontFamilyChoice, customPath: String?): FontFamily {
        if (choice == FontFamilyChoice.CUSTOM_FILE && !customPath.isNullOrBlank()) {
            customFontCache[customPath]?.let { return it }
            val file = File(customPath)
            if (file.exists() && file.canRead()) {
                val loaded = runCatching {
                    val tf = Typeface.createFromFile(file)
                    FontFamily(tf)
                }.getOrNull()
                if (loaded != null) {
                    customFontCache[customPath] = loaded
                    return loaded
                }
            }
        }
        return when (choice) {
            FontFamilyChoice.NDOT_55 -> ndot55Family
            FontFamilyChoice.NDOT_77 -> ndot77Family
            FontFamilyChoice.NTYPE_82 -> ntype82Family
            FontFamilyChoice.SANS_CLEAN -> cleanSansFamily
            FontFamilyChoice.CUSTOM_FILE -> cleanSansFamily
        }
    }

    fun resolveTextStyle(config: TypographyElementConfig): TextStyle {
        val weight = when {
            config.fontWeight <= 350 -> FontWeight.Light
            config.fontWeight <= 450 -> FontWeight.Normal
            config.fontWeight <= 550 -> FontWeight.Medium
            config.fontWeight <= 650 -> FontWeight.SemiBold
            else -> FontWeight.Bold
        }
        return TextStyle(
            fontFamily = resolveFontFamily(config.fontChoice, config.customFontPath),
            fontWeight = weight,
            fontSize = config.sizeSp.coerceIn(8f, 48f).sp,
            letterSpacing = config.letterSpacingSp.coerceIn(-2f, 8f).sp,
            lineHeight = config.lineHeightSp.coerceAtLeast(config.sizeSp + 2f).sp
        )
    }

    fun formatText(raw: String, mode: TextCaseMode): String = when (mode) {
        TextCaseMode.ORIGINAL -> raw
        TextCaseMode.UPPERCASE -> raw.uppercase(Locale.getDefault())
        TextCaseMode.LOWERCASE -> raw.lowercase(Locale.getDefault())
        TextCaseMode.TITLE_CASE -> raw.split(" ").joinToString(" ") { word ->
            word.lowercase(Locale.getDefault()).replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
        }
    }

    fun importCustomFont(context: Context, uri: Uri, displayName: String = "custom_font.ttf"): String? {
        return runCatching {
            val dir = File(context.filesDir, "custom_fonts").apply { mkdirs() }
            val ext = if (displayName.endsWith(".otf", ignoreCase = true)) ".otf" else ".ttf"
            val dest = File(dir, "font_${System.currentTimeMillis()}$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            // Verify Typeface parses properly
            Typeface.createFromFile(dest)
            customFontCache.remove(dest.absolutePath)
            dest.absolutePath
        }.getOrNull()
    }
}
