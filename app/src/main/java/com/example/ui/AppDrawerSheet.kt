package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backspace
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.appindexer.AppIndexerRepository
import com.example.appindexer.BiometricVaultController
import com.example.appindexer.IndexedApp
import com.example.appindexer.NothingGlyphCatalog
import com.example.coreui.DotMatrixDivider
import com.example.coreui.MaskedAppIcon
import com.example.coreui.NothingPillChip
import com.example.coreui.NothingSurfaceCard
import com.example.settingsstore.model.BadgeStyle
import com.example.settingsstore.model.DrawerMode
import com.example.settingsstore.model.DrawerTabPosition
import com.example.settingsstore.model.IconMaskConfig
import com.example.settingsstore.model.LauncherSettings
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Immutable
private data class PreparedDrawerApp(
    val app: IndexedApp,
    val displayLabel: String,
    val displayCategory: String,
    val customImageUri: String?,
    val customGlyphVector: ImageVector?,
    val monochromeOverride: Boolean?,
    val iconScaleOverride: Float?
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerSheet(
    apps: List<IndexedApp>,
    settings: LauncherSettings,
    appIndexer: AppIndexerRepository,
    iconVersion: Int,
    badgeCounts: Map<String, Int> = emptyMap(),
    contextHiddenPackages: Set<String> = emptySet(),
    gridState: LazyGridState = rememberLazyGridState(),
    listState: LazyListState = rememberLazyListState(),
    onAppClick: (IndexedApp) -> Unit,
    onAppLongPress: (IndexedApp) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVault: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val palette = LocalNothingPalette.current

    // Pre-filter, sort, and format labels once outside scroll frames
    val preparedApps = remember(
        apps,
        settings.appCustomizations,
        contextHiddenPackages,
        settings.typographyDrawer.textCase
    ) {
        val textCase = settings.typographyDrawer.textCase
        val customizations = settings.appCustomizations
        apps.asSequence()
            .filter { app ->
                val custom = customizations[app.packageName]
                custom?.hidden != true && custom?.lockedInVault != true && app.packageName !in contextHiddenPackages
            }
            .sortedWith(
                compareByDescending<IndexedApp> {
                    customizations[it.packageName]?.pinnedToTop == true
                }.thenBy {
                    (customizations[it.packageName]?.customLabel ?: it.label).lowercase()
                }
            )
            .map { app ->
                val custom = customizations[app.packageName]
                val rawLabel = custom?.customLabel ?: app.label
                PreparedDrawerApp(
                    app = app,
                    displayLabel = NothingTypographyEngine.formatText(rawLabel, textCase),
                    displayCategory = custom?.customCategory ?: app.category,
                    customImageUri = custom?.customImageUri,
                    customGlyphVector = NothingGlyphCatalog.resolve(custom?.customGlyphId),
                    monochromeOverride = custom?.monochromeOverride,
                    iconScaleOverride = custom?.iconScaleOverride
                )
            }
            .toList()
    }

    val drawerTextStyle = remember(settings.typographyDrawer, palette.textPrimary) {
        NothingTypographyEngine.resolveTextStyle(settings.typographyDrawer)
            .copy(color = palette.textPrimary)
    }

    val categories = remember(preparedApps) {
        val cats = preparedApps.map { it.displayCategory }.distinct().sorted()
        listOf("All") + cats
    }
    var selectedCategory by remember { mutableStateOf("All") }
    val filteredApps = remember(preparedApps, selectedCategory) {
        if (selectedCategory == "All") preparedApps
        else preparedApps.filter { it.displayCategory == selectedCategory }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.drawerBg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = settings.grid.marginDp.dp)
            .testTag("app_drawer_sheet")
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.borderSubtle, RoundedCornerShape(999.dp))
                    .clickable { onOpenSearch() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = palette.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = settings.searchConfig.placeholderText,
                    fontFamily = NothingTypographyEngine.ntype82Family,
                    fontSize = 12.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onOpenVault, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Rounded.Lock, "Biometric Vault", tint = palette.textPrimary)
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Rounded.Settings, "Launcher Settings", tint = palette.textPrimary)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Rounded.Close, "Close Drawer", tint = palette.textPrimary)
            }
        }

        @Composable
        fun CategoryTabsRow() {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    NothingPillChip(
                        label = cat,
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat }
                    )
                }
            }
        }

        if (settings.drawerMode == DrawerMode.CATEGORIZED_TABS &&
            settings.drawerTabPosition == DrawerTabPosition.TOP
        ) {
            CategoryTabsRow()
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (settings.drawerMode) {
                DrawerMode.VERTICAL_LIST -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = preparedApps,
                            key = { it.app.packageName },
                            contentType = { "drawer_list_item" }
                        ) { item ->
                            DrawerListAppRow(
                                item = item,
                                maskConfig = settings.iconMask,
                                appIndexer = appIndexer,
                                iconVersion = iconVersion,
                                badgeCount = badgeCounts[item.app.packageName] ?: 0,
                                badgeStyle = settings.badgeStyle,
                                deepFocusActive = settings.deepFocusActive,
                                labelStyle = drawerTextStyle,
                                secondaryColor = palette.textSecondary,
                                onAppClick = onAppClick,
                                onAppLongPress = onAppLongPress
                            )
                        }
                    }
                }

                DrawerMode.ALPHABETICAL_GRID, DrawerMode.CATEGORIZED_TABS -> {
                    val listToShow = if (settings.drawerMode == DrawerMode.CATEGORIZED_TABS) filteredApps else preparedApps
                    val cols = settings.grid.drawerColumns.coerceIn(2, 12)
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(cols),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(settings.grid.horizontalPaddingDp.dp),
                        verticalArrangement = Arrangement.spacedBy(settings.grid.verticalPaddingDp.dp)
                    ) {
                        items(
                            items = listToShow,
                            key = { it.app.packageName },
                            contentType = { "drawer_grid_tile" }
                        ) { item ->
                            DrawerGridAppTile(
                                item = item,
                                maskConfig = settings.iconMask,
                                appIndexer = appIndexer,
                                iconVersion = iconVersion,
                                badgeCount = badgeCounts[item.app.packageName] ?: 0,
                                badgeStyle = settings.badgeStyle,
                                deepFocusActive = settings.deepFocusActive,
                                labelStyle = drawerTextStyle,
                                onAppClick = onAppClick,
                                onAppLongPress = onAppLongPress
                            )
                        }
                    }
                }

                DrawerMode.HORIZONTAL_PAGED -> {
                    val cols = settings.grid.drawerColumns.coerceIn(2, 12)
                    val rows = settings.grid.drawerRows.coerceIn(2, 12)
                    val perPage = (cols * rows).coerceAtLeast(4)
                    val chunks = remember(preparedApps, perPage) { preparedApps.chunked(perPage) }
                    val pagerState = rememberPagerState(pageCount = { chunks.size.coerceAtLeast(1) })
                    Column(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) { page ->
                            val pageApps = chunks.getOrNull(page).orEmpty()
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(cols),
                                contentPadding = PaddingValues(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(settings.grid.horizontalPaddingDp.dp),
                                verticalArrangement = Arrangement.spacedBy(settings.grid.verticalPaddingDp.dp)
                            ) {
                                items(
                                    items = pageApps,
                                    key = { it.app.packageName },
                                    contentType = { "drawer_grid_tile" }
                                ) { item ->
                                    DrawerGridAppTile(
                                        item = item,
                                        maskConfig = settings.iconMask,
                                        appIndexer = appIndexer,
                                        iconVersion = iconVersion,
                                        badgeCount = badgeCounts[item.app.packageName] ?: 0,
                                        badgeStyle = settings.badgeStyle,
                                        deepFocusActive = settings.deepFocusActive,
                                        labelStyle = drawerTextStyle,
                                        onAppClick = onAppClick,
                                        onAppLongPress = onAppLongPress
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(chunks.size.coerceAtLeast(1)) { idx ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (pagerState.currentPage == idx) 8.dp else 5.dp)
                                        .clip(CircleShape)
                                        .background(if (pagerState.currentPage == idx) palette.accent else palette.borderSubtle)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (settings.drawerMode == DrawerMode.CATEGORIZED_TABS &&
            settings.drawerTabPosition == DrawerTabPosition.BOTTOM
        ) {
            CategoryTabsRow()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerListAppRow(
    item: PreparedDrawerApp,
    maskConfig: IconMaskConfig,
    appIndexer: AppIndexerRepository,
    iconVersion: Int,
    badgeCount: Int,
    badgeStyle: BadgeStyle,
    deepFocusActive: Boolean,
    labelStyle: TextStyle,
    secondaryColor: Color,
    onAppClick: (IndexedApp) -> Unit,
    onAppLongPress: (IndexedApp) -> Unit
) {
    val bitmap = appIndexer.rememberAppIcon(
        packageName = item.app.packageName,
        activityName = item.app.activityName,
        iconVersion = iconVersion
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { onAppClick(item.app) },
                onLongClick = { onAppLongPress(item.app) }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MaskedAppIcon(
            iconBitmap = bitmap,
            maskConfig = maskConfig,
            baseSize = 42.dp,
            customImageUri = item.customImageUri,
            customGlyphVector = item.customGlyphVector,
            monochromeOverride = item.monochromeOverride,
            scaleOverride = item.iconScaleOverride,
            badgeCount = badgeCount,
            badgeStyle = badgeStyle,
            dimmedForFocus = deepFocusActive
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayLabel,
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.displayCategory,
                fontSize = 11.sp,
                color = secondaryColor
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerGridAppTile(
    item: PreparedDrawerApp,
    maskConfig: IconMaskConfig,
    appIndexer: AppIndexerRepository,
    iconVersion: Int,
    badgeCount: Int,
    badgeStyle: BadgeStyle,
    deepFocusActive: Boolean,
    labelStyle: TextStyle,
    onAppClick: (IndexedApp) -> Unit,
    onAppLongPress: (IndexedApp) -> Unit
) {
    val bitmap = appIndexer.rememberAppIcon(
        packageName = item.app.packageName,
        activityName = item.app.activityName,
        iconVersion = iconVersion
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = { onAppClick(item.app) },
                onLongClick = { onAppLongPress(item.app) }
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MaskedAppIcon(
            iconBitmap = bitmap,
            maskConfig = maskConfig,
            baseSize = 48.dp,
            customImageUri = item.customImageUri,
            customGlyphVector = item.customGlyphVector,
            monochromeOverride = item.monochromeOverride,
            scaleOverride = item.iconScaleOverride,
            badgeCount = badgeCount,
            badgeStyle = badgeStyle,
            dimmedForFocus = deepFocusActive
        )
        Spacer(Modifier.height(maskConfig.labelOffsetDp.dp))
        Text(
            text = item.displayLabel,
            style = labelStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BiometricVaultSheet(
    apps: List<IndexedApp>,
    settings: LauncherSettings,
    appIndexer: AppIndexerRepository,
    vaultUnlocked: Boolean,
    showPinPrompt: Boolean,
    onSetUnlocked: (Boolean) -> Unit,
    onSetShowPinPrompt: (Boolean) -> Unit,
    onLaunchApp: (IndexedApp) -> Unit,
    onUnhideOrUnlockApp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val palette = LocalNothingPalette.current
    var enteredPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!vaultUnlocked) {
            BiometricVaultController.promptBiometric(
                context = context,
                onSuccess = { onSetUnlocked(true) },
                onUsePinFallback = { onSetShowPinPrompt(true) }
            )
        }
    }

    val vaultApps = remember(apps, settings.appCustomizations) {
        apps.filter { app ->
            val custom = settings.appCustomizations[app.packageName]
            custom?.lockedInVault == true || custom?.hidden == true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.96f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        if (!vaultUnlocked) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.Lock, null, tint = palette.accent, modifier = Modifier.size(42.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "BIOMETRIC VAULT",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 18.sp,
                    color = palette.textPrimary
                )
                Text(
                    text = "Enter PIN (default 0000) or authenticate",
                    fontSize = 12.sp,
                    color = palette.textSecondary
                )
                Spacer(Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { idx ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (idx < enteredPin.length) palette.accent else palette.borderSubtle
                                )
                        )
                    }
                }
                if (pinError) {
                    Spacer(Modifier.height(8.dp))
                    Text("Incorrect PIN", color = palette.accent, fontSize = 12.sp)
                }

                Spacer(Modifier.height(22.dp))
                val rows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("BIO", "0", "DEL")
                )
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.padding(vertical = 6.dp)
                    ) {
                        row.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(palette.surface)
                                    .border(1.dp, palette.borderSubtle, CircleShape)
                                    .clickable {
                                        when (key) {
                                            "DEL" -> if (enteredPin.isNotEmpty()) enteredPin = enteredPin.dropLast(1)
                                            "BIO" -> BiometricVaultController.promptBiometric(
                                                context = context,
                                                onSuccess = { onSetUnlocked(true) },
                                                onUsePinFallback = { onSetShowPinPrompt(true) }
                                            )
                                            else -> {
                                                if (enteredPin.length < 4) {
                                                    enteredPin += key
                                                    pinError = false
                                                    if (enteredPin.length == 4) {
                                                        if (enteredPin == settings.vaultPinHash) {
                                                            onSetUnlocked(true)
                                                        } else {
                                                            pinError = true
                                                            enteredPin = ""
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (key == "DEL") {
                                    Icon(Icons.Rounded.Backspace, "Backspace", tint = palette.textPrimary)
                                } else {
                                    Text(
                                        text = key,
                                        fontFamily = NothingTypographyEngine.ndot55Family,
                                        fontSize = if (key == "BIO") 12.sp else 20.sp,
                                        color = if (key == "BIO") palette.accent else palette.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "HIDDEN & LOCKED APPS",
                        fontFamily = NothingTypographyEngine.ndot55Family,
                        fontSize = 16.sp,
                        color = palette.accent
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, "Close Vault", tint = palette.textPrimary)
                    }
                }
                DotMatrixDivider()
                Spacer(Modifier.height(10.dp))
                if (vaultApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No apps locked in Vault.\nLong-press any app to Hide or Lock in Biometric Vault.",
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(vaultApps, key = { it.packageName }) { app ->
                            val custom = settings.appCustomizations[app.packageName]
                            val bitmap = appIndexer.rememberAppIcon(app.packageName, app.activityName)
                            NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onLaunchApp(app) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MaskedAppIcon(iconBitmap = bitmap, maskConfig = settings.iconMask, baseSize = 42.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(custom?.customLabel ?: app.label, color = palette.textPrimary, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = if (custom?.lockedInVault == true) "Vault Locked" else "Hidden",
                                            fontSize = 11.sp,
                                            color = palette.accent
                                        )
                                    }
                                    IconButton(onClick = { onUnhideOrUnlockApp(app.packageName) }) {
                                        Icon(Icons.Rounded.Visibility, "Restore app", tint = palette.textPrimary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MindfulFrictionCountdownDialog(
    app: IndexedApp,
    frictionSeconds: Int,
    dailyLimitMinutes: Int,
    onProceedLaunch: () -> Unit,
    onCancel: () -> Unit
) {
    val palette = LocalNothingPalette.current
    var remaining by remember(app.packageName) { mutableIntStateOf(frictionSeconds.coerceIn(1, 10)) }

    LaunchedEffect(app.packageName) {
        while (remaining > 0) {
            delay(1000L)
            remaining--
        }
    }

    Dialog(onDismissRequest = onCancel) {
        NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "MINDFUL PAUSE",
                    fontFamily = NothingTypographyEngine.ndot55Family,
                    fontSize = 14.sp,
                    color = palette.accent
                )
                Text(
                    text = "Take a breath before opening ${app.label}",
                    color = palette.textPrimary,
                    textAlign = TextAlign.Center
                )
                if (dailyLimitMinutes > 0 && app.dailyMinutesUsed >= dailyLimitMinutes) {
                    Text(
                        text = "Daily limit reached (${app.dailyMinutesUsed}m / ${dailyLimitMinutes}m)",
                        color = palette.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val totalDots = 24
                        val c = center
                        val r = size.minDimension * 0.42f
                        val activeDots = ((remaining.toFloat() / frictionSeconds.coerceAtLeast(1)) * totalDots).toInt()
                        for (i in 0 until totalDots) {
                            val a = -PI / 2.0 + 2.0 * PI * i / totalDots
                            drawCircle(
                                color = if (i < activeDots) palette.accent else palette.borderSubtle,
                                radius = if (i < activeDots) 4.2f else 2.4f,
                                center = androidx.compose.ui.geometry.Offset(
                                    c.x + r * cos(a).toFloat(),
                                    c.y + r * sin(a).toFloat()
                                )
                            )
                        }
                        drawCircle(color = palette.borderSubtle, radius = r * 0.65f, style = Stroke(1.5f))
                    }
                    Text(
                        text = if (remaining > 0) "${remaining}s" else "READY",
                        fontFamily = NothingTypographyEngine.ndot55Family,
                        fontSize = 22.sp,
                        color = palette.textPrimary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onCancel) { Text("Stay Focused") }
                    Button(
                        onClick = onProceedLaunch,
                        enabled = remaining == 0,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                    ) {
                        Text("Open App", color = Color.White)
                    }
                }
            }
        }
    }
}
