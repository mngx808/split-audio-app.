package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.SavedTrackEntity
import com.example.data.TrackBookmarkEntity
import com.example.service.AudioRoutingSnapshot
import com.example.service.PodcastEngineState
import com.example.ui.SplitAudioViewModel
import com.example.ui.theme.BluetoothContainer
import com.example.ui.theme.BluetoothEmerald
import com.example.ui.theme.BluetoothEmeraldSoft
import com.example.ui.theme.ConsoleCardBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.DangerRose
import com.example.ui.theme.OriginCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningGold

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BluetoothPodcastPlayerSection(
    engine: PodcastEngineState,
    routing: AudioRoutingSnapshot,
    tracks: List<SavedTrackEntity>,
    playlists: List<String>,
    selectedPlaylist: String,
    bookmarks: List<TrackBookmarkEntity>,
    isScanningStorage: Boolean,
    onPickFilesClick: () -> Unit,
    onPickFolderClick: () -> Unit,
    onScanStorageClick: () -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onSelectTrack: (SavedTrackEntity) -> Unit,
    onToggleFavorite: (SavedTrackEntity) -> Unit,
    onDeleteTrack: (SavedTrackEntity) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBackward10s: () -> Unit,
    onSeekForward10s: () -> Unit,
    onSeekToMs: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onTogglePinToBluetooth: (Boolean) -> Unit,
    onSelectBluetoothDevice: (Int) -> Unit,
    onOpenSleepTimerDialog: () -> Unit,
    onOpenBookmarkDialog: () -> Unit,
    onJumpToBookmark: (TrackBookmarkEntity) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, BluetoothEmerald.copy(alpha = 0.55f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ConsoleSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Section Header + Green Connected Bluetooth Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BluetoothContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BluetoothAudio,
                            contentDescription = "سماعة البلوتوث",
                            tint = BluetoothEmerald
                        )
                    }
                    Column {
                        Text(
                            text = "قسم البودكاست والموسيقى (سماعة البلوتوث)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        val btLabel = routing.activeBluetoothDevice?.productName
                            ?: "الربط التلقائي عند اتصال البلوتوث"
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (routing.activeBluetoothDevice != null) BluetoothEmerald else WarningGold
                                    )
                            )
                            Text(
                                text = btLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = BluetoothEmeraldSoft,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Direct Bluetooth Pinning Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "تثبيت بلوتوث",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Switch(
                        checked = routing.isPodcastPinnedToBluetooth,
                        onCheckedChange = onTogglePinToBluetooth,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = BluetoothEmerald
                        ),
                        modifier = Modifier.testTag("pin_podcast_bluetooth_switch")
                    )
                }
            }

            // If multiple Bluetooth output devices exist, show quick selector chips
            if (routing.connectedBluetoothDevices.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اختر السماعة:",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    routing.connectedBluetoothDevices.forEach { device ->
                        FilterChip(
                            selected = routing.activeBluetoothDevice?.id == device.id,
                            onClick = { onSelectBluetoothDevice(device.id) },
                            label = { Text(device.productName) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BluetoothContainer,
                                selectedLabelColor = BluetoothEmeraldSoft
                            )
                        )
                    }
                }
            }

            // File / Folder / MediaStore Picker Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onPickFilesClick,
                    colors = ButtonDefaults.buttonColors(containerColor = BluetoothEmerald),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_audio_files_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AudioFile,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "ملفات صوتية",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.Black,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onPickFolderClick,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pick_audio_folder_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CreateNewFolder,
                        contentDescription = null,
                        tint = BluetoothEmeraldSoft,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "اختيار مجلد",
                        style = MaterialTheme.typography.labelLarge,
                        color = BluetoothEmeraldSoft,
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onScanStorageClick,
                    enabled = !isScanningStorage,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("scan_storage_audio_button")
                ) {
                    if (isScanningStorage) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = BluetoothEmerald
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = null,
                            tint = OriginCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "فحص الهاتف",
                        style = MaterialTheme.typography.labelLarge,
                        color = OriginCyan,
                        maxLines = 1
                    )
                }
            }

            // Active Track Player Console Card
            Surface(
                color = ConsoleSurfaceElevated,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ConsoleCardBorder, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val activeTrack = engine.currentTrack
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeTrack?.title ?: "اختر ملفاً صوتياً لبدء البث عبر البلوتوث",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = activeTrack?.artistOrFolder
                                    ?: "MP3 • M4A • WAV • OGG | معزول عن تركيز صوت يوتيوب",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // AudioFocus Bypass Pill
                        Surface(
                            color = BluetoothContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = BluetoothEmerald,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "مانع انقطاع نشط",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BluetoothEmeraldSoft
                                )
                            }
                        }
                    }

                    // Seek Bar + Timestamps
                    val safeDuration = engine.durationMs.coerceAtLeast(1L)
                    val sliderPosition = (engine.currentPositionMs.toFloat() / safeDuration.toFloat())
                        .coerceIn(0f, 1f)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = sliderPosition,
                            onValueChange = { fraction ->
                                val targetMs = (fraction * safeDuration).toLong()
                                onSeekToMs(targetMs)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = BluetoothEmerald,
                                activeTrackColor = BluetoothEmerald,
                                inactiveTrackColor = ConsoleCardBorder
                            ),
                            modifier = Modifier.testTag("podcast_seek_slider")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = SplitAudioViewModel.formatDurationAr(engine.currentPositionMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = BluetoothEmeraldSoft
                            )
                            Text(
                                text = SplitAudioViewModel.formatDurationAr(engine.durationMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }

                    // Transport Controls: -10s, Prev, Play/Pause, Next, +10s
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onSeekBackward10s,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ConsoleSurface)
                                .testTag("seek_backward_10s_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "ترجيع ١٠ ثوانٍ",
                                tint = TextPrimary
                            )
                        }

                        IconButton(
                            onClick = onSkipPrevious,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("skip_previous_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "الملف السابق",
                                tint = TextPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(BluetoothEmerald)
                                .clickable { onTogglePlayPause() }
                                .testTag("podcast_play_pause_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (engine.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (engine.isPlaying) "إيقاف مؤقت" else "تشغيل البودكاست",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        IconButton(
                            onClick = onSkipNext,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("skip_next_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "الملف التالي",
                                tint = TextPrimary
                            )
                        }

                        IconButton(
                            onClick = onSeekForward10s,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ConsoleSurface)
                                .testTag("seek_forward_10s_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "تقديم ١٠ ثوانٍ",
                                tint = TextPrimary
                            )
                        }
                    }

                    // Speed Control Chips + Sleep Timer + Timestamp Bookmark Button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        speedOptions.forEach { spd ->
                            val isSelected = kotlin.math.abs(engine.playbackSpeed - spd) < 0.05f
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSpeedSelected(spd) },
                                label = { Text("${spd}x") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BluetoothContainer,
                                    selectedLabelColor = BluetoothEmeraldSoft
                                ),
                                modifier = Modifier.testTag("speed_chip_${spd}")
                            )
                        }

                        AssistChip(
                            onClick = onOpenSleepTimerDialog,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Bedtime,
                                    contentDescription = null,
                                    tint = WarningGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                val rem = engine.sleepTimerRemainingSeconds
                                Text(
                                    text = if (rem != null) {
                                        "نوم: ${rem / 60}:${String.format("%02d", rem % 60)}"
                                    } else {
                                        "مؤقت النوم"
                                    }
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = WarningGold
                            ),
                            modifier = Modifier.testTag("sleep_timer_button")
                        )

                        AssistChip(
                            onClick = onOpenBookmarkDialog,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.BookmarkAdd,
                                    contentDescription = null,
                                    tint = OriginCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text("+ علامة مرجعية") },
                            colors = AssistChipDefaults.assistChipColors(
                                labelColor = OriginCyan
                            ),
                            modifier = Modifier.testTag("add_bookmark_button")
                        )
                    }

                    // Saved Bookmarks for current track
                    if (bookmarks.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bookmarks,
                                    contentDescription = null,
                                    tint = OriginCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "العلامات الزمنية المحفوظة لهذه الحلقة:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OriginCyan
                                )
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                bookmarks.forEach { bm ->
                                    Surface(
                                        color = ConsoleSurface,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .border(1.dp, OriginCyan.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                            .clickable { onJumpToBookmark(bm) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${bm.label} (${SplitAudioViewModel.formatDurationAr(bm.positionMs)})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextPrimary
                                            )
                                            Text(
                                                text = "×",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = DangerRose,
                                                modifier = Modifier.clickable { onDeleteBookmark(bm.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Playlist Filter Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                playlists.forEach { pl ->
                    FilterChip(
                        selected = selectedPlaylist == pl,
                        onClick = { onSelectPlaylist(pl) },
                        label = { Text(pl) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BluetoothContainer,
                            selectedLabelColor = BluetoothEmeraldSoft
                        )
                    )
                }
            }

            // Playlist Queue List
            Text(
                text = "قائمة التشغيل المحفوظة (${tracks.size} ملف):",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 230.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(tracks, key = { it.uriString }) { track ->
                    val isCurrent = engine.currentTrack?.uriString == track.uriString
                    TrackQueueRowItem(
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && engine.isPlaying,
                        onClick = { onSelectTrack(track) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onDelete = { onDeleteTrack(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackQueueRowItem(
    track: SavedTrackEntity,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (isCurrent) BluetoothContainer else ConsoleSurfaceElevated,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isCurrent) BluetoothEmerald else ConsoleCardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .testTag("track_item_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isCurrent) BluetoothEmerald else ConsoleSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.GraphicEq else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = if (isCurrent) Color.Black else BluetoothEmeraldSoft,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = if (isCurrent) BluetoothEmeraldSoft else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = SplitAudioViewModel.formatDurationAr(track.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        if (track.lastPositionMs > 2000L) {
                            Text(
                                text = "• استئناف من ${SplitAudioViewModel.formatDurationAr(track.lastPositionMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = OriginCyan
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "تفضيل",
                        tint = if (track.isFavorite) DangerRose else TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "حذف من القائمة",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddBookmarkDialog(
    currentPositionMs: Long,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleSurfaceElevated,
        title = {
            Text(
                text = "إضافة علامة زمنية (${SplitAudioViewModel.formatDurationAr(currentPositionMs)})",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        },
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("عنوان العلامة (اختياري)") },
                placeholder = { Text("مثال: بداية الفصل الثاني أو فكرة مهمة") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bookmark_label_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(label) },
                colors = ButtonDefaults.buttonColors(containerColor = BluetoothEmerald),
                modifier = Modifier.testTag("confirm_bookmark_button")
            ) {
                Text("حفظ العلامة", color = Color.Black)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إلغاء", color = TextPrimary)
            }
        }
    )
}

@Composable
fun SleepTimerDialog(
    activeRemainingSeconds: Int?,
    onSelectMinutes: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val presets = listOf(10, 15, 30, 45, 60)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleSurfaceElevated,
        title = {
            Text(
                text = "مؤقت النوم الذكي (للبودكاست)",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "سيتم إيقاف البودكاست تلقائياً وحفظ موضع الاستماع الحالي عند انتهاء الوقت:",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                presets.forEach { mins ->
                    OutlinedButton(
                        onClick = { onSelectMinutes(mins) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إيقاف بعد $mins دقيقة", color = BluetoothEmeraldSoft)
                    }
                }
                if (activeRemainingSeconds != null) {
                    Button(
                        onClick = { onSelectMinutes(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRose),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("إلغاء المؤقت الحالي", color = Color.White)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("إغلاق", color = TextPrimary)
            }
        }
    )
}
