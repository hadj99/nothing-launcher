package com.example.widgethost

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.appindexer.ScreenTimeVelocity
import com.example.contextautomation.ActiveMediaState
import com.example.contextautomation.ContextAutomationEngine
import com.example.contextautomation.DeviceTelemetrySnapshot
import com.example.coreui.NothingSurfaceCard
import com.example.customwidgets.NothingCustomWidgetCard
import com.example.customwidgets.WeatherState
import com.example.settingsstore.db.StepHistoryEntity
import com.example.settingsstore.model.CustomWidgetType
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.WidgetStackSlot
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import kotlinx.coroutines.withTimeoutOrNull

@Immutable
private sealed class StackWidgetEntry {
    data class Custom(val type: CustomWidgetType) : StackWidgetEntry()
    data class System(val appWidgetId: Int) : StackWidgetEntry()
}

private fun Modifier.onWidgetLongPress(
    key: Any?,
    onLongPress: () -> Unit
): Modifier = pointerInput(key) {
    val slop = viewConfiguration.touchSlop
    val timeout = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startPos = down.position
        var movedOrUp = false
        val timedOut = withTimeoutOrNull(timeout) {
            while (!movedOrUp) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) {
                    movedOrUp = true
                } else if ((change.position - startPos).getDistance() > slop) {
                    movedOrUp = true
                }
            }
            false
        } == null

        if (timedOut && !movedOrUp) {
            onLongPress()
            var stillDown = true
            while (stillDown) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                stillDown = event.changes.any { it.pressed }
            }
        }
    }
}

