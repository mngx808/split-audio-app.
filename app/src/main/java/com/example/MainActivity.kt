package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.example.ui.SplitAudioUiState
import com.example.ui.SplitAudioViewModel
import com.example.ui.SplitLayoutMode
import com.example.ui.components.AddBookmarkDialog
import com.example.ui.components.BluetoothPodcastPlayerSection
import com.example.ui.components.IndependentVolumeSlidersCard
import com.example.ui.components.OriginOsProtectionDialog
import com.example.ui.components.SleepTimerDialog
import com.example.ui.components.TopConsoleHeaderCard
import com.example.ui.components.YoutubeSpeakerRouterSection
import com.example.ui.theme.ConsoleBackground
import com.example.ui.theme.MyApplicationTheme

@UnstableApi
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: SplitAudioViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                SplitAudioRouterAppScreen(
                    uiState = uiState,
                    viewModel = viewModel,
                    onRequestBatteryExemption = { requestIgnoreBatteryOptimizations() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        runCatching {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }.onFailure {
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }
}

@UnstableApi
@Composable
fun SplitAudioRouterAppScreen(
    uiState: SplitAudioUiState,
    viewModel: SplitAudioViewModel,
    onRequestBatteryExemption: () -> Unit
) {
    val context = LocalContext.current

    // Return to unified split screen if expanded sub-mode is active
    BackHandler(enabled = uiState.layoutMode != SplitLayoutMode.UNIFIED_SPLIT) {
        viewModel.setLayoutMode(SplitLayoutMode.UNIFIED_SPLIT)
    }

    // Runtime permissions launcher for Android 13-16 (API 33-36) on vivo Y05
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        viewModel.refreshBluetoothDevices()
        val audioGranted = grantResults[Manifest.permission.READ_MEDIA_AUDIO] == true ||
            grantResults[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        if (audioGranted) {
            viewModel.scanDeviceStorageForAudio()
        }
    }

    // File Picker (MP3, M4A, WAV, OGG, AAC)
    val audioFilesPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onAudioFilesPicked(uris)
        }
    }

    // Folder Picker (SAF Document Tree)
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        viewModel.onFolderPicked(treeUri)
    }

    fun buildRequiredPermissionsList(): Array<String> {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.READ_MEDIA_AUDIO)
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            list.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return list.toTypedArray()
    }

    // Refresh device and battery state on launch without blocking the initial UI render
    LaunchedEffect(Unit) {
        viewModel.refreshBatteryOptimizationState()
        viewModel.refreshBluetoothDevices()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleBackground),
        containerColor = ConsoleBackground,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Top Console Header & OriginOS 6 Protection Bar
                TopConsoleHeaderCard(
                    routing = uiState.routing,
                    isBatteryIgnored = uiState.isBatteryOptimizationIgnored,
                    isForegroundServiceActive = uiState.engine.isForegroundServiceActive,
                    layoutMode = uiState.layoutMode,
                    statusBannerMessage = uiState.statusBannerMessage,
                    onRefreshDevices = { viewModel.refreshBluetoothDevices() },
                    onOpenOriginOsGuide = {
                        viewModel.refreshBatteryOptimizationState()
                        viewModel.setShowOriginOsDialog(true)
                    },
                    onRequestPermissionsClick = {
                        permissionsLauncher.launch(buildRequiredPermissionsList())
                    },
                    onLayoutModeChange = { viewModel.setLayoutMode(it) },
                    onDismissBanner = { viewModel.dismissStatusBanner() }
                )

                // 2. Independent Volume Control Matrix (Bluetooth Stream vs Phone Speaker Stream)
                IndependentVolumeSlidersCard(
                    bluetoothVolume = uiState.engine.playerVolume,
                    isBluetoothMuted = uiState.engine.isMuted,
                    speakerVolume = uiState.routing.phoneSpeakerVolumeFraction,
                    isSpeakerMuted = uiState.routing.isPhoneSpeakerMuted,
                    onBluetoothVolumeChange = { viewModel.setBluetoothVolume(it) },
                    onToggleBluetoothMute = { viewModel.toggleBluetoothMute() },
                    onSpeakerVolumeChange = { viewModel.setPhoneSpeakerVolume(it) },
                    onToggleSpeakerMute = { viewModel.togglePhoneSpeakerMute() }
                )

                // 3. Bluetooth Podcast & Music Player Section
                if (uiState.layoutMode != SplitLayoutMode.YOUTUBE_EXPANDED) {
                    BluetoothPodcastPlayerSection(
                        engine = uiState.engine,
                        routing = uiState.routing,
                        tracks = uiState.filteredTracks,
                        playlists = uiState.playlists,
                        selectedPlaylist = uiState.selectedPlaylist,
                        bookmarks = uiState.bookmarksForCurrentTrack,
                        isScanningStorage = uiState.isScanningStorage,
                        onPickFilesClick = {
                            audioFilesPickerLauncher.launch(arrayOf("audio/*"))
                        },
                        onPickFolderClick = {
                            folderPickerLauncher.launch(null)
                        },
                        onScanStorageClick = {
                            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Manifest.permission.READ_MEDIA_AUDIO
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            }
                            if (ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED) {
                                viewModel.scanDeviceStorageForAudio()
                            } else {
                                permissionsLauncher.launch(buildRequiredPermissionsList())
                            }
                        },
                        onSelectPlaylist = { viewModel.selectPlaylistFilter(it) },
                        onSelectTrack = { viewModel.playTrackFromList(it) },
                        onToggleFavorite = { viewModel.toggleTrackFavorite(it) },
                        onDeleteTrack = { viewModel.deleteTrack(it) },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSeekBackward10s = { viewModel.seekBackward10s() },
                        onSeekForward10s = { viewModel.seekForward10s() },
                        onSeekToMs = { viewModel.seekToMs(it) },
                        onSkipPrevious = { viewModel.skipPrevious() },
                        onSkipNext = { viewModel.skipNext() },
                        onSpeedSelected = { viewModel.setPlaybackSpeed(it) },
                        onTogglePinToBluetooth = { viewModel.setPodcastPinnedToBluetooth(it) },
                        onSelectBluetoothDevice = { viewModel.selectBluetoothDevice(it) },
                        onOpenSleepTimerDialog = { viewModel.setShowSleepTimerDialog(true) },
                        onOpenBookmarkDialog = { viewModel.setShowBookmarkDialog(true) },
                        onJumpToBookmark = { viewModel.jumpToBookmark(it) },
                        onDeleteBookmark = { viewModel.deleteBookmark(it) }
                    )
                }

                // 4. YouTube Phone Speaker Router & Embedded Browser Section
                if (uiState.layoutMode != SplitLayoutMode.PODCAST_EXPANDED) {
                    YoutubeSpeakerRouterSection(
                        routing = uiState.routing,
                        layoutMode = uiState.layoutMode,
                        youtubeWebGainPercent = uiState.youtubeWebGainPercent,
                        onToggleSpeakerOverride = { viewModel.setPhoneSpeakerOverride(it) },
                        onSelectYoutubeMode = { viewModel.setYoutubeSpeakerMode(it) },
                        onToggleExpandYoutube = {
                            if (uiState.layoutMode == SplitLayoutMode.YOUTUBE_EXPANDED) {
                                viewModel.setLayoutMode(SplitLayoutMode.UNIFIED_SPLIT)
                            } else {
                                viewModel.setLayoutMode(SplitLayoutMode.YOUTUBE_EXPANDED)
                            }
                        },
                        onUrlChanged = { viewModel.updateYoutubeUrl(it) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Dialogs
    if (uiState.showOriginOsGuideDialog) {
        OriginOsProtectionDialog(
            isBatteryIgnored = uiState.isBatteryOptimizationIgnored,
            onRequestIgnoreBatteryOptimizations = {
                onRequestBatteryExemption()
                viewModel.setShowOriginOsDialog(false)
            },
            onDismiss = { viewModel.setShowOriginOsDialog(false) }
        )
    }

    if (uiState.showBookmarkDialog) {
        AddBookmarkDialog(
            currentPositionMs = uiState.engine.currentPositionMs,
            onConfirm = { label -> viewModel.addTimestampBookmark(label) },
            onDismiss = { viewModel.setShowBookmarkDialog(false) }
        )
    }

    if (uiState.showSleepTimerDialog) {
        SleepTimerDialog(
            activeRemainingSeconds = uiState.engine.sleepTimerRemainingSeconds,
            onSelectMinutes = { mins -> viewModel.setSleepTimerMinutes(mins) },
            onDismiss = { viewModel.setShowSleepTimerDialog(false) }
        )
    }
}
