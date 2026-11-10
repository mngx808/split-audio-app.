package com.example.service

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ConnectedAudioDevice(
    val id: Int,
    val productName: String,
    val type: Int,
    val typeLabelAr: String,
    val isBluetooth: Boolean,
    val isBuiltInSpeaker: Boolean
)

enum class YoutubeSpeakerMode {
    MODE_A_GLOBAL_SPEAKER_OVERRIDE,
    MODE_B_EMBEDDED_WEBVIEW_SPEAKER
}

data class AudioRoutingSnapshot(
    val connectedBluetoothDevices: List<ConnectedAudioDevice> = emptyList(),
    val activeBluetoothDevice: ConnectedAudioDevice? = null,
    val builtInSpeakerDevice: ConnectedAudioDevice? = null,
    val isPodcastPinnedToBluetooth: Boolean = true,
    val isPhoneSpeakerOverrideEnabled: Boolean = true,
    val youtubeSpeakerMode: YoutubeSpeakerMode = YoutubeSpeakerMode.MODE_B_EMBEDDED_WEBVIEW_SPEAKER,
    val bluetoothPlayerVolume: Float = 0.85f,
    val phoneSpeakerVolumeFraction: Float = 0.70f,
    val isBluetoothMuted: Boolean = false,
    val isPhoneSpeakerMuted: Boolean = false,
    val lastRouteEventAr: String = "جاهز لفصل مسارات الصوت (بلوتوث + سماعة الهاتف)"
)

