package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.appindexer.IndexedApp
import com.example.appindexer.NothingGlyphCatalog
import com.example.coreui.DotMatrixDivider
import com.example.coreui.MaskedAppIcon
import com.example.coreui.NothingPillChip
import com.example.coreui.NothingSliderRow
import com.example.coreui.NothingSurfaceCard
import com.example.coreui.NothingToggleRow
import com.example.settingsstore.model.AppCustomization
import com.example.settingsstore.model.LauncherSettings
import com.example.themeengine.LocalNothingPalette
import com.example.themeengine.NothingTypographyEngine
import kotlin.math.roundToInt

@Composable
fun LongPressAppMenuDialog(
    app: IndexedApp,
    iconBitmap: androidx.compose.ui.graphics.ImageBitmap?,
    settings: LauncherSettings,
    onDismiss: () -> Unit,
    onSaveCustomization: ((AppCustomization) -> AppCustomization) -> Unit,
    onToggleHome: () -> Unit,
    onToggleDock: () -> Unit,
    onAddToFolder: (String) -> Unit,
    onOpenAppInfo: () -> Unit,
    onLaunchSplitScreen: () -> Unit,
    onUninstall: () -> Unit
) {
    val palette = LocalNothingPalette.current
    val custom = settings.appCustomizations[app.packageName] ?: AppCustomization(app.packageName)
    var showCustomizeSheet by remember { mutableStateOf(false) }
    var showFolderInput by remember { mutableStateOf(false) }
    var folderNameInput by remember { mutableStateOf("Work") }

    val isOnHome = settings.homeAppPages.any { it.contains(app.packageName) }
    val isInDock = settings.dockPages.any { it.contains(app.packageName) }

    Dialog(onDismissRequest = onDismiss) {
        NothingSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 24.dp,
            borderWidth = settings.theme.contextMenuBorderDp.dp,
            backgroundColor = palette.surface.copy(alpha = settings.theme.contextMenuOpacity.coerceIn(0.4f, 1f))
        ) {
            if (!showCustomizeSheet && !showFolderInput) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MaskedAppIcon(
                            iconBitmap = iconBitmap,
                            maskConfig = settings.iconMask,
                            baseSize = 44.dp,
                            customImageUri = custom.customImageUri,
                            customGlyphVector = NothingGlyphCatalog.resolve(custom.customGlyphId),
                            monochromeOverride = custom.monochromeOverride,
                            scaleOverride = custom.iconScaleOverride
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = custom.customLabel ?: app.label,
                                fontFamily = NothingTypographyEngine.ndot55Family,
                                fontSize = 16.sp,
                                color = palette.textPrimary
                            )
                            Text(
                                text = "${app.category}  •  ${app.launchCount} launches",
                                fontSize = 11.sp,
                                color = palette.textSecondary
                            )
                        }
                    }

                    DotMatrixDivider()

                    MenuActionRow(
                        icon = Icons.Rounded.Edit,
                        label = "Customize Icon, Label & Friction Timer",
                        accent = true,
                        onClick = { showCustomizeSheet = true }
                    )
                    MenuActionRow(
                        icon = Icons.Rounded.AddToHomeScreen,
                        label = if (isOnHome) "Remove from Home Page" else "Pin to Home Page",
                        onClick = {
                            onToggleHome()
                            onDismiss()
                        }
                    )
                    MenuActionRow(
                        icon = Icons.Rounded.Dock,
                        label = if (isInDock) "Remove from Dock" else "Pin to Bottom Dock",
                        onClick = {
                            onToggleDock()
                            onDismiss()
                        }
                    )
                    MenuActionRow(
                        icon = Icons.Rounded.CreateNewFolder,
                        label = "Add to Folder",
                        onClick = { showFolderInput = true }
                    )
                    MenuActionRow(
                        icon = Icons.Rounded.PushPin,
                        label = if (custom.pinnedToTop) "Unpin from Drawer Top" else "Pin to Drawer Top",
                        onClick = {
                            onSaveCustomization { it.copy(pinnedToTop = !it.pinnedToTop) }
                            onDismiss()
                        }
                    )
                    MenuActionRow(
                        icon = Icons.Rounded.Splitscreen,
                        label = "Launch in Split / Adjacent Mode",
                        onClick = {
                            onLaunchSplitScreen()
                            onDismiss()
                        }
                    )
                    MenuActionRow(
                        icon = Icons.Rounded.VisibilityOff,
                        label = if (custom.hidden) "Unhide from Drawer" else "Hide App",
                        onClick = {
                            onSaveCustomization { it.copy(hidden = !it.hidden) }
                            onDismiss()
                        }
                    )
                    MenuActionRow(
                        icon = Icons.Rounded.Lock,
                        label = if (custom.lockedInVault) "Remove from Biometric Vault" else "Lock in Biometric Vault",
                        onClick = {
                            onSaveCustomization { it.copy(lockedInVault = !it.lockedInVault) }
                            onDismiss()
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = {
                            onOpenAppInfo()
                            onDismiss()
                        }) {
                            Icon(Icons.Rounded.Info, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("App Info")
                        }
                        if (!app.isSystemApp) {
                            TextButton(onClick = {
                                onUninstall()
                                onDismiss()
                            }) {
                                Icon(Icons.Rounded.DeleteOutline, null, tint = palette.accent, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Uninstall", color = palette.accent)
                            }
                        }
                    }
                }
            } else if (showFolderInput) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ADD TO FOLDER",
                        fontFamily = NothingTypographyEngine.ndot55Family,
                        fontSize = 14.sp,
                        color = palette.accent
                    )
                    if (settings.folders.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            settings.folders.forEach { folder ->
                                NothingPillChip(
                                    label = folder.name,
                                    selected = folderNameInput == folder.name,
                                    onClick = { folderNameInput = folder.name }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = folderNameInput,
                        onValueChange = { folderNameInput = it },
                        label = { Text("Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showFolderInput = false }) { Text("Back") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (folderNameInput.isNotBlank()) {
                                    onAddToFolder(folderNameInput.trim())
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                        ) {
                            Text("Add", color = Color.White)
                        }
                    }
                }
            } else {
                // Full Customize App & Mindful Friction Timer Sheet
                var labelText by remember { mutableStateOf(custom.customLabel ?: app.label) }
                var selectedGlyph by remember { mutableStateOf(custom.customGlyphId) }
                var customImg by remember { mutableStateOf(custom.customImageUri) }
                var monoOverride by remember { mutableStateOf(custom.monochromeOverride ?: settings.iconMask.monochromeFilter) }
                var scaleOverride by remember { mutableFloatStateOf(custom.iconScaleOverride ?: settings.iconMask.iconScale) }
                var frictionSec by remember { mutableIntStateOf(custom.frictionSeconds) }
                var dailyLimit by remember { mutableIntStateOf(custom.dailyLimitMinutes) }

                val photoPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickVisualMedia()
                ) { uri: Uri? ->
                    if (uri != null) {
                        customImg = uri.toString()
                        selectedGlyph = null
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "CUSTOMIZE APP",
                        fontFamily = NothingTypographyEngine.ndot55Family,
                        fontSize = 14.sp,
                        color = palette.accent
                    )
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = { labelText = it },
                        label = { Text("App Display Label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Built-in Monochrome Glyph", fontSize = 12.sp, color = palette.textSecondary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NothingPillChip(
                            label = "Default",
                            selected = selectedGlyph == null && customImg == null,
                            onClick = {
                                selectedGlyph = null
                                customImg = null
                            }
                        )
                        NothingGlyphCatalog.glyphs.forEach { (id, vec) ->
                            val isSelected = selectedGlyph == id
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) palette.accent else palette.background)
                                    .border(1.dp, palette.borderSubtle, CircleShape)
                                    .clickable {
                                        selectedGlyph = id
                                        customImg = null
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(vec, id, tint = if (isSelected) Color.White else palette.textPrimary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    NothingPillChip(
                        label = "Pick Gallery Icon (Photo Picker)",
                        selected = customImg != null,
                        onClick = {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )

                    NothingToggleRow(
                        label = "Monochrome Icon Filter",
                        checked = monoOverride,
                        onCheckedChange = { monoOverride = it }
                    )

                    NothingSliderRow(
                        label = "Individual Icon Scale",
                        value = scaleOverride,
                        valueRange = 0.6f..1.4f,
                        onValueChange = { scaleOverride = it },
                        valueFormatter = { "${(it * 100).roundToInt()}%" }
                    )

                    DotMatrixDivider()

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = palette.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("MINDFUL FRICTION TIMER", fontFamily = NothingTypographyEngine.ndot55Family, fontSize = 12.sp, color = palette.accent)
                    }

                    NothingSliderRow(
                        label = "Breathing Countdown Before Open",
                        value = frictionSec.toFloat(),
                        valueRange = 0f..10f,
                        onValueChange = {
                            val v = it.roundToInt()
                            frictionSec = if (v in 1..2) 3 else v
                        },
                        valueFormatter = { if (it < 1f) "Off" else "${it.roundToInt()}s" }
                    )

                    NothingSliderRow(
                        label = "Daily Usage Alert Limit",
                        value = dailyLimit.toFloat(),
                        valueRange = 0f..180f,
                        onValueChange = { dailyLimit = (it / 5f).roundToInt() * 5 },
                        valueFormatter = { if (it < 5f) "None" else "${it.roundToInt()} min" }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showCustomizeSheet = false }) { Text("Back") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                onSaveCustomization {
                                    it.copy(
                                        customLabel = labelText.trim().takeIf { lbl -> lbl.isNotEmpty() && lbl != app.label },
                                        customGlyphId = selectedGlyph,
                                        customImageUri = customImg,
                                        monochromeOverride = monoOverride,
                                        iconScaleOverride = scaleOverride,
                                        frictionSeconds = frictionSec,
                                        dailyLimitMinutes = dailyLimit
                                    )
                                }
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                        ) {
                            Text("Save", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuActionRow(
    icon: ImageVector,
    label: String,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    val palette = LocalNothingPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) palette.accent else palette.textPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 13.5.sp,
            fontWeight = if (accent) FontWeight.SemiBold else FontWeight.Normal,
            color = if (accent) palette.accent else palette.textPrimary
        )
    }
}
