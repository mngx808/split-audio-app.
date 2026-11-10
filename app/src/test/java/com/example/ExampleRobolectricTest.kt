package com.example

import android.content.Context
import android.media.AudioDeviceInfo
import androidx.test.core.app.ApplicationProvider
import com.example.service.AudioRouteController
import com.example.ui.SplitAudioViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read Arabic app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("موجه الصوت المزدوج", appName)
    }

    @Test
    fun `verify bluetooth output device classification and duration formatting`() {
        assertTrue(AudioRouteController.isBluetoothOutputType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertTrue(AudioRouteController.isBluetoothOutputType(AudioDeviceInfo.TYPE_BLE_HEADSET))
        assertFalse(AudioRouteController.isBluetoothOutputType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals("01:30", SplitAudioViewModel.formatDurationAr(90_000L))
    }
}