class AudioRouteController(
    context: Context,
    private val onBluetoothTargetChanged: (AudioDeviceInfo?) -> Unit
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _snapshot = MutableStateFlow(AudioRoutingSnapshot())
    val snapshot: StateFlow<AudioRoutingSnapshot> = _snapshot.asStateFlow()

    private var preferredBluetoothDeviceId: Int? = null
    private var preMuteSpeakerVolume: Int = -1

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshOutputDevices(triggerReasonAr = "تم اكتشاف جهاز إخراج صوتي جديد")
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshOutputDevices(triggerReasonAr = "تم فصل أحد أجهزة الصوت، جاري تحديث المسار")
        }
    }

    init {
        audioManager.registerAudioDeviceCallback(deviceCallback, mainHandler)
        syncInitialSpeakerVolume()
        refreshOutputDevices(triggerReasonAr = "تم تشغيل مراقب المسارات الصوتية لجهاز vivo Y05")
    }

    fun release() {
        runCatching {
            audioManager.unregisterAudioDeviceCallback(deviceCallback)
            if (_snapshot.value.isPhoneSpeakerOverrideEnabled) {
                clearSpeakerOverrideInternal()
            }
        }
    }

    fun refreshOutputDevices(triggerReasonAr: String? = null) {
        val rawDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val bluetoothList = mutableListOf<ConnectedAudioDevice>()
        var speakerDevice: ConnectedAudioDevice? = null
        var matchedBtDeviceInfo: AudioDeviceInfo? = null

        for (info in rawDevices) {
            if (!info.isSink) continue
            val isBt = isBluetoothOutputType(info.type)
            val isSpeaker = info.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

            if (isBt) {
                val cleanName = info.productName?.toString()?.takeIf { it.isNotBlank() }
                    ?: "سماعة بلوتوث لاسلكية"
                bluetoothList.add(
                    ConnectedAudioDevice(
                        id = info.id,
                        productName = cleanName,
                        type = info.type,
                        typeLabelAr = getDeviceTypeLabelAr(info.type),
                        isBluetooth = true,
                        isBuiltInSpeaker = false
                    )
                )
            } else if (isSpeaker && speakerDevice == null) {
                speakerDevice = ConnectedAudioDevice(
                    id = info.id,
                    productName = "سماعة الهاتف الخارجية (vivo Y05)",
                    type = info.type,
                    typeLabelAr = "السماعة المدمجة",
                    isBluetooth = false,
                    isBuiltInSpeaker = true
                )
            }
        }

        // Determine active Bluetooth device (prefer A2DP/BLE Headset or user-selected device ID)
        val chosenBtUi = bluetoothList.firstOrNull { it.id == preferredBluetoothDeviceId }
            ?: bluetoothList.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
            }
            ?: bluetoothList.firstOrNull()

        if (chosenBtUi != null) {
            preferredBluetoothDeviceId = chosenBtUi.id
            matchedBtDeviceInfo = rawDevices.firstOrNull { it.id == chosenBtUi.id }
        }

        val current = _snapshot.value
        val statusMsg = when {
            chosenBtUi != null && current.isPodcastPinnedToBluetooth ->
                "تم ربط البودكاست حصرياً بـ ${chosenBtUi.productName} (${chosenBtUi.typeLabelAr})"
            chosenBtUi == null && current.isPodcastPinnedToBluetooth ->
                "بانتظار اتصال سماعة بلوتوث (سيتم الربط التلقائي فور الاتصال)"
            else -> triggerReasonAr ?: current.lastRouteEventAr
        }

        _snapshot.value = current.copy(
            connectedBluetoothDevices = bluetoothList,
            activeBluetoothDevice = chosenBtUi,
            builtInSpeakerDevice = speakerDevice,
            lastRouteEventAr = statusMsg
        )

        // Bind ExoPlayer strictly to Bluetooth device if pinning is active
        if (current.isPodcastPinnedToBluetooth) {
            onBluetoothTargetChanged(matchedBtDeviceInfo)
        } else {
            onBluetoothTargetChanged(null)
        }

        // Re-assert phone speaker override if active
        if (current.isPhoneSpeakerOverrideEnabled) {
            applySpeakerOverrideInternal()
        }
    }

    fun findBuiltInSpeakerDeviceInfo(): AudioDeviceInfo? {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.isSink && it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    fun findActiveBluetoothDeviceInfo(): AudioDeviceInfo? {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
        val preferredId = preferredBluetoothDeviceId
        if (preferredId != null) {
            outputs.firstOrNull { it.id == preferredId && isBluetoothOutputType(it.type) }?.let { return it }
        }
        return outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
        } ?: outputs.firstOrNull { isBluetoothOutputType(it.type) }
    }

    fun selectBluetoothDevice(deviceId: Int) {
        preferredBluetoothDeviceId = deviceId
        refreshOutputDevices(triggerReasonAr = "تم تغيير سماعة البلوتوث المختارة للبث")
    }

    fun setPodcastPinnedToBluetooth(pinned: Boolean) {
        _snapshot.value = _snapshot.value.copy(
            isPodcastPinnedToBluetooth = pinned,
            lastRouteEventAr = if (pinned) {
                "تم تفعيل العزل الحصري للبودكاست على سماعة البلوتوث"
            } else {
                "تم إلغاء تثبيت البلوتوث (المسار الافتراضي)"
            }
        )
        refreshOutputDevices()
    }

    fun setPhoneSpeakerOverrideEnabled(enabled: Boolean) {
        if (enabled) {
            applySpeakerOverrideInternal()
        } else {
            clearSpeakerOverrideInternal()
        }
        _snapshot.value = _snapshot.value.copy(
            isPhoneSpeakerOverrideEnabled = enabled,
            lastRouteEventAr = if (enabled) {
                "تم تفعيل توجيه صوت اليوتيوب والموبايل إلى السماعة الخارجية للهاتف"
            } else {
                "تم إيقاف توجيه السماعة الخارجية للهاتف"
            }
        )
        // Re-verify ExoPlayer remains strictly pinned to Bluetooth
        if (_snapshot.value.isPodcastPinnedToBluetooth) {
            onBluetoothTargetChanged(findActiveBluetoothDeviceInfo())
        }
    }

    fun setYoutubeSpeakerMode(mode: YoutubeSpeakerMode) {
        _snapshot.value = _snapshot.value.copy(
            youtubeSpeakerMode = mode,
            lastRouteEventAr = when (mode) {
                YoutubeSpeakerMode.MODE_A_GLOBAL_SPEAKER_OVERRIDE ->
                    "الوضع A نشط: توجيه صوت النظام الخارجي للسماعة المدمجة + تثبيت البودكاست على البلوتوث"
                YoutubeSpeakerMode.MODE_B_EMBEDDED_WEBVIEW_SPEAKER ->
                    "الوضع B نشط: مشغل يوتيوب المدمج داخل الشاشة مع توجيه الصوت لسماعة الهاتف"
            }
        )
        if (_snapshot.value.isPhoneSpeakerOverrideEnabled) {
            applySpeakerOverrideInternal()
        }
        if (_snapshot.value.isPodcastPinnedToBluetooth) {
            onBluetoothTargetChanged(findActiveBluetoothDeviceInfo())
        }
    }

    fun updateBluetoothVolumeState(volume: Float, isMuted: Boolean) {
        _snapshot.value = _snapshot.value.copy(
            bluetoothPlayerVolume = volume.coerceIn(0f, 1f),
            isBluetoothMuted = isMuted
        )
    }

    fun setPhoneSpeakerVolumeFraction(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        runCatching {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val targetIndex = (clamped * maxVol).toInt().coerceIn(0, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0)
        }
        _snapshot.value = _snapshot.value.copy(
            phoneSpeakerVolumeFraction = clamped,
            isPhoneSpeakerMuted = clamped <= 0.01f
        )
    }

    fun togglePhoneSpeakerMute() {
        val current = _snapshot.value
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (!current.isPhoneSpeakerMuted) {
            preMuteSpeakerVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0) }
            _snapshot.value = current.copy(isPhoneSpeakerMuted = true)
        } else {
            val restoredIndex = if (preMuteSpeakerVolume > 0) preMuteSpeakerVolume else (maxVol * 0.65).toInt()
            runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, restoredIndex, 0) }
            _snapshot.value = current.copy(
                isPhoneSpeakerMuted = false,
                phoneSpeakerVolumeFraction = restoredIndex.toFloat() / maxVol.toFloat()
            )
        }
    }

    private fun syncInitialSpeakerVolume() {
        runCatching {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            _snapshot.value = _snapshot.value.copy(
                phoneSpeakerVolumeFraction = (curVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f),
                isPhoneSpeakerMuted = curVol == 0
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun applySpeakerOverrideInternal() {
        runCatching {
            val speakerDevice = findBuiltInSpeakerDeviceInfo()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && speakerDevice != null) {
                audioManager.setCommunicationDevice(speakerDevice)
            } else {
                audioManager.isSpeakerphoneOn = true
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun clearSpeakerOverrideInternal() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                audioManager.isSpeakerphoneOn = false
            }
        }
    }

    companion object {
        fun isBluetoothOutputType(type: Int): Boolean {
            if (type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (type == AudioDeviceInfo.TYPE_BLE_HEADSET || type == AudioDeviceInfo.TYPE_BLE_SPEAKER) {
                    return true
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (type == AudioDeviceInfo.TYPE_BLE_BROADCAST) {
                    return true
                }
            }
            return false
        }

        fun getDeviceTypeLabelAr(type: Int): String {
            return when (type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "بلوتوث عالي الجودة A2DP"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "سماعة بلوتوث SCO"
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "سماعة بلوتوث منخفض الطاقة BLE"
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> "مكبر بلوتوث BLE"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "سماعة الهاتف الخارجية"
                else -> "جهاز صوت لاسلكي"
            }
        }
    }
}
