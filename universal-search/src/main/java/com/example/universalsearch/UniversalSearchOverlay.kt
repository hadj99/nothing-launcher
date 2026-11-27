package com.example.universalsearch

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.random.Random
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.appindexer.AppIndexerRepository
import com.example.appindexer.AppShortcutInfo
import com.example.appindexer.IndexedApp
import com.example.appindexer.NothingGlyphCatalog
import com.example.coreui.MaskedAppIcon
import com.example.coreui.NothingPillChip
import com.example.coreui.NothingSurfaceCard
import com.example.settingsstore.model.LauncherSettings
import com.example.settingsstore.model.SearchBarPosition
import com.example.settingsstore.model.SearchSourceType
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine

@Composable
fun UniversalSearchOverlay(
    query: String,
    onQueryChange: (String) -> Unit,
    searchState: UniversalSearchState,
    recentQueries: List<String>,
    settings: LauncherSettings,
    appIndexer: AppIndexerRepository,
    searchEngine: UniversalSearchEngine,
    onLaunchApp: (IndexedApp) -> Unit,
    onLaunchShortcut: (AppShortcutInfo) -> Unit,
    onOpenLauncherSettingsTab: (Int) -> Unit,
    onAddSafFolderUri: (String) -> Unit,
    onToggleIncognito: () -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
    onCommitQuery: (String) -> Unit = {},
    onToggleTorch: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val palette = LocalNothingPalette.current
    val searchTextStyle = remember(settings.typographySearch) {
        NothingTypographyEngine.resolveTextStyle(settings.typographySearch)
    }
    val focusRequester = remember { FocusRequester() }
    var contactsRequested by remember { mutableStateOf(false) }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        contactsRequested = true
        if (granted) onQueryChange(query)
    }

    val safFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            onAddSafFolderUri(uri.toString())
        }
    }

    LaunchedEffect(settings.searchConfig.autoOpenKeyboard) {
        if (settings.searchConfig.autoOpenKeyboard) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val barAtBottom = settings.searchConfig.position == SearchBarPosition.BOTTOM

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.94f))
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        @Composable
        fun SearchBarField() {
            val shape = RoundedCornerShape(settings.searchConfig.cornerRadiusDp.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(palette.surface.copy(alpha = settings.searchConfig.backgroundOpacity.coerceIn(0.2f, 1f)))
                    .border(
                        settings.searchConfig.borderWidthDp.dp,
                        palette.borderSubtle,
                        shape
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = settings.searchConfig.placeholderText,
                            style = searchTextStyle.copy(color = palette.textSecondary)
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = searchTextStyle.copy(color = palette.textPrimary),
                        cursorBrush = SolidColor(palette.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                val trimmed = query.trim()
                                if (trimmed.isNotEmpty()) {
                                    onCommitQuery(trimmed)
                                }
                                val firstApp = searchState.appMatches.firstOrNull()
                                if (firstApp != null) {
                                    onLaunchApp(firstApp)
                                } else if (trimmed.isNotEmpty()) {
                                    searchState.webEngines.firstOrNull()?.let {
                                        searchEngine.executeWebSearch(it, trimmed)
                                    }
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("universal_search_input")
                    )
                }
                if (query.trim().isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.accent)
                            .clickable {
                                val trimmed = query.trim()
                                onCommitQuery(trimmed)
                                val firstApp = searchState.appMatches.firstOrNull()
                                if (firstApp != null) {
                                    onLaunchApp(firstApp)
                                } else {
                                    searchState.webEngines.firstOrNull()?.let {
                                        searchEngine.executeWebSearch(it, trimmed)
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "GO",
                            fontFamily = NothingTypographyEngine.ndot55Family,
                            fontSize = 10.sp,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                }
                IconButton(
                    onClick = onToggleIncognito,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VisibilityOff,
                        contentDescription = "Toggle Incognito Search",
                        tint = if (settings.searchConfig.incognitoMode) palette.accent else palette.textSecondary
                    )
                }
                IconButton(
                    onClick = {
                        if (query.isNotEmpty()) onQueryChange("") else onDismiss()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("universal_search_close")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear or close search",
                        tint = palette.textPrimary
                    )
                }
            }
        }

        if (!barAtBottom) {
            SearchBarField()
            Spacer(Modifier.height(8.dp))
        }

        // Quick Tools Deck above search results & history
        SearchQuickToolsDeck(
            query = query,
            onSetQuery = onQueryChange,
            onFireTimerSeconds = { secs -> searchEngine.fireTimer(UtilityResult.QuickTimer("${secs / 60}m", secs)) },
            onToggleTorch = onToggleTorch,
            onOpenSettings = { onOpenLauncherSettingsTab(0) }
        )
        Spacer(Modifier.height(8.dp))

        // Web & In-App Search Pills
        if (query.isNotBlank() && searchState.webEngines.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                searchState.webEngines.forEach { engine ->
                    NothingPillChip(
                        label = "${engine.name}: $query",
                        selected = false,
                        onClick = {
                            onCommitQuery(query.trim())
                            searchEngine.executeWebSearch(engine, query)
                        }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (query.isBlank()) {
                if (recentQueries.isNotEmpty() && !settings.searchConfig.incognitoMode) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "RECENT QUERIES",
                                fontFamily = NothingTypographyEngine.ndot55Family,
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            )
                            Text(
                                text = "CLEAR",
                                fontFamily = NothingTypographyEngine.ntype82Family,
                                fontSize = 11.sp,
                                color = palette.accent,
                                modifier = Modifier
                                    .clickable { onClearHistory() }
                                    .padding(4.dp)
                            )
                        }
                    }
                    items(recentQueries) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.surface)
                                .clickable { onQueryChange(item) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.History, null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(item, color = palette.textPrimary, fontSize = 14.sp)
                        }
                    }
                }

                // Helpful tips & zero-permission / scoped actions
                item {
                    NothingSurfaceCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "SEARCH EVERYTHING",
                                fontFamily = NothingTypographyEngine.ndot55Family,
                                fontSize = 12.sp,
                                color = palette.accent
                            )
                            Text(
                                text = "Try math (245 * 18), FX/units (100 USD to DZD, 5 km to miles), timers (timer 10m), hex (#D71921), apps, contacts, settings, or files.",
                                fontSize = 12.sp,
                                color = palette.textSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!searchState.contactsPermissionGranted && !contactsRequested) {
                                    NothingPillChip(
                                        label = "Enable Contacts",
                                        selected = false,
                                        onClick = { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) }
                                    )
                                }
                                NothingPillChip(
                                    label = "Index Folder (SAF)",
                                    selected = false,
                                    onClick = { safFolderLauncher.launch(null) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Respect user-defined priority order for results
                settings.searchConfig.sourcePriorityOrder.forEach { source ->
                    when (source) {
                        SearchSourceType.UTILITIES -> {
                            items(searchState.utilityMatches) { util ->
                                UtilityResultCard(
                                    result = util,
                                    onCopyText = { textToCopy ->
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("DotOS", textToCopy))
                                        Toast.makeText(context, "Copied: $textToCopy", Toast.LENGTH_SHORT).show()
                                    },
                                    onFireTimer = { searchEngine.fireTimer(it) },
                                    onFireAlarm = { searchEngine.fireAlarm(it) }
                                )
                            }
                        }

                        SearchSourceType.APPS_SHORTCUTS -> {
                            if (searchState.appMatches.isNotEmpty()) {
                                items(searchState.appMatches, key = { "app_${it.packageName}" }) { app ->
                                    val custom = settings.appCustomizations[app.packageName]
                                    val displayLabel = custom?.customLabel ?: app.label
                                    val bitmap = appIndexer.getCachedIcon(app.packageName, app.activityName)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(palette.surface)
                                            .border(1.dp, palette.borderSubtle, RoundedCornerShape(14.dp))
                                            .clickable { onLaunchApp(app) }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        MaskedAppIcon(
                                            iconBitmap = bitmap,
                                            maskConfig = settings.iconMask,
                                            baseSize = 42.dp,
                                            customImageUri = custom?.customImageUri,
                                            customGlyphVector = NothingGlyphCatalog.resolve(custom?.customGlyphId),
                                            monochromeOverride = custom?.monochromeOverride,
                                            scaleOverride = custom?.iconScaleOverride
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = NothingTypographyEngine.formatText(displayLabel, settings.typographySearch.textCase),
                                                style = searchTextStyle.copy(color = palette.textPrimary)
                                            )
                                            Text(
                                                text = app.category,
                                                fontSize = 11.sp,
                                                color = palette.textSecondary
                                            )
                                        }
                                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            items(searchState.shortcutMatches) { sc ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.widgetBg)
                                        .clickable { onLaunchShortcut(sc) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("SHORTCUT", fontFamily = NothingTypographyEngine.ndot55Family, fontSize = 10.sp, color = palette.accent)
                                    Spacer(Modifier.width(10.dp))
                                    Text(sc.longLabel.ifBlank { sc.shortLabel }, color = palette.textPrimary, fontSize = 13.sp)
                                }
                            }
                        }

                        SearchSourceType.CONTACTS -> {
                            items(searchState.contactMatches, key = { "ct_${it.id}_${it.phoneNumber}" }) { ct ->
                                NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Person, null, tint = palette.accent)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(ct.displayName, color = palette.textPrimary, fontWeight = FontWeight.Medium)
                                            Text(ct.phoneNumber, color = palette.textSecondary, fontSize = 12.sp)
                                        }
                                        IconButton(onClick = { searchEngine.dialNumber(ct.phoneNumber) }) {
                                            Icon(Icons.Rounded.Call, "Call", tint = palette.textPrimary)
                                        }
                                        IconButton(onClick = { searchEngine.smsNumber(ct.phoneNumber) }) {
                                            Icon(Icons.Rounded.Sms, "SMS", tint = palette.textPrimary)
                                        }
                                        Text(
                                            text = "WA",
                                            fontFamily = NothingTypographyEngine.ntype82Family,
                                            fontSize = 11.sp,
                                            color = palette.accent,
                                            modifier = Modifier
                                                .clickable { searchEngine.openMessagingApp("com.whatsapp", ct.phoneNumber) }
                                                .padding(6.dp)
                                        )
                                        Text(
                                            text = "TG",
                                            fontFamily = NothingTypographyEngine.ntype82Family,
                                            fontSize = 11.sp,
                                            color = palette.textPrimary,
                                            modifier = Modifier
                                                .clickable { searchEngine.openMessagingApp("org.telegram.messenger", ct.phoneNumber) }
                                                .padding(6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        SearchSourceType.LOCAL_FILES -> {
                            items(searchState.fileMatches) { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.surface)
                                        .clickable { searchEngine.openFile(file) }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Description, null, tint = palette.accent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(file.name, color = palette.textPrimary, fontSize = 13.sp)
                                        Text(file.mimeType, color = palette.textSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        SearchSourceType.DEVICE_SETTINGS -> {
                            items(searchState.settingMatches, key = { it.id }) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.surface)
                                        .clickable {
                                            if (item.androidAction != null) {
                                                searchEngine.openSystemSetting(item.androidAction)
                                            } else if (item.launcherTabIndex != null) {
                                                onOpenLauncherSettingsTab(item.launcherTabIndex)
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Settings, null, tint = palette.accent, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, color = palette.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text(item.subtitle, color = palette.textSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }

                        SearchSourceType.CLIPBOARD -> {
                            items(searchState.clipboardMatches, key = { "clip_${it.hash}" }) { clip ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.surface)
                                        .border(1.dp, palette.borderSubtle, RoundedCornerShape(12.dp))
                                        .clickable {
                                            if (clip.isUrl) {
                                                searchEngine.openUrl(clip.text)
                                            } else {
                                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cb.setPrimaryClip(ClipData.newPlainText("DotOS Snippet", clip.text))
                                                Toast.makeText(context, "Copied snippet", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.ContentCopy, null, tint = palette.accent, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("CLIPBOARD SNIPPET", fontFamily = NothingTypographyEngine.ndot55Family, fontSize = 9.5.sp, color = palette.accent)
                                        Text(clip.text, color = palette.textPrimary, fontSize = 12.5.sp, maxLines = 2)
                                    }
                                }
                            }
                        }

                        SearchSourceType.WEB_ENGINES -> Unit
                    }
                }
            }
        }

        if (barAtBottom) {
            Spacer(Modifier.height(10.dp))
            SearchBarField()
        }
    }
}

@Composable
private fun UtilityResultCard(
    result: UtilityResult,
    onCopyText: (String) -> Unit,
    onFireTimer: (UtilityResult.QuickTimer) -> Unit,
    onFireAlarm: (UtilityResult.QuickAlarm) -> Unit
) {
    val palette = LocalNothingPalette.current
    NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
        when (result) {
            is UtilityResult.MathEvaluation -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCopyText(result.formattedResult) }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(result.expression, color = palette.textSecondary, fontSize = 12.sp)
                        Text(
                            text = "= ${result.formattedResult}",
                            fontFamily = NothingTypographyEngine.ndot77Family,
                            fontSize = 22.sp,
                            color = palette.accent
                        )
                    }
                    Icon(Icons.Rounded.ContentCopy, "Copy result", tint = palette.textSecondary)
                }
            }

            is UtilityResult.Conversion -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCopyText(result.targetText) }
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("${result.sourceText}  •  ${result.note}", color = palette.textSecondary, fontSize = 11.5.sp)
                        Text(
                            text = result.targetText,
                            fontFamily = NothingTypographyEngine.ndot77Family,
                            fontSize = 20.sp,
                            color = palette.textPrimary
                        )
                    }
                    Icon(Icons.Rounded.ContentCopy, "Copy conversion", tint = palette.accent)
                }
            }

            is UtilityResult.HexColorPreview -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCopyText(result.hex) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(result.argbInt))
                            .border(1.dp, palette.borderSubtle, CircleShape)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(result.hex, fontFamily = NothingTypographyEngine.ntype82Family, fontSize = 16.sp, color = palette.textPrimary)
                        Text(result.rgbLabel, color = palette.textSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Rounded.ContentCopy, "Copy Hex", tint = palette.textSecondary)
                }
            }

            is UtilityResult.QuickTimer -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFireTimer(result) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Timer, null, tint = palette.accent)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Start ${result.label}", color = palette.textPrimary, fontWeight = FontWeight.Medium)
                        Text("Tap to launch Android Clock timer", color = palette.textSecondary, fontSize = 11.5.sp)
                    }
                }
            }

            is UtilityResult.QuickAlarm -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onFireAlarm(result) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Alarm, null, tint = palette.accent)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Set Alarm for ${result.formattedTime}", color = palette.textPrimary, fontWeight = FontWeight.Medium)
                        Text("Tap to schedule in Clock", color = palette.textSecondary, fontSize = 11.5.sp)
                    }
                }
            }
        }
    }
}

