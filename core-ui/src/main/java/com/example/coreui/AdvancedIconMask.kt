package com.example.coreui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix as AndroidColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.settingsstore.model.BadgeStyle
import com.example.settingsstore.model.IconMaskConfig
import com.example.settingsstore.model.IconMaskShape
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import com.example.themeengine.parseHexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class PolygonMaskShape(
    private val vertices: List<Offset>,
    private val roundFraction: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = buildRoundedPolygonPath(size, vertices, roundFraction)
        return Outline.Generic(path)
    }
}

private fun buildRoundedPolygonPath(
    size: Size,
    normalizedVertices: List<Offset>,
    roundFraction: Float
): Path {
    val path = Path()
    if (normalizedVertices.size < 3) return path

    val pts = normalizedVertices.map { Offset(it.x * size.width, it.y * size.height) }
    val clampedRound = roundFraction.coerceIn(0f, 0.48f)

    if (clampedRound <= 0.01f) {
        path.moveTo(pts.first().x, pts.first().y)
        for (i in 1 until pts.size) {
            path.lineTo(pts[i].x, pts[i].y)
        }
        path.close()
        return path
    }

    val count = pts.size
    for (i in 0 until count) {
        val prev = pts[(i - 1 + count) % count]
        val curr = pts[i]
        val next = pts[(i + 1) % count]

        val start = Offset(
            curr.x + (prev.x - curr.x) * clampedRound,
            curr.y + (prev.y - curr.y) * clampedRound
        )
        val end = Offset(
            curr.x + (next.x - curr.x) * clampedRound,
            curr.y + (next.y - curr.y) * clampedRound
        )

        if (i == 0) {
            path.moveTo(start.x, start.y)
        } else {
            path.lineTo(start.x, start.y)
        }
        path.quadraticTo(curr.x, curr.y, end.x, end.y)
    }
    path.close()
    return path
}

private fun buildRegularPolygonVertices(sides: Int, radius: Float, rotationDeg: Float): List<Offset> {
    val rotRad = Math.toRadians(rotationDeg.toDouble())
    return (0 until sides).map { i ->
        val angle = (2.0 * PI * i / sides) - (PI / 2.0) + rotRad
        Offset(
            0.5f + radius * cos(angle).toFloat(),
            0.5f + radius * sin(angle).toFloat()
        )
    }
}

private fun buildStarVertices(points: Int, innerRatio: Float, rotationDeg: Float): List<Offset> {
    val rotRad = Math.toRadians(rotationDeg.toDouble())
    val outerR = 0.49f
    val innerR = (outerR * innerRatio.coerceIn(0.22f, 0.85f))
    val total = points * 2
    return (0 until total).map { i ->
        val angle = (PI * i / points) - (PI / 2.0) + rotRad
        val r = if (i % 2 == 0) outerR else innerR
        Offset(
            0.5f + r * cos(angle).toFloat(),
            0.5f + r * sin(angle).toFloat()
        )
    }
}

fun resolveMaskShape(
    shape: IconMaskShape,
    borderRadiusPercent: Float,
    polygonSides: Int = 5,
    starInnerRatio: Float = 0.48f,
    rotationDegrees: Float = 0f
): Shape {
    val radiusFrac = (borderRadiusPercent.coerceIn(0f, 100f) / 100f)
    return when (shape) {
        IconMaskShape.RAW -> RoundedCornerShape(0.dp)
        IconMaskShape.CIRCLE -> CircleShape
        IconMaskShape.SQUIRCLE -> RoundedCornerShape(percent = (14 + radiusFrac * 36).toInt().coerceIn(8, 50))
        IconMaskShape.SHARP_SQUARE -> RoundedCornerShape(percent = (radiusFrac * 18).toInt().coerceIn(0, 18))
        IconMaskShape.TEARDROP -> {
            val r = (20 + radiusFrac * 30).toInt().coerceIn(15, 50)
            RoundedCornerShape(
                topStartPercent = r,
                topEndPercent = r,
                bottomStartPercent = r,
                bottomEndPercent = (radiusFrac * 14).toInt().coerceIn(4, 18)
            )
        }
        IconMaskShape.HEXAGON -> {
            PolygonMaskShape(buildRegularPolygonVertices(6, 0.48f, rotationDegrees), roundFraction = 0f)
        }
        IconMaskShape.ROUNDED_HEXAGON -> {
            PolygonMaskShape(
                buildRegularPolygonVertices(6, 0.49f, rotationDegrees),
                roundFraction = (0.12f + radiusFrac * 0.34f)
            )
        }
        IconMaskShape.DIAMOND -> {
            PolygonMaskShape(
                buildRegularPolygonVertices(4, 0.49f, rotationDegrees),
                roundFraction = radiusFrac * 0.42f
            )
        }
        IconMaskShape.STAR -> {
            val points = polygonSides.coerceIn(4, 10)
            PolygonMaskShape(
                buildStarVertices(points, starInnerRatio, rotationDegrees),
                roundFraction = radiusFrac * 0.32f
            )
        }
        IconMaskShape.CUSTOM_POLYGON -> {
            val sides = polygonSides.coerceIn(3, 12)
            PolygonMaskShape(
                buildRegularPolygonVertices(sides, 0.48f, rotationDegrees),
                roundFraction = radiusFrac * 0.44f
            )
        }
    }
}

