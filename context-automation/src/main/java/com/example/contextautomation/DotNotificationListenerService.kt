package com.example.contextautomation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class ActiveMediaState(
    val title: String = "NO MEDIA ACTIVE",
    val artist: String = "Tap play or open music app",
    val packageName: String? = null,
    val isPlaying: Boolean = false,
    val hasSession: Boolean = false
)

object LiveNotificationBridge {
    private val _badgeCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val badgeCounts: StateFlow<Map<String, Int>> = _badgeCounts.asStateFlow()

    private val _mediaState = MutableStateFlow(ActiveMediaState())
    val mediaState: StateFlow<ActiveMediaState> = _mediaState.asStateFlow()

    private var activeController: MediaController? = null

    fun isNotificationAccessEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabled.contains(context.packageName)
    }

    fun openNotificationAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    internal fun updateNotifications(notifications: Array<StatusBarNotification>?) {
        val counts = HashMap<String, Int>()
        notifications?.forEach { sbn ->
            if (!sbn.isOngoing) {
                counts[sbn.packageName] = (counts[sbn.packageName] ?: 0) + 1
            }
        }
        _badgeCounts.value = counts
    }

    internal fun updateMediaFromManager(context: Context, serviceComponent: ComponentName) {
        runCatching {
            val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager ?: return
            val controllers = msm.getActiveSessions(serviceComponent)
            val primary = controllers.firstOrNull {
                it.playbackState?.state == PlaybackState.STATE_PLAYING
            } ?: controllers.firstOrNull()
            activeController = primary
            if (primary == null) {
                _mediaState.value = ActiveMediaState()
            } else {
                val meta = primary.metadata
                val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
                    ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                    ?: "ACTIVE AUDIO"
                val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: primary.packageName
                val playing = primary.playbackState?.state == PlaybackState.STATE_PLAYING
                _mediaState.value = ActiveMediaState(
                    title = title,
                    artist = artist,
                    packageName = primary.packageName,
                    isPlaying = playing,
                    hasSession = true
                )
            }
        }
    }

    fun togglePlayPause() {
        val ctrl = activeController ?: return
        val playing = ctrl.playbackState?.state == PlaybackState.STATE_PLAYING
        if (playing) ctrl.transportControls.pause() else ctrl.transportControls.play()
        _mediaState.value = _mediaState.value.copy(isPlaying = !playing)
    }

    fun skipNext() {
        activeController?.transportControls?.skipToNext()
    }

    fun skipPrevious() {
        activeController?.transportControls?.skipToPrevious()
    }
}

class DotNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        LiveNotificationBridge.updateNotifications(runCatching { activeNotifications }.getOrNull())
        LiveNotificationBridge.updateMediaFromManager(
            this,
            ComponentName(this, DotNotificationListenerService::class.java)
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        LiveNotificationBridge.updateNotifications(runCatching { activeNotifications }.getOrNull())
        LiveNotificationBridge.updateMediaFromManager(
            this,
            ComponentName(this, DotNotificationListenerService::class.java)
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        LiveNotificationBridge.updateNotifications(runCatching { activeNotifications }.getOrNull())
    }
}
