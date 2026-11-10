package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.example.data.SavedTrackEntity
import com.example.data.SplitAudioDatabase
import com.example.data.SplitAudioRepository
import com.example.data.TrackBookmarkEntity
import com.example.service.AudioRoutingSnapshot
import com.example.service.PodcastEngineState
import com.example.service.SplitAudioEngineManager
import com.example.service.YoutubeSpeakerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SplitLayoutMode {
    UNIFIED_SPLIT,
    PODCAST_EXPANDED,
    YOUTUBE_EXPANDED
}

data class SplitAudioUiState(
    val engine: PodcastEngineState = PodcastEngineState(),
    val routing: AudioRoutingSnapshot = AudioRoutingSnapshot(),
    val allTracks: List<SavedTrackEntity> = emptyList(),
    val filteredTracks: List<SavedTrackEntity> = emptyList(),
    val playlists: List<String> = listOf("الكل", "المفضلة"),
    val selectedPlaylist: String = "الكل",
    val bookmarksForCurrentTrack: List<TrackBookmarkEntity> = emptyList(),
    val allBookmarks: List<TrackBookmarkEntity> = emptyList(),
    val layoutMode: SplitLayoutMode = SplitLayoutMode.UNIFIED_SPLIT,
    val isBatteryOptimizationIgnored: Boolean = false,
    val showOriginOsGuideDialog: Boolean = false,
    val showBookmarkDialog: Boolean = false,
    val showSleepTimerDialog: Boolean = false,
    val statusBannerMessage: String? = null,
    val isScanningStorage: Boolean = false,
    val youtubeCurrentUrl: String = "https://m.youtube.com",
    val youtubeWebGainPercent: Int = 100
)

