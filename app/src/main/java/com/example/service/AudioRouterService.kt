package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.R
import com.example.data.SavedTrackEntity
import com.example.data.SplitAudioDatabase
import com.example.data.SplitAudioRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PodcastEngineState(
    val currentTrack: SavedTrackEntity? = null,
    val queue: List<SavedTrackEntity> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val playerVolume: Float = 0.85f,
    val isMuted: Boolean = false,
    val sleepTimerRemainingSeconds: Int? = null,
    val isForegroundServiceActive: Boolean = false,
    val audioFocusBypassActive: Boolean = true
)

/**
 * Shared singleton controller managing the dual-stream audio engine so both the
 * MediaSessionService (ForegroundService for OriginOS 6) and the Compose ViewModel
 * interact with the exact same ExoPlayer & AudioRouteController instance.
 */
@UnstableApi
class SplitAudioEngineManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repository = SplitAudioRepository(
        appContext,
        SplitAudioDatabase.getInstance(appContext).splitAudioDao()
    )

    private var exoPlayer: ExoPlayer? = null
    private var preMuteBluetoothVolume: Float = 0.85f
    private var progressPollingJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var lastPersistedSecond: Long = -1L

    val routeController = AudioRouteController(appContext) { preferredBluetoothDevice ->
        applyBluetoothPreferredDevice(preferredBluetoothDevice)
    }

    private val _engineState = MutableStateFlow(PodcastEngineState())
    val engineState: StateFlow<PodcastEngineState> = _engineState.asStateFlow()

    val routingState: StateFlow<AudioRoutingSnapshot> = routeController.snapshot

    fun preloadQueueMetadataOnly(tracks: List<SavedTrackEntity>, startIndex: Int = 0) {
        if (tracks.isEmpty() || _engineState.value.currentTrack != null) return
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val targetTrack = tracks[safeIndex]
        _engineState.value = _engineState.value.copy(
            queue = tracks,
            currentTrack = targetTrack,
            currentIndex = safeIndex,
            currentPositionMs = targetTrack.lastPositionMs.coerceAtLeast(0L),
            durationMs = targetTrack.durationMs
        )
    }

    fun getOrCreatePlayer(): ExoPlayer {
        return ensurePlayerInitialized()
    }

    private fun ensurePlayerInitialized(): ExoPlayer {
        exoPlayer?.let { return it }

        // CRITICAL: handleAudioFocus = false ensures YouTube playback NEVER pauses or ducks
        // our Bluetooth Podcast stream on the vivo Y05!
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val player = ExoPlayer.Builder(appContext)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ false)
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.volume = _engineState.value.playerVolume

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val dur = player.duration.takeIf { it > 0 } ?: _engineState.value.durationMs
                _engineState.value = _engineState.value.copy(
                    isPlaying = isPlaying,
                    currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = dur
                )
                if (isPlaying) {
                    // Re-verify Bluetooth device binding every time playback starts
                    if (routeController.snapshot.value.isPodcastPinnedToBluetooth) {
                        applyBluetoothPreferredDevice(routeController.findActiveBluetoothDeviceInfo())
                    }
                    ensureForegroundServiceRunning()
                    startProgressTicker()
                } else {
                    persistCurrentProgressNow()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val idx = player.currentMediaItemIndex
                val queue = _engineState.value.queue
                val track = queue.getOrNull(idx)
                val dur = player.duration.takeIf { it > 0 } ?: track?.durationMs ?: 0L
                _engineState.value = _engineState.value.copy(
                    currentTrack = track,
                    currentIndex = idx,
                    currentPositionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = dur
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    val dur = player.duration.takeIf { it > 0 } ?: _engineState.value.durationMs
                    _engineState.value = _engineState.value.copy(
                        durationMs = dur,
                        currentPositionMs = player.currentPosition.coerceAtLeast(0L)
                    )
                }
            }
        })

        exoPlayer = player
        if (routeController.snapshot.value.isPodcastPinnedToBluetooth) {
            applyBluetoothPreferredDevice(routeController.findActiveBluetoothDeviceInfo())
        }
        return player
    }

    private fun applyBluetoothPreferredDevice(deviceInfo: AudioDeviceInfo?) {
        val player = exoPlayer ?: return
        runCatching {
            player.setPreferredAudioDevice(deviceInfo)
        }
    }

    fun loadQueueAndPlay(
        tracks: List<SavedTrackEntity>,
        startIndex: Int = 0,
        resumeSavedPosition: Boolean = true,
        autoPlay: Boolean = true
    ) {
        if (tracks.isEmpty()) return
        val player = ensurePlayerInitialized()
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val targetTrack = tracks[safeIndex]

        val mediaItems = tracks.map { entity ->
            MediaItem.Builder()
                .setMediaId(entity.uriString)
                .setUri(entity.uriString)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(entity.title)
                        .setArtist(entity.artistOrFolder)
                        .build()
                )
                .build()
        }

        val startPositionMs = if (resumeSavedPosition && targetTrack.lastPositionMs > 1500L &&
            (targetTrack.durationMs == 0L || targetTrack.lastPositionMs < targetTrack.durationMs - 2000L)
        ) {
            targetTrack.lastPositionMs
        } else {
            0L
        }

        _engineState.value = _engineState.value.copy(
            queue = tracks,
            currentTrack = targetTrack,
            currentIndex = safeIndex,
            currentPositionMs = startPositionMs,
            durationMs = targetTrack.durationMs
        )

        if (routeController.snapshot.value.isPodcastPinnedToBluetooth) {
            applyBluetoothPreferredDevice(routeController.findActiveBluetoothDeviceInfo())
        }

        player.setMediaItems(mediaItems, safeIndex, startPositionMs)
        player.prepare()
        if (autoPlay) {
            ensureForegroundServiceRunning()
            player.play()
        }
    }

    fun togglePlayPause() {
        val player = ensurePlayerInitialized()
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.mediaItemCount == 0 && _engineState.value.queue.isNotEmpty()) {
                loadQueueAndPlay(_engineState.value.queue, _engineState.value.currentIndex.coerceAtLeast(0))
            } else {
                if (routeController.snapshot.value.isPodcastPinnedToBluetooth) {
                    applyBluetoothPreferredDevice(routeController.findActiveBluetoothDeviceInfo())
                }
                ensureForegroundServiceRunning()
                player.play()
            }
        }
    }

    fun seekByDeltaMs(deltaMs: Long) {
        val player = ensurePlayerInitialized()
        val duration = player.duration.takeIf { it > 0 } ?: _engineState.value.durationMs
        val target = if (duration > 0) {
            (player.currentPosition + deltaMs).coerceIn(0L, duration)
        } else {
            (player.currentPosition + deltaMs).coerceAtLeast(0L)
        }
        player.seekTo(target)
        _engineState.value = _engineState.value.copy(currentPositionMs = target)
        persistCurrentProgressNow()
    }

    fun seekToMs(positionMs: Long) {
        val player = ensurePlayerInitialized()
        val target = positionMs.coerceAtLeast(0L)
        player.seekTo(target)
        _engineState.value = _engineState.value.copy(currentPositionMs = target)
        persistCurrentProgressNow()
    }

    fun skipToNext() {
        val player = ensurePlayerInitialized()
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        }
    }

    fun skipToPrevious() {
        val player = ensurePlayerInitialized()
        if (player.currentPosition > 4000L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.5f)
        val player = ensurePlayerInitialized()
        player.playbackParameters = PlaybackParameters(clamped)
        _engineState.value = _engineState.value.copy(playbackSpeed = clamped)
    }

    fun setBluetoothPlayerVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        val player = ensurePlayerInitialized()
        player.volume = clamped
        val isMuted = clamped <= 0.005f
        if (!isMuted) {
            preMuteBluetoothVolume = clamped
        }
        _engineState.value = _engineState.value.copy(
            playerVolume = clamped,
            isMuted = isMuted
        )
        routeController.updateBluetoothVolumeState(clamped, isMuted)
    }

    fun toggleBluetoothMute() {
        val current = _engineState.value
        if (!current.isMuted) {
            preMuteBluetoothVolume = current.playerVolume.takeIf { it > 0.05f } ?: 0.85f
            setBluetoothPlayerVolume(0f)
        } else {
            setBluetoothPlayerVolume(preMuteBluetoothVolume)
        }
    }

    fun startSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        if (minutes == null || minutes <= 0) {
            _engineState.value = _engineState.value.copy(sleepTimerRemainingSeconds = null)
            return
        }
        var remainingSeconds = minutes * 60
        _engineState.value = _engineState.value.copy(sleepTimerRemainingSeconds = remainingSeconds)

        sleepTimerJob = scope.launch {
            while (isActive && remainingSeconds > 0) {
                delay(1000L)
                remainingSeconds -= 1
                _engineState.value = _engineState.value.copy(sleepTimerRemainingSeconds = remainingSeconds)
            }
            if (remainingSeconds <= 0) {
                exoPlayer?.pause()
                persistCurrentProgressNow()
                _engineState.value = _engineState.value.copy(sleepTimerRemainingSeconds = null)
            }
        }
    }

    fun markServiceRunningState(isRunning: Boolean) {
        _engineState.value = _engineState.value.copy(isForegroundServiceActive = isRunning)
    }

    private fun startProgressTicker() {
        progressPollingJob?.cancel()
        progressPollingJob = scope.launch {
            while (isActive && exoPlayer?.isPlaying == true) {
                val player = exoPlayer ?: break
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.takeIf { it > 0 } ?: _engineState.value.durationMs
                _engineState.value = _engineState.value.copy(
                    currentPositionMs = pos,
                    durationMs = dur
                )
                val currentSec = pos / 1000L
                if (currentSec % 3L == 0L && currentSec != lastPersistedSecond) {
                    lastPersistedSecond = currentSec
                    persistCurrentProgressNow()
                }
                delay(500L)
            }
        }
    }

    private fun persistCurrentProgressNow() {
        val track = _engineState.value.currentTrack ?: return
        val pos = _engineState.value.currentPositionMs
        val dur = _engineState.value.durationMs
        scope.launch(Dispatchers.IO) {
            repository.saveProgress(track.uriString, pos, dur)
        }
    }

    fun ensureForegroundServiceRunning() {
        runCatching {
            val intent = Intent(appContext, AudioRouterService::class.java).apply {
                action = AudioRouterService.ACTION_START_FOREGROUND
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: SplitAudioEngineManager? = null

        fun getInstance(context: Context): SplitAudioEngineManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SplitAudioEngineManager(context)
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * Lightweight ForegroundService with `foregroundServiceType="mediaPlayback"`
 * engineered for vivo Y05 (4GB physical RAM + OriginOS 6 background task management).
 */
@UnstableApi
class AudioRouterService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val manager = SplitAudioEngineManager.getInstance(this)
        val player = manager.getOrCreatePlayer()

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()

        acquireLightweightWakeLock()
        promoteToForeground()
        manager.markServiceRunningState(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START_FOREGROUND -> promoteToForeground()
            ACTION_TOGGLE_PLAY -> SplitAudioEngineManager.getInstance(this).togglePlayPause()
            ACTION_STOP_SERVICE -> {
                SplitAudioEngineManager.getInstance(this).markServiceRunningState(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        // START_STICKY tells OriginOS 6 to recreate the service if memory pressure ever reclaims it
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player != null && player.isPlaying) {
            // Keep playing in background on OriginOS 6 when user swipes away recents
            promoteToForeground()
        } else {
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onDestroy() {
        SplitAudioEngineManager.getInstance(this).markServiceRunningState(false)
        releaseWakeLock()
        mediaSession?.run {
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun promoteToForeground() {
        runCatching {
            val notification = buildForegroundNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        val manager = SplitAudioEngineManager.getInstance(this)
        val trackTitle = manager.engineState.value.currentTrack?.title
            ?: getString(R.string.service_running_title)
        val btName = manager.routingState.value.activeBluetoothDevice?.productName
            ?: "بلوتوث معزول"

        val openAppIntent = PendingIntent.getActivity(
            this,
            100,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val togglePlayIntent = PendingIntent.getService(
            this,
            101,
            Intent(this, AudioRouterService::class.java).apply { action = ACTION_TOGGLE_PLAY },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_headset)
            .setContentTitle(trackTitle)
            .setContentText("بلوتوث: $btName • يوتيوب: سماعة الهاتف (OriginOS 6 محمي)")
            .setSubText("vivo Y05 Split Audio")
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                android.R.drawable.ic_media_play,
                "تشغيل / إيقاف",
                togglePlayIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun acquireLightweightWakeLock() {
        runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "SplitAudioRouter:VivoY05PlaybackLock"
            ).apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L) // Max 4 hours safety timeout
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            wakeLock = null
        }
    }

    companion object {
        const val CHANNEL_ID = "vivo_y05_split_audio_channel"
        const val NOTIFICATION_ID = 3605
        const val ACTION_START_FOREGROUND = "com.example.action.START_FOREGROUND"
        const val ACTION_TOGGLE_PLAY = "com.example.action.TOGGLE_PLAY"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
    }
}
