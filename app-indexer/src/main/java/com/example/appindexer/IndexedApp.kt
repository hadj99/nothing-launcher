package com.example.appindexer

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector

@Immutable
data class AppShortcutInfo(
    val id: String,
    val packageName: String,
    val shortLabel: String,
    val longLabel: String
)

@Immutable
data class IndexedApp(
    val packageName: String,
    val activityName: String,
    val label: String,
    val category: String,
    val launchCount: Int = 0,
    val lastUsedTimestamp: Long = 0L,
    val dailyMinutesUsed: Int = 0,
    val isSystemApp: Boolean = false,
    val shortcuts: List<AppShortcutInfo> = emptyList()
)

@Immutable
data class InstalledIconPack(
    val packageName: String,
    val label: String
)

@Immutable
data class ScreenTimeVelocity(
    val totalMinutesToday: Int = 0,
    val recentHourMinutes: Int = 0,
    val velocityScore: Float = 0.25f, // 0f (calm) .. 1f (intense)
    val usagePermissionGranted: Boolean = false
)

object NothingGlyphCatalog {
    val glyphs: Map<String, ImageVector> = linkedMapOf(
        "bolt" to Icons.Rounded.Bolt,
        "phone" to Icons.Rounded.Phone,
        "chat" to Icons.Rounded.ChatBubble,
        "camera" to Icons.Rounded.CameraAlt,
        "music" to Icons.Rounded.MusicNote,
        "audio" to Icons.Rounded.Headphones,
        "mail" to Icons.Rounded.Mail,
        "map" to Icons.Rounded.Map,
        "explore" to Icons.Rounded.Explore,
        "terminal" to Icons.Rounded.Terminal,
        "code" to Icons.Rounded.Code,
        "folder" to Icons.Rounded.Folder,
        "video" to Icons.Rounded.Videocam,
        "shield" to Icons.Rounded.Shield,
        "star" to Icons.Rounded.Star,
        "settings" to Icons.Rounded.Settings
    )

    fun resolve(glyphId: String?): ImageVector? = glyphId?.let { glyphs[it] }
}
