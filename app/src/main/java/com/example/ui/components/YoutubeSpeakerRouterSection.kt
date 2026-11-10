package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SpeakerPhone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.service.AudioRoutingSnapshot
import com.example.service.YoutubeSpeakerMode
import com.example.ui.SplitLayoutMode
import com.example.ui.theme.ConsoleCardBorder
import com.example.ui.theme.ConsoleSurface
import com.example.ui.theme.ConsoleSurfaceElevated
import com.example.ui.theme.SpeakerAmber
import com.example.ui.theme.SpeakerContainer
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YoutubeSpeakerRouterSection(
    routing: AudioRoutingSnapshot,
    layoutMode: SplitLayoutMode,
    youtubeWebGainPercent: Int,
    onToggleSpeakerOverride: (Boolean) -> Unit,
    onSelectYoutubeMode: (YoutubeSpeakerMode) -> Unit,
    onToggleExpandYoutube: () -> Unit,
    onUrlChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isWebViewActive by remember { mutableStateOf(false) }
    var pendingUrl by remember { mutableStateOf("https://m.youtube.com") }
    var loadProgress by remember { mutableIntStateOf(100) }
    var webViewErrorText by remember { mutableStateOf<String?>(null) }

    // Cleanly release WebView when leaving composition to prevent memory leaks & GPU rendernode errors
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
                webViewRef = null
            }
        }
    }

    // Apply WebAudio/HTML5 Video volume gain whenever slider updates
    LaunchedEffect(youtubeWebGainPercent, routing.isPhoneSpeakerMuted, isWebViewActive) {
        if (!isWebViewActive) return@LaunchedEffect
        val targetVol = if (routing.isPhoneSpeakerMuted) {
            0.0f
        } else {
            (youtubeWebGainPercent / 100f).coerceIn(0f, 1f)
        }
        runCatching {
            webViewRef?.evaluateJavascript(
                """
                (function() {
                    var mediaEls = document.querySelectorAll('video, audio');
                    for (var i = 0; i < mediaEls.length; i++) {
                        mediaEls[i].volume = $targetVol;
                        mediaEls[i].muted = ${routing.isPhoneSpeakerMuted};
                    }
                })();
                """.trimIndent(),
                null
            )
        }
    }

    fun openOrNavigateWebView(url: String) {
        pendingUrl = url
        webViewErrorText = null
        onUrlChanged(url)
        if (!isWebViewActive) {
            isWebViewActive = true
        } else {
            runCatching { webViewRef?.loadUrl(url) }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, SpeakerAmber.copy(alpha = 0.55f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = ConsoleSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header + Toggle Switch "توجيه صوت الموبايل للسماعة الخارجية"
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
                            .background(SpeakerContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SpeakerPhone,
                            contentDescription = "سماعة الهاتف الخارجية",
                            tint = SpeakerAmber
                        )
                    }
                    Column {
                        Text(
                            text = "قسم توجيه صوت اليوتيوب (سماعة الهاتف)",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "توجيه صوت الموبايل للسماعة الخارجية بينما يستمر البودكاست بالبلوتوث",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpeakerAmber
                        )
                    }
                }

                Switch(
                    checked = routing.isPhoneSpeakerOverrideEnabled,
                    onCheckedChange = onToggleSpeakerOverride,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = SpeakerAmber
                    ),
                    modifier = Modifier.testTag("phone_speaker_override_switch")
                )
            }

            // Mode A vs Mode B Selector Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = routing.youtubeSpeakerMode == YoutubeSpeakerMode.MODE_B_EMBEDDED_WEBVIEW_SPEAKER,
                    onClick = { onSelectYoutubeMode(YoutubeSpeakerMode.MODE_B_EMBEDDED_WEBVIEW_SPEAKER) },
                    label = { Text("الوضع B: مشغل يوتيوب المدمج") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SpeakerContainer,
                        selectedLabelColor = SpeakerAmber
                    ),
                    modifier = Modifier.testTag("mode_b_embedded_youtube_chip")
                )

                FilterChip(
                    selected = routing.youtubeSpeakerMode == YoutubeSpeakerMode.MODE_A_GLOBAL_SPEAKER_OVERRIDE,
                    onClick = { onSelectYoutubeMode(YoutubeSpeakerMode.MODE_A_GLOBAL_SPEAKER_OVERRIDE) },
                    label = { Text("الوضع A: توجيه عام للنظام") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SpeakerContainer,
                        selectedLabelColor = SpeakerAmber
                    ),
                    modifier = Modifier.testTag("mode_a_global_speaker_chip")
                )
            }

            // Mode A Helper Card when selected
            if (routing.youtubeSpeakerMode == YoutubeSpeakerMode.MODE_A_GLOBAL_SPEAKER_OVERRIDE) {
                Surface(
                    color = ConsoleSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ConsoleCardBorder, RoundedCornerShape(14.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "الوضع A: توجيه السماعة الخارجية العام + ثبات البلوتوث",
                            style = MaterialTheme.typography.titleSmall,
                            color = SpeakerAmber
                        )
                        Text(
                            text = "يقوم هذا الوضع بتثبيت مشغل البودكاست حصرياً على عنوان سماعة البلوتوث (setPreferredAudioDevice) مع إجبار مسار الصوت العام على السماعة المدمجة للهاتف. يمكنك أيضاً استخدام المشغل المدمج بالأسفل في أي وقت.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Button(
                            onClick = {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com")).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpeakerAmber),
                            modifier = Modifier.testTag("open_external_youtube_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("فتح تطبيق يوتيوب الخارجي (مع بقاء البودكاست بالخلفية)", color = Color.Black)
                        }
                    }
                }
            }

            // Embedded YouTube Navigation & Search Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ابحث في يوتيوب مباشرة...", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            if (searchQuery.isNotBlank()) {
                                val encoded = Uri.encode(searchQuery.trim())
                                openOrNavigateWebView("https://m.youtube.com/results?search_query=$encoded")
                            }
                        }
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (searchQuery.isNotBlank()) {
                                    val encoded = Uri.encode(searchQuery.trim())
                                    openOrNavigateWebView("https://m.youtube.com/results?search_query=$encoded")
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "بحث في يوتيوب",
                                tint = SpeakerAmber
                            )
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("youtube_search_input")
                )

                IconButton(
                    onClick = { openOrNavigateWebView("https://m.youtube.com") },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ConsoleSurfaceElevated)
                        .testTag("youtube_home_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "الرئيسية ليوتيوب",
                        tint = SpeakerAmber
                    )
                }

                IconButton(
                    onClick = {
                        runCatching {
                            if (webViewRef?.canGoBack() == true) {
                                webViewRef?.goBack()
                            }
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ConsoleSurfaceElevated)
                        .testTag("youtube_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "رجوع في يوتيوب",
                        tint = TextPrimary
                    )
                }

                IconButton(
                    onClick = {
                        if (isWebViewActive) {
                            runCatching { webViewRef?.reload() }
                        } else {
                            openOrNavigateWebView(pendingUrl)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ConsoleSurfaceElevated)
                        .testTag("youtube_reload_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "تحديث يوتيوب",
                        tint = TextPrimary
                    )
                }

                IconButton(
                    onClick = onToggleExpandYoutube,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SpeakerContainer)
                        .testTag("youtube_expand_button")
                ) {
                    Icon(
                        imageVector = if (layoutMode == SplitLayoutMode.YOUTUBE_EXPANDED) {
                            Icons.Default.FullscreenExit
                        } else {
                            Icons.Default.Fullscreen
                        },
                        contentDescription = "تكبير أو تصغير شاشة يوتيوب",
                        tint = SpeakerAmber
                    )
                }
            }

            // Quick Topic Shortcuts for YouTube
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val quickTopics = listOf(
                    "الصفحة الرئيسية" to "https://m.youtube.com",
                    "مباشر وأخبار" to "https://m.youtube.com/results?search_query=live+news+arabic",
                    "وثائقيات" to "https://m.youtube.com/results?search_query=وثائقي",
                    "قرآن كريم" to "https://m.youtube.com/results?search_query=قرآن+كريم",
                    "ملخصات مباريات" to "https://m.youtube.com/results?search_query=ملخص+مباريات+اليوم"
                )
                quickTopics.forEach { (label, url) ->
                    AssistChip(
                        onClick = { openOrNavigateWebView(url) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = ConsoleSurfaceElevated,
                            labelColor = TextPrimary
                        )
                    )
                }
            }

            if (isWebViewActive && loadProgress in 1..99) {
                LinearProgressIndicator(
                    progress = { loadProgress / 100f },
                    color = SpeakerAmber,
                    trackColor = ConsoleSurfaceElevated,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Embedded m.youtube.com WebView Container (On-Demand to save RAM on vivo Y05 & avoid cold-start GPU rendernode issues)
            val webViewHeight = if (layoutMode == SplitLayoutMode.YOUTUBE_EXPANDED) 520.dp else 340.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(webViewHeight)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, ConsoleCardBorder, RoundedCornerShape(16.dp))
                    .background(ConsoleSurfaceElevated)
                    .testTag("youtube_embedded_webview_box")
            ) {
                if (!isWebViewActive || webViewErrorText != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(SpeakerContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleFilled,
                                contentDescription = null,
                                tint = SpeakerAmber,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = webViewErrorText ?: "مشغل يوتيوب المدمج جاهز للتشغيل على سماعة الهاتف",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "تم تفعيل وضع توفير ذاكرة RAM (4GB) لجهاز vivo Y05. اضغط أدناه لفتح يوتيوب داخل الشاشة مع توجيه صوته للسماعة الخارجية بينما يستمر البودكاست في سماعة البلوتوث.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { openOrNavigateWebView(pendingUrl) },
                                colors = ButtonDefaults.buttonColors(containerColor = SpeakerAmber),
                                modifier = Modifier.testTag("launch_embedded_youtube_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartDisplay,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تشغيل شاشة يوتيوب المدمجة الآن", color = Color.Black)
                            }

                            OutlinedButton(
                                onClick = {
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com")).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                            ) {
                                Text("فتح تطبيق يوتيوب", color = SpeakerAmber)
                            }
                        }
                    }
                } else {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                // Prevent DRM rendernode errors on software-rendered virtual GPUs
                                if (isLikelySoftwareEmulator()) {
                                    setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                                }
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    loadsImagesAutomatically = true
                                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress
                                    }
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        return if (url.startsWith("http://") || url.startsWith("https://")) {
                                            onUrlChanged(url)
                                            false
                                        } else {
                                            true
                                        }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        val targetVol = if (routing.isPhoneSpeakerMuted) {
                                            0.0f
                                        } else {
                                            (youtubeWebGainPercent / 100f).coerceIn(0f, 1f)
                                        }
                                        runCatching {
                                            view?.evaluateJavascript(
                                                """
                                                (function() {
                                                    var mediaEls = document.querySelectorAll('video, audio');
                                                    for (var i = 0; i < mediaEls.length; i++) {
                                                        mediaEls[i].volume = $targetVol;
                                                    }
                                                })();
                                                """.trimIndent(),
                                                null
                                            )
                                        }
                                    }

                                    override fun onRenderProcessGone(
                                        view: WebView?,
                                        detail: RenderProcessGoneDetail?
                                    ): Boolean {
                                        // Handle WebView renderer termination gracefully without crashing the app
                                        runCatching {
                                            view?.destroy()
                                        }
                                        webViewRef = null
                                        isWebViewActive = false
                                        webViewErrorText = "تم تحرير ذاكرة متصفح يوتيوب لحماية استمرار البودكاست. اضغط لإعادة الفتح."
                                        return true
                                    }
                                }
                                runCatching {
                                    loadUrl(pendingUrl)
                                }
                                webViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Top Bar Overlay inside WebView: Speaker route indicator + Close WebView to save RAM button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SmartDisplay,
                                    contentDescription = null,
                                    tint = SpeakerAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "مخرج الصوت: سماعة الهاتف الخارجية",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpeakerAmber
                                )
                            }
                        }

                        Surface(
                            color = Color.Black.copy(alpha = 0.78f),
                            shape = RoundedCornerShape(10.dp),
                            onClick = {
                                runCatching {
                                    webViewRef?.stopLoading()
                                    webViewRef?.destroy()
                                    webViewRef = null
                                }
                                isWebViewActive = false
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = "إيقاف شاشة يوتيوب لتوفير الذاكرة",
                                    tint = SpeakerAmber,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "إغلاق الشاشة لتوفير RAM",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isLikelySoftwareEmulator(): Boolean {
    return (Build.FINGERPRINT.startsWith("generic") ||
        Build.FINGERPRINT.lowercase().contains("emulator") ||
        Build.MODEL.contains("sdk_gphone") ||
        Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK built for") ||
        Build.HARDWARE.contains("goldfish") ||
        Build.HARDWARE.contains("ranchu") ||
        Build.HARDWARE.contains("cutf_cvm") ||
        Build.PRODUCT.contains("sdk_gphone") ||
        Build.PRODUCT.contains("vbox86p") ||
        Build.PRODUCT.contains("emulator"))
}
