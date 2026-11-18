package com.example.universalsearch

import androidx.compose.runtime.Immutable
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.pow

@Immutable
sealed class UtilityResult {
    data class MathEvaluation(val expression: String, val formattedResult: String) : UtilityResult()
    data class Conversion(val sourceText: String, val targetText: String, val note: String) : UtilityResult()
    data class HexColorPreview(val hex: String, val argbInt: Int, val rgbLabel: String) : UtilityResult()
    data class QuickTimer(val label: String, val durationSeconds: Int) : UtilityResult()
    data class QuickAlarm(val hour: Int, val minute: Int, val formattedTime: String) : UtilityResult()
}

object UtilityParsers {
    private val decimalFormat = DecimalFormat("#,##0.####")

    @Volatile
    private var liveFxActive: Boolean = false

    @Volatile
    private var currencyRatesToUsd = mapOf(
        "USD" to 1.0,
        "EUR" to 1.085,
        "GBP" to 1.27,
        "DZD" to 0.00746,
        "JPY" to 0.0065,
        "CAD" to 0.72,
        "AUD" to 0.65,
        "CHF" to 1.13,
        "MAD" to 0.10,
        "SAR" to 0.2666,
        "AED" to 0.2722,
        "INR" to 0.0119
    )

    fun updateLiveFxRates(ratesPerUsd: Map<String, Double>) {
        if (ratesPerUsd.isEmpty()) return
        val converted = HashMap<String, Double>()
        ratesPerUsd.forEach { (code, perUsd) ->
            if (perUsd > 0.0) {
                converted[code.uppercase(Locale.US)] = 1.0 / perUsd
            }
        }
        if (converted.isNotEmpty()) {
            converted["USD"] = 1.0
            currencyRatesToUsd = converted
            liveFxActive = true
        }
    }

    fun parse(query: String): List<UtilityResult> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val results = mutableListOf<UtilityResult>()

        parseHexColor(trimmed)?.let { results.add(it) }
        parseTimerOrAlarm(trimmed)?.let { results.add(it) }
        parseConversion(trimmed)?.let { results.add(it) }
        parseMath(trimmed)?.let { results.add(it) }