private enum class QuickSearchTool(val label: String) {
    CALC("Calculator"),
    CONVERTER("Units & FX"),
    STOPWATCH("Stopwatch"),
    PASS_GEN("Pass / PIN"),
    CLIPBOARD("Clipboard"),
    RANDOM("Coin / Dice")
}

@Composable
private fun SearchQuickToolsDeck(
    query: String,
    onSetQuery: (String) -> Unit,
    onFireTimerSeconds: (Int) -> Unit,
    onToggleTorch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val palette = LocalNothingPalette.current
    var activeTool by remember { mutableStateOf<QuickSearchTool?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickSearchTool.entries.forEach { tool ->
                NothingPillChip(
                    label = tool.label,
                    selected = activeTool == tool,
                    onClick = { activeTool = if (activeTool == tool) null else tool }
                )
            }
            NothingPillChip(
                label = "Torch",
                selected = false,
                onClick = onToggleTorch
            )
            NothingPillChip(
                label = "Studio",
                selected = false,
                onClick = onOpenSettings
            )
        }

        when (activeTool) {
            QuickSearchTool.CALC -> {
                NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "QUICK MATH PAD (AUTO-EVALUATES BELOW)",
                            fontFamily = NothingTypographyEngine.ndot55Family,
                            fontSize = 10.sp,
                            color = palette.accent
                        )
                        val ops = listOf("7", "8", "9", "+", "(", "4", "5", "6", "-", ")", "1", "2", "3", "*", "/", "0", ".", "%", " * 0.15", "CLR")
                        ops.chunked(5).forEach { row ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { token ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (token == "CLR") palette.accent.copy(alpha = 0.22f) else palette.background)
                                            .border(1.dp, palette.borderSubtle, RoundedCornerShape(10.dp))
                                            .clickable {
                                                if (token == "CLR") onSetQuery("")
                                                else onSetQuery(query + token)
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (token == " * 0.15") "15%" else token,
                                            fontFamily = NothingTypographyEngine.ntype82Family,
                                            fontSize = 12.sp,
                                            color = palette.textPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            QuickSearchTool.CONVERTER -> {
                NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("1-TAP CONVERSIONS", fontFamily = NothingTypographyEngine.ndot55Family, fontSize = 10.sp, color = palette.accent)
                        val presets = listOf(
                            "100 USD to DZD",
                            "100 EUR to DZD",
                            "10 km to miles",
                            "30 C to F",
                            "75 kg to lb",
                            "100 USD to EUR"
                        )
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            presets.forEach { p ->
                                NothingPillChip(label = p, selected = query == p, onClick = { onSetQuery(p) })
                            }
                        }
                    }
                }
            }

            QuickSearchTool.STOPWATCH -> {
                var running by remember { mutableStateOf(false) }
                var elapsedDecisec by remember { mutableLongStateOf(0L) }
                LaunchedEffect(running) {
                    while (running) {
                        delay(100L)
                        elapsedDecisec++
                    }
                }
                val mins = (elapsedDecisec / 600L)
                val secs = (elapsedDecisec / 10L) % 60L
                val tenths = elapsedDecisec % 10L

                NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = String.format(Locale.US, "%02d:%02d.%d", mins, secs, tenths),
                                fontFamily = NothingTypographyEngine.ndot77Family,
                                fontSize = 24.sp,
                                color = palette.textPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                NothingPillChip(
                                    label = if (running) "Pause" else "Start",
                                    selected = running,
                                    onClick = { running = !running }
                                )
                                NothingPillChip(
                                    label = "Reset",
                                    selected = false,
                                    onClick = {
                                        running = false
                                        elapsedDecisec = 0L
                                    }
                                )
                            }
                        }
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1 to "1m Timer", 5 to "5m Timer", 10 to "10m Timer", 25 to "25m Focus").forEach { (m, lbl) ->
                                NothingPillChip(label = lbl, selected = false, onClick = { onFireTimerSeconds(m * 60) })
                            }
                        }
                    }
                }
            }

            QuickSearchTool.PASS_GEN -> {
                val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%&*"
                var generated by remember {
                    mutableStateOf((1..16).map { chars[Random.nextInt(chars.length)] }.joinToString(""))
                }
                NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("GENERATED KEY", fontFamily = NothingTypographyEngine.ndot55Family, fontSize = 10.sp, color = palette.accent)
                            Text(generated, fontFamily = NothingTypographyEngine.ntype82Family, fontSize = 14.sp, color = palette.textPrimary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NothingPillChip(
                                label = "16-Char",
                                selected = false,
                                onClick = {
                                    generated = (1..16).map { chars[Random.nextInt(chars.length)] }.joinToString("")
                                }
                            )
                            NothingPillChip(
                                label = "6-PIN",
                                selected = false,
                                onClick = {
                                    generated = (1..6).map { Random.nextInt(0, 10) }.joinToString("")
                                }
                            )
                            NothingPillChip(
                                label = "Copy",
                                selected = true,
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("DotOS Key", generated))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }

            QuickSearchTool.CLIPBOARD -> {
                val clipText = remember(activeTool) {
                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cb?.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim().orEmpty()
                }
                NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = clipText.ifBlank { "Clipboard is currently empty" },
                            fontSize = 12.sp,
                            color = palette.textPrimary,
                            maxLines = 2,
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        if (clipText.isNotBlank()) {
                            NothingPillChip(label = "Use in Search", selected = true, onClick = { onSetQuery(clipText) })
                        }
                    }
                }
            }

            QuickSearchTool.RANDOM -> {
                var resultText by remember { mutableStateOf("Tap Coin or Dice") }
                NothingSurfaceCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = resultText,
                            fontFamily = NothingTypographyEngine.ndot55Family,
                            fontSize = 15.sp,
                            color = palette.accent
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            NothingPillChip(
                                label = "Flip Coin",
                                selected = false,
                                onClick = { resultText = if (Random.nextBoolean()) "COIN: HEADS" else "COIN: TAILS" }
                            )
                            NothingPillChip(
                                label = "Roll D6",
                                selected = false,
                                onClick = { resultText = "DICE: ${Random.nextInt(1, 7)}" }
                            )
                            NothingPillChip(
                                label = "1–100",
                                selected = false,
                                onClick = { resultText = "PICK: ${Random.nextInt(1, 101)}" }
                            )
                        }
                    }
                }
            }

            null -> Unit
        }
    }
}