@Immutable
private data class BakedIconKey(
    val sourceId: Int,
    val shape: IconMaskShape,
    val borderRadiusBits: Int,
    val polygonSides: Int,
    val starRatioBits: Int,
    val rotationBits: Int,
    val innerPadBits: Int,
    val borderWidthBits: Int,
    val borderColorArgb: Int,
    val bgColorArgb: Int,
    val rimGlow: Boolean,
    val rimGlowArgb: Int,
    val monochrome: Boolean,
    val duotone: Boolean,
    val dotOverlay: Boolean,
    val dimmed: Boolean,
    val accentArgb: Int
)

private object BakedIconMemoryCache {
    val map = ConcurrentHashMap<BakedIconKey, ImageBitmap>(384)
    @Volatile
    var lastStyleSignature: Int = 0

    fun ensureStyleGeneration(styleSig: Int) {
        if (lastStyleSignature != styleSig) {
            lastStyleSignature = styleSig
            if (map.size > 280) {
                map.clear()
            }
        }
    }
}

private val GrayscaleComposeFilter: ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

private val GrayscaleAndroidFilter: ColorMatrixColorFilter =
    ColorMatrixColorFilter(AndroidColorMatrix().apply { setSaturation(0f) })

private fun outlineToAndroidPath(outline: Outline): AndroidPath {
    return when (outline) {
        is Outline.Rectangle -> AndroidPath().apply {
            addRect(
                outline.rect.left,
                outline.rect.top,
                outline.rect.right,
                outline.rect.bottom,
                AndroidPath.Direction.CW
            )
        }
        is Outline.Rounded -> AndroidPath().apply {
            val rr = outline.roundRect
            val radii = floatArrayOf(
                rr.topLeftCornerRadius.x, rr.topLeftCornerRadius.y,
                rr.topRightCornerRadius.x, rr.topRightCornerRadius.y,
                rr.bottomRightCornerRadius.x, rr.bottomRightCornerRadius.y,
                rr.bottomLeftCornerRadius.x, rr.bottomLeftCornerRadius.y
            )
            addRoundRect(
                RectF(rr.left, rr.top, rr.right, rr.bottom),
                radii,
                AndroidPath.Direction.CW
            )
        }
        is Outline.Generic -> {
            AndroidPath(outline.path.asAndroidPath())
        }
    }
}