        return results
    }

    private fun parseHexColor(input: String): UtilityResult.HexColorPreview? {
        val match = Regex("^#?([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$").matchEntire(input) ?: return null
        if (!input.startsWith("#") && input.all { it.isDigit() }) return null
        val clean = match.groupValues[1].uppercase(Locale.US)
        val argb = if (clean.length == 6) {
            (0xFF000000L or clean.toLong(16)).toInt()
        } else {
            clean.toLong(16).toInt()
        }
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return UtilityResult.HexColorPreview(
            hex = "#$clean",
            argbInt = argb,
            rgbLabel = "RGB($r, $g, $b)"
        )
    }

    private fun parseTimerOrAlarm(input: String): UtilityResult? {
        val timerMatch = Regex("^timer\\s+(\\d+)\\s*(s|sec|secs|m|min|mins|h|hr|hrs)?$", RegexOption.IGNORE_CASE)
            .matchEntire(input)
        if (timerMatch != null) {
            val amount = timerMatch.groupValues[1].toIntOrNull() ?: return null
            val unit = timerMatch.groupValues[2].lowercase(Locale.US)
            val seconds = when {
                unit.startsWith("h") -> amount * 3600
                unit.startsWith("s") -> amount
                else -> amount * 60
            }.coerceAtLeast(1)
            val label = when {
                seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m Timer"
                seconds >= 60 -> "${seconds / 60}m Timer"
                else -> "${seconds}s Timer"
            }
            return UtilityResult.QuickTimer(label = label, durationSeconds = seconds)
        }

        val alarmMatch = Regex("^alarm\\s+(\\d{1,2}):(\\d{2})$", RegexOption.IGNORE_CASE)
            .matchEntire(input)
        if (alarmMatch != null) {
            val h = alarmMatch.groupValues[1].toIntOrNull()?.takeIf { it in 0..23 } ?: return null
            val m = alarmMatch.groupValues[2].toIntOrNull()?.takeIf { it in 0..59 } ?: return null
            return UtilityResult.QuickAlarm(
                hour = h,
                minute = m,
                formattedTime = String.format(Locale.US, "%02d:%02d", h, m)
            )
        }
        return null
    }

    private fun parseConversion(input: String): UtilityResult.Conversion? {
        val regex = Regex("^([+-]?\\d+(?:\\.\\d+)?)\\s*([a-zA-Z°]+)\\s+(?:to|in)\\s+([a-zA-Z°]+)$", RegexOption.IGNORE_CASE)
        val match = regex.matchEntire(input) ?: return null
        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val fromRaw = match.groupValues[2].uppercase(Locale.US)
        val toRaw = match.groupValues[3].uppercase(Locale.US)

        // Currency
        val fromUsd = currencyRatesToUsd[fromRaw]
        val toUsd = currencyRatesToUsd[toRaw]
        if (fromUsd != null && toUsd != null) {
            val converted = (value * fromUsd) / toUsd
            return UtilityResult.Conversion(
                sourceText = "${decimalFormat.format(value)} $fromRaw",
                targetText = "${decimalFormat.format(converted)} $toRaw",
                note = if (liveFxActive) "Live FX rate" else "Offline FX reference rate"
            )
        }

        // Temperature
        if (fromRaw in setOf("C", "F", "K") && toRaw in setOf("C", "F", "K")) {
            val celsius = when (fromRaw) {
                "C" -> value
                "F" -> (value - 32.0) * 5.0 / 9.0
                else -> value - 273.15
            }
            val out = when (toRaw) {
                "C" -> celsius
                "F" -> (celsius * 9.0 / 5.0) + 32.0
                else -> celsius + 273.15
            }
            return UtilityResult.Conversion(
                sourceText = "${decimalFormat.format(value)}°$fromRaw",
                targetText = "${decimalFormat.format(out)}°$toRaw",
                note = "Temperature conversion"
            )
        }

        // Length -> meters
        val lengthToMeters = mapOf(
            "M" to 1.0, "METER" to 1.0, "METERS" to 1.0,
            "KM" to 1000.0, "CM" to 0.01, "MM" to 0.001,
            "MI" to 1609.344, "MILE" to 1609.344, "MILES" to 1609.344,
            "FT" to 0.3048, "FEET" to 0.3048, "IN" to 0.0254, "INCH" to 0.0254,
            "YD" to 0.9144
        )
        val lFrom = lengthToMeters[fromRaw]
        val lTo = lengthToMeters[toRaw]
        if (lFrom != null && lTo != null) {
            val out = (value * lFrom) / lTo
            return UtilityResult.Conversion(
                sourceText = "${decimalFormat.format(value)} $fromRaw",
                targetText = "${decimalFormat.format(out)} $toRaw",
                note = "Length conversion"
            )
        }

        // Mass -> grams
        val massToGrams = mapOf(
            "G" to 1.0, "KG" to 1000.0, "MG" to 0.001,
            "LB" to 453.59237, "LBS" to 453.59237, "OZ" to 28.34952
        )
        val mFrom = massToGrams[fromRaw]
        val mTo = massToGrams[toRaw]
        if (mFrom != null && mTo != null) {
            val out = (value * mFrom) / mTo
            return UtilityResult.Conversion(
                sourceText = "${decimalFormat.format(value)} $fromRaw",
                targetText = "${decimalFormat.format(out)} $toRaw",
                note = "Mass conversion"
            )
        }

        // Data -> bytes
        val dataToBytes = mapOf(
            "B" to 1.0, "KB" to 1024.0, "MB" to 1024.0.pow(2),
            "GB" to 1024.0.pow(3), "TB" to 1024.0.pow(4)
        )
        val dFrom = dataToBytes[fromRaw]
        val dTo = dataToBytes[toRaw]
        if (dFrom != null && dTo != null) {
            val out = (value * dFrom) / dTo
            return UtilityResult.Conversion(
                sourceText = "${decimalFormat.format(value)} $fromRaw",
                targetText = "${decimalFormat.format(out)} $toRaw",
                note = "Binary data conversion"
            )
        }
        return null
    }

    private fun parseMath(input: String): UtilityResult.MathEvaluation? {
        val clean = input.replace(" ", "").replace("×", "*").replace("÷", "/")
        if (!clean.any { it in "+-*/^%" }) return null
        if (!clean.all { it.isDigit() || it in "+-*/^%.()" }) return null
        val value = runCatching { ExpressionParser(clean).parse() }.getOrNull() ?: return null
        if (value.isNaN() || value.isInfinite()) return null
        return UtilityResult.MathEvaluation(
            expression = input,
            formattedResult = decimalFormat.format(value)
        )
    }

    private class ExpressionParser(private val s: String) {
        private var pos = -1
        private var ch = 0

        private fun nextChar() {
            ch = if (++pos < s.length) s[pos].code else -1
        }

        private fun eat(charToEat: Int): Boolean {
            if (ch == charToEat) {
                nextChar()
                return true
            }
            return false
        }

        fun parse(): Double {
            nextChar()
            val x = parseExpression()
            if (pos < s.length) throw IllegalArgumentException("Unexpected char")
            return x
        }

        private fun parseExpression(): Double {
            var x = parseTerm()
            while (true) {
                x = when {
                    eat('+'.code) -> x + parseTerm()
                    eat('-'.code) -> x - parseTerm()
                    else -> return x
                }
            }
        }

        private fun parseTerm(): Double {
            var x = parseFactor()
            while (true) {
                x = when {
                    eat('*'.code) -> x * parseFactor()
                    eat('/'.code) -> x / parseFactor()
                    eat('%'.code) -> x % parseFactor()
                    else -> return x
                }
            }
        }

        private fun parseFactor(): Double {
            if (eat('+'.code)) return parseFactor()
            if (eat('-'.code)) return -parseFactor()
            var x: Double
            val startPos = pos
            if (eat('('.code)) {
                x = parseExpression()
                if (!eat(')'.code)) throw IllegalArgumentException("Missing )")
            } else if (ch in '0'.code..'9'.code || ch == '.'.code) {
                while (ch in '0'.code..'9'.code || ch == '.'.code) nextChar()
                x = s.substring(startPos, pos).toDouble()
            } else {
                throw IllegalArgumentException("Unexpected")
            }
            if (eat('^'.code)) x = x.pow(parseFactor())
            return x
        }
    }
}