@Composable
fun InteractiveWidgetStackCard(
    slot: WidgetStackSlot,
    settings: LauncherSettings,
    widgetHostManager: LauncherWidgetHostManager,
    stepsToday: Int,
    stepSensorAvailable: Boolean,
    hasStepPermission: Boolean,
    stepHistory: List<StepHistoryEntity>,
    weatherState: WeatherState,
    telemetry: DeviceTelemetrySnapshot,
    screenTimeVelocity: ScreenTimeVelocity,
    contextEngine: ContextAutomationEngine,
    onRequestStepSync: () -> Unit,
    onCycleTimeFormat: () -> Unit,
    onRefreshWeather: () -> Unit,
    onCycleYearMode: () -> Unit,
    onToggleDeepFocus: () -> Unit,
    onOpenUsageAccess: () -> Unit,
    onOpenWidgetSettings: (CustomWidgetType) -> Unit,
    onOpenStackEditor: (WidgetStackSlot) -> Unit,
    editMode: Boolean = false,
    onLongPressSlot: (WidgetStackSlot) -> Unit = { onOpenStackEditor(it) },
    onUpdateSlot: ((WidgetStackSlot) -> WidgetStackSlot) -> Unit = {},
    onDeleteSlot: () -> Unit = {},
    mediaState: ActiveMediaState = ActiveMediaState(),
    hasBluetoothPermission: Boolean = true,
    onRequestBluetoothPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val cardHeight = slot.heightDp.coerceIn(96, 340)
    val contentScale = slot.contentScale.coerceIn(0.7f, 1.45f)

    val entries = remember(slot.stackedCustomWidgets, slot.stackedSystemWidgetIds) {
        slot.stackedCustomWidgets.map { StackWidgetEntry.Custom(it) } +
            slot.stackedSystemWidgetIds.map { StackWidgetEntry.System(it) }
    }

    if (entries.isEmpty()) {
        NothingSurfaceCard(
            modifier = modifier
                .fillMaxWidth()
                .height(cardHeight.dp)
                .clickable { onOpenStackEditor(slot) }
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Rounded.Add, null, tint = palette.accent)
                Text(
                    text = " ADD WIDGET TO STACK",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
            }
        }
        return
    }

    val pagerState = rememberPagerState(
        initialPage = slot.activeIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0)),
        pageCount = { entries.size }
    )

    LaunchedEffect(entries.size) {
        if (pagerState.currentPage >= entries.size) {
            pagerState.scrollToPage(0)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((cardHeight + 4).dp)
                .then(
                    if (editMode) {
                        Modifier.border(
                            width = 1.5.dp,
                            color = palette.accent.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(23.dp)
                        )
                    } else {
                        Modifier
                    }
                )
                .onWidgetLongPress(slot.id) { onLongPressSlot(slot) }
                .testTag("widget_stack_${slot.id}")
        ) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0
            ) { page ->
                when (val entry = entries[page]) {
                    is StackWidgetEntry.Custom -> {
                        NothingCustomWidgetCard(
                            widgetType = entry.type,
                            settings = settings,
                            stepsToday = stepsToday,
                            stepSensorAvailable = stepSensorAvailable,
                            hasStepPermission = hasStepPermission,
                            stepHistory = stepHistory,
                            weatherState = weatherState,
                            telemetry = telemetry,
                            screenTimeVelocity = screenTimeVelocity,
                            contextEngine = contextEngine,
                            onRequestStepSync = onRequestStepSync,
                            onCycleTimeFormat = onCycleTimeFormat,
                            onRefreshWeather = onRefreshWeather,
                            onCycleYearMode = onCycleYearMode,
                            onToggleDeepFocus = onToggleDeepFocus,
                            onOpenUsageAccess = onOpenUsageAccess,
                            onOpenWidgetSettings = onOpenWidgetSettings,
                            mediaState = mediaState,
                            hasBluetoothPermission = hasBluetoothPermission,
                            onRequestBluetoothPermission = onRequestBluetoothPermission,
                            cardHeightDp = cardHeight,
                            contentScale = contentScale
                        )
                    }

                    is StackWidgetEntry.System -> {
                        NothingSurfaceCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(cardHeight.dp),
                            cornerRadius = 22.dp
                        ) {
                            AndroidView(
                                factory = {
                                    widgetHostManager.createHostView(entry.appWidgetId)
                                        ?: android.widget.TextView(it).apply {
                                            text = "System Widget #${entry.appWidgetId}"
                                        }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }

            // Right-edge dot-matrix vertical stack indicator + stack configure button
            if (entries.size > 1) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.background.copy(alpha = 0.55f))
                        .clickable { onOpenStackEditor(slot) }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    entries.indices.forEach { idx ->
                        val selected = idx == pagerState.currentPage
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(if (selected) 12.dp else 4.dp)
                                .clip(CircleShape)
                                .background(if (selected) palette.accent else palette.textSecondary.copy(alpha = 0.5f))
                        )
                    }
                }
            }
        }

        // Direct on-widget resize & layout controls when Home Edit Mode is active
        if (editMode) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.surface.copy(alpha = 0.94f))
                    .border(1.dp, palette.borderSubtle, RoundedCornerShape(12.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shrink / Enlarge Height buttons + presets
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniResizeActionChip(
                        icon = Icons.Rounded.Remove,
                        contentDescription = "Make Smaller",
                        onClick = {
                            onUpdateSlot { s ->
                                val nextH = (s.heightDp - 24).coerceAtLeast(96)
                                val nextScale = (nextH / 156f).coerceIn(0.78f, 1.3f)
                                s.copy(heightDp = nextH, contentScale = nextScale)
                            }
                        }
                    )
                    listOf("S" to 112, "M" to 156, "L" to 216, "XL" to 276).forEach { (lbl, h) ->
                        val active = kotlin.math.abs(cardHeight - h) <= 14
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) palette.accent else palette.background.copy(alpha = 0.6f))
                                .clickable {
                                    onUpdateSlot { s ->
                                        val nextScale = when (h) {
                                            112 -> 0.84f
                                            156 -> 1.0f
                                            216 -> 1.12f
                                            else -> 1.24f
                                        }
                                        s.copy(heightDp = h, contentScale = nextScale)
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = lbl,
                                fontFamily = NothingTypographyEngine.ndot55Family,
                                fontSize = 9.sp,
                                color = if (active) Color.White else palette.textSecondary
                            )
                        }
                    }
                    MiniResizeActionChip(
                        icon = Icons.Rounded.Add,
                        contentDescription = "Make Larger",
                        onClick = {
                            onUpdateSlot { s ->
                                val nextH = (s.heightDp + 24).coerceAtMost(340)
                                val nextScale = (nextH / 156f).coerceIn(0.78f, 1.35f)
                                s.copy(heightDp = nextH, contentScale = nextScale)
                            }
                        }
                    )
                }

                // Width toggle (Half 1/2 vs Full) + Stack editor + Delete
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isHalf = slot.widthFraction <= 0.55f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isHalf) palette.accent.copy(alpha = 0.22f) else palette.background.copy(alpha = 0.6f))
                            .border(1.dp, if (isHalf) palette.accent else palette.borderSubtle, RoundedCornerShape(6.dp))
                            .clickable {
                                onUpdateSlot { s ->
                                    s.copy(widthFraction = if (isHalf) 1.0f else 0.5f)
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isHalf) "1/2 W" else "FULL",
                            fontFamily = NothingTypographyEngine.ntype82Family,
                            fontSize = 8.5.sp,
                            color = if (isHalf) palette.accent else palette.textSecondary
                        )
                    }
                    MiniResizeActionChip(
                        icon = Icons.Rounded.Tune,
                        contentDescription = "Customize & Resize",
                        onClick = { onOpenStackEditor(slot) }
                    )
                    MiniResizeActionChip(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Remove Widget Block",
                        tint = palette.accent,
                        onClick = onDeleteSlot
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniResizeActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color? = null,
    onClick: () -> Unit
) {
    val palette = LocalNothingPalette.current
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(palette.background.copy(alpha = 0.7f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint ?: palette.textPrimary,
            modifier = Modifier.size(14.dp)
        )
    }
}