@UnstableApi
class SplitAudioViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val dao = SplitAudioDatabase.getInstance(appContext).splitAudioDao()
    private val repository = SplitAudioRepository(appContext, dao)
    private val engineManager = SplitAudioEngineManager.getInstance(appContext)

    private val _selectedPlaylist = MutableStateFlow("الكل")
    private val _layoutMode = MutableStateFlow(SplitLayoutMode.UNIFIED_SPLIT)
    private val _isBatteryIgnored = MutableStateFlow(checkBatteryOptimizationIgnored())
    private val _showOriginOsDialog = MutableStateFlow(false)
    private val _showBookmarkDialog = MutableStateFlow(false)
    private val _showSleepTimerDialog = MutableStateFlow(false)
    private val _statusBanner = MutableStateFlow<String?>(null)
    private val _isScanning = MutableStateFlow(false)
    private val _youtubeUrl = MutableStateFlow("https://m.youtube.com")
    private val _youtubeWebGainPercent = MutableStateFlow(100)

    private data class InternalDialogFlags(
        val selectedPlaylist: String,
        val layoutMode: SplitLayoutMode,
        val isBatteryIgnored: Boolean,
        val showOriginOsDialog: Boolean,
        val showBookmarkDialog: Boolean,
        val showSleepTimerDialog: Boolean,
        val statusBanner: String?,
        val isScanning: Boolean,
        val youtubeUrl: String,
        val youtubeWebGainPercent: Int
    )

    private val dialogFlagsFlow = combine(
        combine(_selectedPlaylist, _layoutMode, _isBatteryIgnored) { a, b, c -> Triple(a, b, c) },
        combine(_showOriginOsDialog, _showBookmarkDialog, _showSleepTimerDialog) { d, e, f -> Triple(d, e, f) },
        combine(_statusBanner, _isScanning, _youtubeUrl, _youtubeWebGainPercent) { g, h, i, j ->
            listOf(g, h, i, j)
        }
    ) { t1, t2, list ->
        InternalDialogFlags(
            selectedPlaylist = t1.first,
            layoutMode = t1.second,
            isBatteryIgnored = t1.third,
            showOriginOsDialog = t2.first,
            showBookmarkDialog = t2.second,
            showSleepTimerDialog = t2.third,
            statusBanner = list[0] as String?,
            isScanning = list[1] as Boolean,
            youtubeUrl = list[2] as String,
            youtubeWebGainPercent = list[3] as Int
        )
    }

    val uiState: StateFlow<SplitAudioUiState> = combine(
        engineManager.engineState,
        engineManager.routingState,
        repository.allTracksFlow,
        repository.allBookmarksFlow,
        dialogFlagsFlow
    ) { engine, routing, tracks, bookmarks, flags ->
        val dynamicPlaylists = buildList {
            add("الكل")
            add("المفضلة")
            tracks.map { it.playlistName }.distinct().filter { it.isNotBlank() }.forEach { add(it) }
        }

        val filtered = when (flags.selectedPlaylist) {
            "الكل" -> tracks
            "المفضلة" -> tracks.filter { it.isFavorite }
            else -> tracks.filter { it.playlistName == flags.selectedPlaylist }
        }

        val currentUri = engine.currentTrack?.uriString
        val currentBookmarks = if (currentUri != null) {
            bookmarks.filter { it.trackUriString == currentUri }
        } else {
            emptyList()
        }

        SplitAudioUiState(
            engine = engine,
            routing = routing,
            allTracks = tracks,
            filteredTracks = filtered,
            playlists = dynamicPlaylists,
            selectedPlaylist = flags.selectedPlaylist,
            bookmarksForCurrentTrack = currentBookmarks,
            allBookmarks = bookmarks,
            layoutMode = flags.layoutMode,
            isBatteryOptimizationIgnored = flags.isBatteryIgnored,
            showOriginOsGuideDialog = flags.showOriginOsDialog,
            showBookmarkDialog = flags.showBookmarkDialog,
            showSleepTimerDialog = flags.showSleepTimerDialog,
            statusBannerMessage = flags.statusBanner,
            isScanningStorage = flags.isScanning,
            youtubeCurrentUrl = flags.youtubeUrl,
            youtubeWebGainPercent = flags.youtubeWebGainPercent
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SplitAudioUiState(isBatteryOptimizationIgnored = checkBatteryOptimizationIgnored())
    )

    init {
        viewModelScope.launch {
            val demoTracks = repository.ensureBuiltInDemoPodcastTracks()
            if (engineManager.engineState.value.currentTrack == null && demoTracks.isNotEmpty()) {
                engineManager.preloadQueueMetadataOnly(
                    tracks = demoTracks,
                    startIndex = 0
                )
            }
        }
    }

    fun refreshBatteryOptimizationState() {
        _isBatteryIgnored.value = checkBatteryOptimizationIgnored()
    }

    fun refreshBluetoothDevices() {
        engineManager.routeController.refreshOutputDevices(
            triggerReasonAr = "تم تحديث قائمة أجهزة الصوت المتصلة يدوياً"
        )
    }

    fun scanDeviceStorageForAudio() {
        viewModelScope.launch {
            _isScanning.value = true
            val count = repository.scanDeviceLocalAudio()
            _isScanning.value = false
            _statusBanner.value = if (count > 0) {
                "تم اكتشاف وإضافة $count ملف صوتي من ذاكرة vivo Y05"
            } else {
                "لم يتم العثور على ملفات صوتية جديدة في الذاكرة العامة؛ يمكنك اختيار ملف أو مجلد يدوياً"
            }
        }
    }

    fun onAudioFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = repository.importPickedUris(uris, "ملفات مختارة")
            if (imported.isNotEmpty()) {
                _selectedPlaylist.value = "الكل"
                engineManager.loadQueueAndPlay(
                    tracks = imported,
                    startIndex = 0,
                    resumeSavedPosition = true,
                    autoPlay = true
                )
                _statusBanner.value = "تم استيراد وتشغيل ${imported.size} ملف صوتي على مسار البلوتوث"
            }
        }
    }

    fun onFolderPicked(treeUri: Uri?) {
        if (treeUri == null) return
        viewModelScope.launch {
            _isScanning.value = true
            val count = repository.importFolderTree(treeUri)
            _isScanning.value = false
            _statusBanner.value = if (count > 0) {
                "تم استيراد $count ملف صوتي من المجلد المختار"
            } else {
                "لم يتم العثور على ملفات MP3/M4A/WAV/OGG داخل هذا المجلد"
            }
        }
    }

    fun playTrackFromList(track: SavedTrackEntity) {
        val currentList = uiState.value.filteredTracks.ifEmpty { listOf(track) }
        val index = currentList.indexOfFirst { it.uriString == track.uriString }.coerceAtLeast(0)
        engineManager.loadQueueAndPlay(
            tracks = currentList,
            startIndex = index,
            resumeSavedPosition = true,
            autoPlay = true
        )
    }

    fun togglePlayPause() = engineManager.togglePlayPause()
    fun seekForward10s() = engineManager.seekByDeltaMs(10_000L)
    fun seekBackward10s() = engineManager.seekByDeltaMs(-10_000L)
    fun seekToMs(posMs: Long) = engineManager.seekToMs(posMs)
    fun skipNext() = engineManager.skipToNext()
    fun skipPrevious() = engineManager.skipToPrevious()
    fun setPlaybackSpeed(speed: Float) = engineManager.setPlaybackSpeed(speed)

    fun setBluetoothVolume(volume: Float) = engineManager.setBluetoothPlayerVolume(volume)
    fun toggleBluetoothMute() = engineManager.toggleBluetoothMute()

    fun setPhoneSpeakerVolume(fraction: Float) {
        engineManager.routeController.setPhoneSpeakerVolumeFraction(fraction)
        _youtubeWebGainPercent.value = (fraction * 100).toInt().coerceIn(0, 100)
    }

    fun togglePhoneSpeakerMute() {
        engineManager.routeController.togglePhoneSpeakerMute()
        val muted = engineManager.routingState.value.isPhoneSpeakerMuted
        _youtubeWebGainPercent.value = if (muted) 0 else (engineManager.routingState.value.phoneSpeakerVolumeFraction * 100).toInt()
    }

    fun setPodcastPinnedToBluetooth(pinned: Boolean) {
        engineManager.routeController.setPodcastPinnedToBluetooth(pinned)
    }

    fun selectBluetoothDevice(deviceId: Int) {
        engineManager.routeController.selectBluetoothDevice(deviceId)
    }

    fun setPhoneSpeakerOverride(enabled: Boolean) {
        engineManager.routeController.setPhoneSpeakerOverrideEnabled(enabled)
    }

    fun setYoutubeSpeakerMode(mode: YoutubeSpeakerMode) {
        engineManager.routeController.setYoutubeSpeakerMode(mode)
    }

    fun selectPlaylistFilter(playlist: String) {
        _selectedPlaylist.value = playlist
    }

    fun toggleTrackFavorite(track: SavedTrackEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(track.uriString, !track.isFavorite)
        }
    }

    fun deleteTrack(track: SavedTrackEntity) {
        viewModelScope.launch {
            repository.removeTrack(track.uriString)
        }
    }

    fun addTimestampBookmark(customLabel: String) {
        val currentTrack = uiState.value.engine.currentTrack ?: return
        val posMs = uiState.value.engine.currentPositionMs
        val finalLabel = customLabel.trim().ifBlank {
            "علامة عند ${formatDurationAr(posMs)}"
        }
        viewModelScope.launch {
            repository.addBookmark(
                trackUriString = currentTrack.uriString,
                trackTitle = currentTrack.title,
                positionMs = posMs,
                label = finalLabel
            )
            _showBookmarkDialog.value = false
            _statusBanner.value = "تم حفظ العلامة المرجعية: $finalLabel"
        }
    }

    fun jumpToBookmark(bookmark: TrackBookmarkEntity) {
        val currentTrack = uiState.value.engine.currentTrack
        if (currentTrack?.uriString == bookmark.trackUriString) {
            engineManager.seekToMs(bookmark.positionMs)
        } else {
            val all = uiState.value.allTracks
            val idx = all.indexOfFirst { it.uriString == bookmark.trackUriString }
            if (idx >= 0) {
                engineManager.loadQueueAndPlay(all, idx, resumeSavedPosition = false, autoPlay = true)
                engineManager.seekToMs(bookmark.positionMs)
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            repository.deleteBookmark(id)
        }
    }

    fun setSleepTimerMinutes(minutes: Int?) {
        engineManager.startSleepTimer(minutes)
        _showSleepTimerDialog.value = false
        _statusBanner.value = if (minutes != null && minutes > 0) {
            "تم ضبط مؤقت النوم على $minutes دقيقة"
        } else {
            "تم إيقاف مؤقت النوم"
        }
    }

    fun setLayoutMode(mode: SplitLayoutMode) {
        _layoutMode.value = mode
    }

    fun setShowOriginOsDialog(show: Boolean) {
        _showOriginOsDialog.value = show
    }

    fun setShowBookmarkDialog(show: Boolean) {
        _showBookmarkDialog.value = show
    }

    fun setShowSleepTimerDialog(show: Boolean) {
        _showSleepTimerDialog.value = show
    }

    fun dismissStatusBanner() {
        _statusBanner.value = null
    }

    fun updateYoutubeUrl(url: String) {
        _youtubeUrl.value = url
    }

    private fun checkBatteryOptimizationIgnored(): Boolean {
        return runCatching {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isIgnoringBatteryOptimizations(appContext.packageName)
        }.getOrDefault(false)
    }

    companion object {
        fun formatDurationAr(durationMs: Long): String {
            val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L).toInt()
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }
    }
}
