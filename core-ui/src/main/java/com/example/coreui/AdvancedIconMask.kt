package com.example.coreui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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

private val GrayscaleColorFilter: ColorFilter =
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

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
        if (badgeStyle == BadgeStyle.GLYPH_PULSE && badgeCount > 0) {
            palette.accent
        } else {
            parseHexColor(maskConfig.borderColorHex, Color(0x33FFFFFF))
        }
    }
    val rimGlowColor = remember(maskConfig.rimGlowHex) {
        parseHexColor(maskConfig.rimGlowHex, Color(0xFFD71921))
    }

    val activeColorFilter = remember(effectiveMonochrome, maskConfig.duotoneFilter, dimmedForFocus, palette.accent) {
        when {
            maskConfig.duotoneFilter && !dimmedForFocus -> {
                ColorFilter.tint(palette.accent.copy(alpha = 0.88f), BlendMode.Color)
            }
            effectiveMonochrome -> GrayscaleColorFilter
            else -> null
        }
    }

    val innerPad = if (effectiveShape == IconMaskShape.RAW) 0.dp else maskConfig.innerPaddingDp.coerceIn(0f, 18f).dp
    val borderWidth = if (effectiveShape == IconMaskShape.RAW) 0.dp else maskConfig.borderWidthDp.coerceIn(0f, 4f).dp
    val needsLayer = effectiveScale != 1f || dimmedForFocus
    val needsDotOverlay = maskConfig.dotMatrixOverlay && effectiveShape != IconMaskShape.RAW

    Box(
        modifier = modifier
            .size(baseSize)
            .then(
                if (needsLayer) {
                    Modifier.graphicsLayer {
                        scaleX = effectiveScale
                        scaleY = effectiveScale
                        alpha = if (dimmedForFocus) 0.42f else 1f
                    }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(clipShape)
                .background(maskBgColor)
                .then(
                    if (maskConfig.rimGlowEnabled && effectiveShape != IconMaskShape.RAW) {
                        Modifier.border(
                            width = (borderWidth.value + 1.2f).dp,
                            brush = Brush.sweepGradient(
                                listOf(
                                    rimGlowColor.copy(alpha = 0.9f),
                                    Color.White.copy(alpha = 0.25f),
                                    rimGlowColor.copy(alpha = 0.9f)
                                )
                            ),
                            shape = clipShape
                        )
                    } else if (borderWidth > 0.dp) {
                        Modifier.border(borderWidth, borderColor, clipShape)
                    } else Modifier
                )
                .padding(innerPad)
                .then(
                    if (needsDotOverlay) {
                        Modifier.drawWithContent {
                            drawContent()
                            val step = min(size.width, size.height) / 9f
                            var y = step * 0.5f
                            while (y < size.height) {
                                var x = step * 0.5f
                                while (x < size.width) {
                                    drawCircle(
                                        color = Color(0x28000000),
                                        radius = step * 0.16f,
                                        center = Offset(x, y)
                                    )
                                    x += step
                                }
                                y += step
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
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
                iconBitmap != null -> {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = contentDescription,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        colorFilter = activeColorFilter
                    )
                }
                else -> {
                    Canvas(modifier = Modifier.fillMaxSize(0.6f)) {
                        drawCircle(
                            color = palette.textSecondary,
                            style = Stroke(width = 3f)
                        )
                        drawCircle(
                            color = palette.accent,
                            radius = size.minDimension * 0.14f
                        )
                    }
                }
            }
        }

        if (badgeCount > 0 && badgeStyle != BadgeStyle.NONE && badgeStyle != BadgeStyle.GLYPH_PULSE) {
            when (badgeStyle) {
                BadgeStyle.MINIMAL_DOT -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 2.dp, y = (-2).dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(palette.accent)
                            .border(1.dp, palette.background, CircleShape)
                    )
                }
                BadgeStyle.DOT_MATRIX_COUNT -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.accent)
                            .border(1.dp, palette.background, RoundedCornerShape(6.dp))
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
    }
}
