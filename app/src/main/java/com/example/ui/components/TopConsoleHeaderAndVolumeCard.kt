package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.service.AudioRoutingSnapshot
import com.example.ui.SplitLayoutMode
import com.example.ui.theme.BluetoothContainer
import com.example.ui.theme.BluetoothEmerald
import com.example.ui.theme.BluetoothEmeraldSoft
import com.example.ui.theme.ConsoleCardBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.OriginCyan
import com.example.ui.theme.SpeakerAmber
import com.example.ui.theme.SpeakerContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.WarningGold

@Composable
fun TopConsoleHeaderCard(
    routing: AudioRoutingSnapshot,
    isBatteryIgnored: Boolean,
    isForegroundServiceActive: Boolean,
    layoutMode: SplitLayoutMode,
    statusBannerMessage: String?,
    onRefreshDevices: () -> Unit,
    onOpenOriginOsGuide: () -> Unit,
    onRequestPermissionsClick: () -> Unit,
    onLayoutModeChange: (SplitLayoutMode) -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ConsoleCardBorder, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ConsoleSurface)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painterResource(id = R.drawable.img_audio_console_banner_1794244371527),
                contentDescription = "لوحة استوديو توجيه الصوت المزدوج",
                contentScale = ContentScale.Crop,
                alpha = 0.22f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                ConsoleSurface.copy(alpha = 0.72f),
                                ConsoleSurface.copy(alpha = 0.98f)
                            )
                        )
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Title Row + vivo Y05 OriginOS 6 Shield Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "موجه الصوت المزدوج",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary
                            )
                            Surface(
                                color = OriginCyan.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "vivo Y05 • API 36",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OriginCyan,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                        Text(
                            text = "فصل مسار البودكاست (بلوتوث) عن صوت اليوتيوب (سماعة الهاتف)",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onRefreshDevices,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(ConsoleSurfaceElevated)
                                .testTag("refresh_audio_devices_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تحديث أجهزة البلوتوث",
                                tint = BluetoothEmerald
                            )
                        }

                        Surface(
                            color = if (isBatteryIgnored) BluetoothContainer else Color(0xFF2D2110),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clickable { onOpenOriginOsGuide() }
                                .border(
                                    1.dp,
                                    if (isBatteryIgnored) BluetoothEmerald else WarningGold,
                                    RoundedCornerShape(12.dp)
                                )
                                .testTag("origin_os_protection_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isBatteryIgnored) Icons.Default.CheckCircle else Icons.Default.BatteryChargingFull,
                                    contentDescription = "حماية الخلفية OriginOS 6",
                                    tint = if (isBatteryIgnored) BluetoothEmerald else WarningGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isBatteryIgnored) "OriginOS 6 محمي" else "تثبيت بالخلفية",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isBatteryIgnored) BluetoothEmeraldSoft else WarningGold
                                )
                            }
                        }
                    }
                }

                // Dual Active Route Status Badges (Green Bluetooth Headset Badge + Amber Speaker Badge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val btDevice = routing.activeBluetoothDevice
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                1.dp,
                                if (btDevice != null) BluetoothEmerald else ConsoleCardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("bluetooth_connected_badge"),
                        color = if (btDevice != null) BluetoothContainer else ConsoleSurfaceElevated,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (btDevice != null) BluetoothEmerald else WarningGold)
                            )
                            Icon(
                                imageVector = if (btDevice != null) Icons.Default.BluetoothConnected else Icons.Default.BluetoothSearching,
                                contentDescription = "حالة سماعة البلوتوث",
                                tint = if (btDevice != null) BluetoothEmerald else WarningGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = btDevice?.productName ?: "بانتظار سماعة بلوتوث",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (btDevice != null) BluetoothEmeraldSoft else TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (btDevice != null) {
                                        "متصل ومعزول • ${btDevice.typeLabelAr}"
                                    } else {
                                        "اضغط لتحديث أو منح إذن البلوتوث"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onRequestPermissionsClick() }
                                )
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                1.dp,
                                if (routing.isPhoneSpeakerOverrideEnabled) SpeakerAmber else ConsoleCardBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .testTag("speaker_route_badge"),
                        color = if (routing.isPhoneSpeakerOverrideEnabled) SpeakerContainer else ConsoleSurfaceElevated,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SpeakerPhone,
                                contentDescription = "سماعة الهاتف الخارجية",
                                tint = SpeakerAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "سماعة الهاتف الخارجية",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = SpeakerAmber,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (routing.isPhoneSpeakerOverrideEnabled) {
                                        "مخصصة لصوت اليوتيوب • نشط"
                                    } else {
                                        "التوجيه متوقف مؤقتاً"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Layout Mode Switcher (Split Screen vs Podcast Focus vs YouTube Focus)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewAgenda,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "عرض الشاشة:",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    FilterChip(
                        selected = layoutMode == SplitLayoutMode.UNIFIED_SPLIT,
                        onClick = { onLayoutModeChange(SplitLayoutMode.UNIFIED_SPLIT) },
                        label = { Text("مزدوج (بودكاست + يوتيوب)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OriginCyan.copy(alpha = 0.2f),
                            selectedLabelColor = OriginCyan
                        ),
                        modifier = Modifier.testTag("layout_split_chip")
                    )
                    FilterChip(
                        selected = layoutMode == SplitLayoutMode.PODCAST_EXPANDED,
                        onClick = { onLayoutModeChange(SplitLayoutMode.PODCAST_EXPANDED) },
                        label = { Text("البودكاست فقط") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BluetoothEmerald.copy(alpha = 0.2f),
                            selectedLabelColor = BluetoothEmeraldSoft
                        ),
                        modifier = Modifier.testTag("layout_podcast_chip")
                    )
                    FilterChip(
                        selected = layoutMode == SplitLayoutMode.YOUTUBE_EXPANDED,
                        onClick = { onLayoutModeChange(SplitLayoutMode.YOUTUBE_EXPANDED) },
                        label = { Text("يوتيوب كامل") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SpeakerAmber.copy(alpha = 0.2f),
                            selectedLabelColor = SpeakerAmber
                        ),
                        modifier = Modifier.testTag("layout_youtube_chip")
                    )
                }

                // Live Route Banner or User Action Feedback
                AnimatedVisibility(visible = statusBannerMessage != null) {
                    if (statusBannerMessage != null) {
                        Surface(
                            color = ConsoleSurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = statusBannerMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BluetoothEmeraldSoft,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = onDismissBanner,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "إغلاق التنبيه",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
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
fun IndependentVolumeSlidersCard(
    bluetoothVolume: Float,
    isBluetoothMuted: Boolean,
    speakerVolume: Float,
    isSpeakerMuted: Boolean,
    onBluetoothVolumeChange: (Float) -> Unit,
    onToggleBluetoothMute: () -> Unit,
    onSpeakerVolumeChange: (Float) -> Unit,
    onToggleSpeakerMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ConsoleCardBorder, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = ConsoleSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "التحكم المستقل في الصوت (فصل المسارين)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = "بدون تداخل AudioFocus",
                    style = MaterialTheme.typography.labelSmall,
                    color = BluetoothEmeraldSoft
                )
            }

            // Slider 1: Bluetooth Podcast Player Volume (exoPlayer.volume)
            Surface(
                color = BluetoothContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onToggleBluetoothMute,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("bluetooth_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isBluetoothMuted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.Default.Headphones
                            },
                            contentDescription = "كتم أو تشغيل صوت سماعة البلوتوث",
                            tint = BluetoothEmerald
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "صوت البودكاست (سماعة البلوتوث)",
                                style = MaterialTheme.typography.labelLarge,
                                color = BluetoothEmeraldSoft
                            )
                            Text(
                                text = "${(bluetoothVolume * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = BluetoothEmerald
                            )
                        }
                        Slider(
                            value = bluetoothVolume,
                            onValueChange = onBluetoothVolumeChange,
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = BluetoothEmerald,
                                activeTrackColor = BluetoothEmerald,
                                inactiveTrackColor = ConsoleCardBorder
                            ),
                            modifier = Modifier.testTag("bluetooth_volume_slider")
                        )
                    }
                }
            }

            // Slider 2: Phone Speaker Stream Volume (YouTube Stream)
            Surface(
                color = SpeakerContainer.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onToggleSpeakerMute,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("speaker_mute_button")
                    ) {
                        Icon(
                            imageVector = if (isSpeakerMuted) {
                                Icons.AutoMirrored.Filled.VolumeOff
                            } else {
                                Icons.AutoMirrored.Filled.VolumeUp
                            },
                            contentDescription = "كتم أو تشغيل صوت سماعة الهاتف",
                            tint = SpeakerAmber
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "صوت اليوتيوب (سماعة الهاتف الخارجية)",
                                style = MaterialTheme.typography.labelLarge,
                                color = SpeakerAmber
                            )
                            Text(
                                text = "${(speakerVolume * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = SpeakerAmber
                            )
                        }
                        Slider(
                            value = speakerVolume,
                            onValueChange = onSpeakerVolumeChange,
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = SpeakerAmber,
                                activeTrackColor = SpeakerAmber,
                                inactiveTrackColor = ConsoleCardBorder
                            ),
                            modifier = Modifier.testTag("speaker_volume_slider")
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OriginOsProtectionDialog(
    isBatteryIgnored: Boolean,
    onRequestIgnoreBatteryOptimizations: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleSurfaceElevated,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = OriginCyan
                )
                Text(
                    text = "دليل حماية الخلفية (vivo Y05 • OriginOS 6)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "يحتوي جهاز vivo Y05 على ذاكرة 4GB RAM مع إدارة بطارية صارمة في واجهة OriginOS 6. لضمان عدم توقف البودكاست أبداً أثناء مشاهدة يوتيوب أو إغلاق الشاشة:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )

                OriginOsStepItem(
                    stepNumber = "١",
                    title = "استثناء تحسين البطارية (مباشر)",
                    subtitle = if (isBatteryIgnored) {
                        "مفعّل بنجاح! لن يقوم النظام بإيقاف الخدمة الصوتية."
                    } else {
                        "اضغط على الزر بالأسفل لاختيار (عدم التقييد / السماح) للتطبيق."
                    },
                    isDone = isBatteryIgnored
                )

                OriginOsStepItem(
                    stepNumber = "٢",
                    title = "قفل التطبيق في شاشة التطبيقات الحديثة",
                    subtitle = "افتح قائمة التطبيقات المفتوحة في OriginOS 6، اضغط على السهم بجانب اسم التطبيق واختر (قفل / Lock) ليظهر رمز القفل أعلى البطاقة.",
                    isDone = true
                )

                OriginOsStepItem(
                    stepNumber = "٣",
                    title = "خدمة Foreground MediaPlayback الخفيفة",
                    subtitle = "يعمل المحرك الصوتي تلقائياً كخدمة وسائط أمامية خفيفة مع WakeLock جزئي لمنع انقطاع البلوتوث.",
                    isDone = true
                )
            }
        },
        confirmButton = {
            if (!isBatteryIgnored) {
                Button(
                    onClick = onRequestIgnoreBatteryOptimizations,
                    colors = ButtonDefaults.buttonColors(containerColor = BluetoothEmerald),
                    modifier = Modifier.testTag("grant_battery_exemption_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تفعيل استثناء البطارية الآن", color = Color.Black)
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("حسناً، فهمت", color = TextPrimary)
            }
        }
    )
}

@Composable
private fun OriginOsStepItem(
    stepNumber: String,
    title: String,
    subtitle: String,
    isDone: Boolean
) {
    Surface(
        color = ConsoleSurface,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(if (isDone) BluetoothEmerald.copy(alpha = 0.2f) else WarningGold.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stepNumber,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isDone) BluetoothEmerald else WarningGold
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}
