package com.example.coreui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.animationphysics.NothingHaptics
import com.example.themeengine.LocalLauncherSettings
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import kotlin.math.roundToInt

@Composable
fun NothingSurfaceCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderWidth: Dp = 1.dp,
    backgroundColor: Color = LocalNothingPalette.current.widgetBg,
    borderColor: Color = LocalNothingPalette.current.borderSubtle,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(backgroundColor)
            .then(if (borderWidth > 0.dp) Modifier.border(borderWidth, borderColor, shape) else Modifier),
        content = content
    )
}

@Composable
fun DotMatrixDivider(
    modifier: Modifier = Modifier,
    dotColor: Color = LocalNothingPalette.current.borderSubtle,
    dotRadiusDp: Float = 1.5f
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
    ) {
        val spacing = 10.dp.toPx()
        var x = spacing / 2f
        val cy = size.height / 2f
        val r = dotRadiusDp.dp.toPx()
        while (x < size.width) {
            drawCircle(color = dotColor, radius = r, center = Offset(x, cy))
            x += spacing
        }
    }
}

@Composable
fun NothingSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.borderSubtle, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(palette.accent)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.uppercase(),
                fontFamily = NothingTypographyEngine.ndot55Family,
                fontSize = 12.5.sp,
                letterSpacing = 1.5.sp,
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontFamily = NothingTypographyEngine.bodyInterFamily,
                    fontSize = 11.5.sp,
                    color = palette.textSecondary
                )
            }
        }
    }
}

/**
 * Grouped settings card: a titled surface that visually clusters related controls so the
 * Studio never looks like one long undifferentiated list of rows.
 */
@Composable
fun NothingGroupCard(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val palette = LocalNothingPalette.current
    NothingSurfaceCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        backgroundColor = palette.surface.copy(alpha = 0.55f),
        borderColor = palette.borderSubtle
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NothingSectionHeader(title = title, subtitle = subtitle)
            content()
        }
    }
}

@Composable
fun NothingSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    valueFormatter: (Float) -> String = { "${(it * 10).roundToInt() / 10f}" },
    steps: Int = 0,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontFamily = NothingTypographyEngine.cleanSansFamily,
                fontSize = 13.sp,
                color = palette.textPrimary
            )
            Text(
                text = valueFormatter(value),
                fontFamily = NothingTypographyEngine.ntype82Family,
                fontSize = 12.sp,
                color = palette.accent
            )
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = palette.accent,
                activeTrackColor = palette.accent,
                inactiveTrackColor = palette.borderSubtle
            )
        )
    }
}

@Composable
fun NothingToggleRow(
    label: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val settings = LocalLauncherSettings.current
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clickable {
                NothingHaptics.performTick(context, settings.physics)
                onCheckedChange(!checked)
            }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = label,
                fontFamily = NothingTypographyEngine.cleanSansFamily,
                fontSize = 14.sp,
                color = palette.textPrimary
            )
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    fontFamily = NothingTypographyEngine.bodyInterFamily,
                    fontSize = 11.5.sp,
                    color = palette.textSecondary
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                NothingHaptics.performTick(context, settings.physics)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.accent,
                uncheckedThumbColor = palette.textSecondary,
                uncheckedTrackColor = palette.surface
            )
        )
    }
}

@Composable
fun NothingPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val settings = LocalLauncherSettings.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(if (selected) palette.accent else palette.surface)
            .border(1.dp, if (selected) palette.accent else palette.borderSubtle, shape)
            .clickable {
                NothingHaptics.performTick(context, settings.physics)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label.uppercase(),
            fontFamily = NothingTypographyEngine.ntype82Family,
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = if (selected) Color.White else palette.textPrimary,
            maxLines = 1
        )
    }
}
