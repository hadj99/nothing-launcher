package com.example.widgethost

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

@Immutable
private sealed class StackWidgetEntry {
    data class Custom(val type: CustomWidgetType) : StackWidgetEntry()
    data class System(val appWidgetId: Int) : StackWidgetEntry()
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
    mediaState: ActiveMediaState = ActiveMediaState(),
    hasBluetoothPermission: Boolean = true,
    onRequestBluetoothPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalNothingPalette.current
    val entries = slot.stackedCustomWidgets.map { StackWidgetEntry.Custom(it) } +
        slot.stackedSystemWidgetIds.map { StackWidgetEntry.System(it) }

    if (entries.isEmpty()) {
        NothingSurfaceCard(
            modifier = modifier
                .fillMaxWidth()
                .height(152.dp)
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(156.dp)
            .testTag("widget_stack_${slot.id}")
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1
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
                        onRequestBluetoothPermission = onRequestBluetoothPermission
                    )
                }

                is StackWidgetEntry.System -> {
                    NothingSurfaceCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(152.dp),
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
}