private fun bakeMaskedIcon(
    src: ImageBitmap,
    maskConfig: IconMaskConfig,
    effectiveShape: IconMaskShape,
    effectiveMonochrome: Boolean,
    dimmedForFocus: Boolean,
    borderColorArgb: Int,
    bgColorArgb: Int,
    rimGlowArgb: Int,
    accentArgb: Int
): ImageBitmap {
    val px = 136
    val pxF = px.toFloat()
    val density = pxF / 48f
    val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)

    val shape = resolveMaskShape(
        shape = effectiveShape,
        borderRadiusPercent = maskConfig.borderRadiusPercent,
        polygonSides = maskConfig.polygonSides,
        starInnerRatio = maskConfig.starInnerRatio,
        rotationDegrees = maskConfig.rotationDegrees
    )
    val borderWidthPx = if (effectiveShape == IconMaskShape.RAW) {
        0f
    } else {
        maskConfig.borderWidthDp.coerceIn(0f, 4f) * density
    }
    val inset = borderWidthPx * 0.5f
    val outline = shape.createOutline(
        size = Size(pxF - inset * 2f, pxF - inset * 2f),
        layoutDirection = LayoutDirection.Ltr,
        density = Density(density)
    )
    val clipPath = outlineToAndroidPath(outline).apply {
        if (inset != 0f) offset(inset, inset)
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    // 1. Background fill
    if (effectiveShape != IconMaskShape.RAW && (bgColorArgb ushr 24) != 0) {
        paint.style = Paint.Style.FILL
        paint.color = bgColorArgb
        canvas.drawPath(clipPath, paint)
    }

    // 2. Clipped icon bitmap + optional dot-matrix overlay
    val innerPadPx = if (effectiveShape == IconMaskShape.RAW) {
        0f
    } else {
        maskConfig.innerPaddingDp.coerceIn(0f, 18f) * density
    }
    canvas.save()
    canvas.clipPath(clipPath)

    val androidBmp = src.asAndroidBitmap()
    val dstRect = RectF(innerPadPx, innerPadPx, pxF - innerPadPx, pxF - innerPadPx)
    paint.color = android.graphics.Color.WHITE
    paint.alpha = if (dimmedForFocus) 110 else 255
    paint.colorFilter = when {
        maskConfig.duotoneFilter && !dimmedForFocus -> PorterDuffColorFilter(accentArgb, PorterDuff.Mode.MULTIPLY)
        effectiveMonochrome -> GrayscaleAndroidFilter
        else -> null
    }
    canvas.drawBitmap(androidBmp, null, dstRect, paint)
    paint.colorFilter = null
    paint.alpha = 255

    if (maskConfig.dotMatrixOverlay && effectiveShape != IconMaskShape.RAW) {
        paint.style = Paint.Style.FILL
        paint.color = 0x28000000
        val step = pxF / 9f
        var y = step * 0.5f
        while (y < pxF) {
            var x = step * 0.5f
            while (x < pxF) {
                canvas.drawCircle(x, y, step * 0.16f, paint)
                x += step
            }
            y += step
        }
    }
    canvas.restore()

    // 3. Border / Rim Glow stroke
    if (effectiveShape != IconMaskShape.RAW && (maskConfig.rimGlowEnabled || borderWidthPx > 0.2f)) {
        paint.style = Paint.Style.STROKE
        if (maskConfig.rimGlowEnabled) {
            paint.strokeWidth = borderWidthPx + 1.2f * density
            paint.shader = SweepGradient(
                pxF * 0.5f,
                pxF * 0.5f,
                intArrayOf(rimGlowArgb, 0x44FFFFFF, rimGlowArgb),
                null
            )
        } else {
            paint.strokeWidth = borderWidthPx
            paint.color = borderColorArgb
        }
        canvas.drawPath(clipPath, paint)
        paint.shader = null
    }

    return out.asImageBitmap()
}

suspend fun prewarmMaskedIcons(
    bitmaps: List<ImageBitmap>,
    maskConfig: IconMaskConfig,
    deepFocusActive: Boolean,
    accentColor: Color
) = withContext(Dispatchers.Default) {
    if (bitmaps.isEmpty()) return@withContext
    BakedIconMemoryCache.ensureStyleGeneration(maskConfig.hashCode())
    val effectiveShape = maskConfig.shape
    val effectiveMonochrome = maskConfig.monochromeFilter || deepFocusActive
    val maskBgColor = if (effectiveShape == IconMaskShape.RAW) {
        Color.Transparent
    } else {
        parseHexColor(maskConfig.maskBackgroundHex, Color(0xFF16161A))
    }
    val borderColor = parseHexColor(maskConfig.borderColorHex, Color(0x33FFFFFF))
    val rimGlowColor = parseHexColor(maskConfig.rimGlowHex, Color(0xFFD71921))
    val borderArgb = borderColor.toArgb()
    val bgArgb = maskBgColor.toArgb()
    val rimArgb = rimGlowColor.toArgb()
    val accentArgb = accentColor.toArgb()

    for (bmp in bitmaps) {
        val key = BakedIconKey(
            sourceId = System.identityHashCode(bmp),
            shape = effectiveShape,
            borderRadiusBits = maskConfig.borderRadiusPercent.toRawBits(),
            polygonSides = maskConfig.polygonSides,
            starRatioBits = maskConfig.starInnerRatio.toRawBits(),
            rotationBits = maskConfig.rotationDegrees.toRawBits(),
            innerPadBits = maskConfig.innerPaddingDp.toRawBits(),
            borderWidthBits = maskConfig.borderWidthDp.toRawBits(),
            borderColorArgb = borderArgb,
            bgColorArgb = bgArgb,
            rimGlow = maskConfig.rimGlowEnabled,
            rimGlowArgb = rimArgb,
            monochrome = effectiveMonochrome,
            duotone = maskConfig.duotoneFilter,
            dotOverlay = maskConfig.dotMatrixOverlay,
            dimmed = deepFocusActive,
            accentArgb = accentArgb
        )
        if (!BakedIconMemoryCache.map.containsKey(key)) {
            BakedIconMemoryCache.map[key] = bakeMaskedIcon(
                src = bmp,
                maskConfig = maskConfig,
                effectiveShape = effectiveShape,
                effectiveMonochrome = effectiveMonochrome,
                dimmedForFocus = deepFocusActive,
                borderColorArgb = borderArgb,
                bgColorArgb = bgArgb,
                rimGlowArgb = rimArgb,
                accentArgb = accentArgb
            )
        }
    }
}

@Composable
fun MaskedAppIcon(
    iconBitmap: ImageBitmap?,
    maskConfig: IconMaskConfig,
    modifier: Modifier = Modifier,
    baseSize: Dp = 52.dp,
    shapeOverride: IconMaskShape? = null,
    customImageUri: String? = null,
    customGlyphVector: ImageVector? = null,
    monochromeOverride: Boolean? = null,
    scaleOverride: Float? = null,
    badgeCount: Int = 0,
    badgeStyle: BadgeStyle = BadgeStyle.NONE,
    dimmedForFocus: Boolean = false,
    contentDescription: String? = null
) {
    val palette = LocalNothingPalette.current
    val effectiveShape = shapeOverride ?: maskConfig.shape
    val effectiveScale = (scaleOverride ?: maskConfig.iconScale).coerceIn(0.5f, 1.5f)
    val effectiveMonochrome = monochromeOverride ?: (maskConfig.monochromeFilter || dimmedForFocus)
    val scaledSize = baseSize * effectiveScale
    val showOverlayBadge = badgeCount > 0 && badgeStyle != BadgeStyle.NONE && badgeStyle != BadgeStyle.GLYPH_PULSE

    // ULTRA-FAST PATH (99%+ of icons): Pre-baked single flat texture quad — ZERO GPU clips, ZERO nested Boxes!
    if (customImageUri.isNullOrBlank() && customGlyphVector == null && iconBitmap != null) {
        val maskBgColor = if (effectiveShape == IconMaskShape.RAW) {
            Color.Transparent
        } else {
            parseHexColor(maskConfig.maskBackgroundHex, Color(0xFF16161A))
        }
        val borderColor = if (badgeStyle == BadgeStyle.GLYPH_PULSE && badgeCount > 0) {
            palette.accent
        } else {
            parseHexColor(maskConfig.borderColorHex, Color(0x33FFFFFF))
        }
        val rimGlowColor = parseHexColor(maskConfig.rimGlowHex, Color(0xFFD71921))

        val key = BakedIconKey(
            sourceId = System.identityHashCode(iconBitmap),
            shape = effectiveShape,
            borderRadiusBits = maskConfig.borderRadiusPercent.toRawBits(),
            polygonSides = maskConfig.polygonSides,
            starRatioBits = maskConfig.starInnerRatio.toRawBits(),
            rotationBits = maskConfig.rotationDegrees.toRawBits(),
            innerPadBits = maskConfig.innerPaddingDp.toRawBits(),
            borderWidthBits = maskConfig.borderWidthDp.toRawBits(),
            borderColorArgb = borderColor.toArgb(),
            bgColorArgb = maskBgColor.toArgb(),
            rimGlow = maskConfig.rimGlowEnabled,
            rimGlowArgb = rimGlowColor.toArgb(),
            monochrome = effectiveMonochrome,
            duotone = maskConfig.duotoneFilter,
            dotOverlay = maskConfig.dotMatrixOverlay,
            dimmed = dimmedForFocus,
            accentArgb = palette.accent.toArgb()
        )
        BakedIconMemoryCache.ensureStyleGeneration(maskConfig.hashCode())
        val cached = BakedIconMemoryCache.map[key]
        var bakedBitmap by remember(key) { mutableStateOf(cached) }
        if (bakedBitmap == null && cached != null) {
            bakedBitmap = cached
        }
        if (bakedBitmap == null) {
            LaunchedEffect(key) {
                val created = withContext(Dispatchers.Default) {
                    BakedIconMemoryCache.map[key] ?: bakeMaskedIcon(
                        src = iconBitmap,
                        maskConfig = maskConfig,
                        effectiveShape = effectiveShape,
                        effectiveMonochrome = effectiveMonochrome,
                        dimmedForFocus = dimmedForFocus,
                        borderColorArgb = borderColor.toArgb(),
                        bgColorArgb = maskBgColor.toArgb(),
                        rimGlowArgb = rimGlowColor.toArgb(),
                        accentArgb = palette.accent.toArgb()
                    ).also { BakedIconMemoryCache.map[key] = it }
                }
                bakedBitmap = created
            }
        }

        val displayBitmap = bakedBitmap ?: iconBitmap
        if (!showOverlayBadge) {
            Image(
                bitmap = displayBitmap,
                contentDescription = contentDescription,
                modifier = modifier.size(scaledSize),
                contentScale = ContentScale.Fit
            )
            return
        }

        Box(
            modifier = modifier.size(scaledSize),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = displayBitmap,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            IconBadgeOverlay(
                badgeCount = badgeCount,
                badgeStyle = badgeStyle,
                accent = palette.accent,
                background = palette.background,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
        return
    }

    // Fallback path for custom gallery image URIs, custom glyph vectors, or loading placeholder
    val clipShape = remember(
        effectiveShape,
        maskConfig.borderRadiusPercent,
        maskConfig.polygonSides,
        maskConfig.starInnerRatio,
        maskConfig.rotationDegrees
    ) {
        resolveMaskShape(
            shape = effectiveShape,
            borderRadiusPercent = maskConfig.borderRadiusPercent,
            polygonSides = maskConfig.polygonSides,
            starInnerRatio = maskConfig.starInnerRatio,
            rotationDegrees = maskConfig.rotationDegrees
        )
    }
    val maskBgColor = remember(maskConfig.maskBackgroundHex, effectiveShape) {
        if (effectiveShape == IconMaskShape.RAW) Color.Transparent
        else parseHexColor(maskConfig.maskBackgroundHex, Color(0xFF16161A))
    }
    val borderColor = remember(maskConfig.borderColorHex, badgeStyle, badgeCount, palette.accent) {
        if (badgeStyle == BadgeStyle.GLYPH_PULSE && badgeCount > 0) palette.accent
        else parseHexColor(maskConfig.borderColorHex, Color(0x33FFFFFF))
    }
    val activeColorFilter = remember(effectiveMonochrome, maskConfig.duotoneFilter, dimmedForFocus, palette.accent) {
        when {
            maskConfig.duotoneFilter && !dimmedForFocus -> ColorFilter.tint(palette.accent.copy(alpha = 0.88f), BlendMode.Color)
            effectiveMonochrome -> GrayscaleComposeFilter
            else -> null
        }
    }
    val innerPad = if (effectiveShape == IconMaskShape.RAW) 0.dp else maskConfig.innerPaddingDp.coerceIn(0f, 18f).dp
    val borderWidth = if (effectiveShape == IconMaskShape.RAW) 0.dp else maskConfig.borderWidthDp.coerceIn(0f, 4f).dp

    Box(
        modifier = modifier
            .size(scaledSize)
            .then(
                if (dimmedForFocus) Modifier.graphicsLayer { alpha = 0.42f } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(clipShape)
                .background(maskBgColor)
                .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, clipShape) else Modifier)
                .padding(innerPad),
            contentAlignment = Alignment.Center
        ) {
            when {
                !customImageUri.isNullOrBlank() -> {
                    AsyncImage(
                        model = customImageUri,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        colorFilter = activeColorFilter
                    )
                }
                customGlyphVector != null -> {
                    Icon(
                        imageVector = customGlyphVector,
                        contentDescription = contentDescription,
                        tint = if (maskConfig.duotoneFilter) palette.accent else palette.textPrimary,
                        modifier = Modifier.fillMaxSize(0.68f)
                    )
                }
                else -> {
                    Canvas(modifier = Modifier.fillMaxSize(0.6f)) {
                        drawCircle(color = palette.textSecondary, style = Stroke(width = 3f))
                        drawCircle(color = palette.accent, radius = size.minDimension * 0.14f)
                    }
                }
            }
        }

        if (showOverlayBadge) {
            IconBadgeOverlay(
                badgeCount = badgeCount,
                badgeStyle = badgeStyle,
                accent = palette.accent,
                background = palette.background,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
private fun IconBadgeOverlay(
    badgeCount: Int,
    badgeStyle: BadgeStyle,
    accent: Color,
    background: Color,
    modifier: Modifier = Modifier
) {
    when (badgeStyle) {
        BadgeStyle.MINIMAL_DOT -> {
            Box(
                modifier = modifier
                    .offset(x = 2.dp, y = (-2).dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .border(1.dp, background, CircleShape)
            )
        }
        BadgeStyle.DOT_MATRIX_COUNT -> {
            Box(
                modifier = modifier
                    .offset(x = 4.dp, y = (-4).dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(accent)
                    .border(1.dp, background, RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        BadgeStyle.NONE, BadgeStyle.GLYPH_PULSE -> Unit
    }
}
