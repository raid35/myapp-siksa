package com.example.siksa

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.DefaultRenderersFactory
import android.content.Intent
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.toColorInt
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory


@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var channelsList: ArrayList<String>? = null
    private var currentChannelIndex = 0

    private lateinit var btnNext: android.widget.ImageView
    private lateinit var btnPrev: android.widget.ImageView
    private val uiHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideOverlayControls() }

    private val bufferingHandler = Handler(Looper.getMainLooper())
    private val bufferingRunnable = Runnable {
        player?.let {
            it.prepare()
            it.play()
        }
    }

    private val errorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 10
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            return when {
                loadErrorInfo.errorCount < 2 -> 500L
                loadErrorInfo.errorCount < 4 -> 1500L
                else -> 3000L
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SecurityUtils.isSecurityRiskDetected(this)) {
            android.widget.Toast.makeText(
                this,
                "عذراً، لا يمكن التشغيل بوجود تطبيقات VPN أو أدوات تحليل الشبكة أو Root",
                android.widget.Toast.LENGTH_LONG
            ).show()

            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()
        setupUI()
        handleIncomingIntent(intent)
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        updatePlayerViewMode()
        applyImmersiveMode()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent) {
        val dataUri = intent.data
        if (dataUri != null) {
            val fullUrl = dataUri.toString()
            val mediaInfo = extractMediaInfo(fullUrl)

            initializePlayer(
                mediaInfo["url"] ?: "",
                mediaInfo["drm"] ?: "",
                mediaInfo["referer"] ?: "",
                mediaInfo["userAgent"] ?: "VLC/3.0.0"
            )
        }
        else {
            val savedList = ChannelData.list

            if (savedList.isNotEmpty()) {
                channelsList = ArrayList(savedList.map { it.toPlayerString() })
                currentChannelIndex = intent.getIntExtra("channelIndex", 0)
                loadChannelData()
            } else {
                processImmediatePlayback()
            }
        }
    }

    private fun createControlBtn(resId: Int, gravity: Int): android.widget.ImageView {
        val density = resources.displayMetrics.density
        val size = (60 * density).toInt()
        val padding = (12 * density).toInt()

        return android.widget.ImageView(this).apply {
            setImageResource(resId)
            setPadding(padding, padding, padding, padding)
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                this.gravity = gravity
                setMargins(50, 0, 50, 0)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor("#66000000".toColorInt())
                setStroke(2, Color.WHITE)
            }
            visibility = android.view.View.GONE
        }
    }

    private fun showOverlayControls() {
        btnNext.visibility = android.view.View.VISIBLE
        btnPrev.visibility = android.view.View.VISIBLE
        resetHideTimer()
    }

    private fun hideOverlayControls() {
        btnNext.visibility = android.view.View.GONE
        btnPrev.visibility = android.view.View.GONE
    }

    private fun resetHideTimer() {
        uiHandler.removeCallbacks(hideControlsRunnable)
        uiHandler.postDelayed(hideControlsRunnable, 3000)
    }

    private fun setupUI() {
        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            setOnClickListener { showOverlayControls() }
        }

        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            useController = false

            resizeMode = if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
                AspectRatioFrameLayout.RESIZE_MODE_FILL
            } else {
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }

        rootLayout.addView(playerView)
        btnPrev = createControlBtn(android.R.drawable.ic_media_previous, Gravity.START or Gravity.CENTER_VERTICAL)
        btnPrev.setOnClickListener { changeChannel(false); resetHideTimer() }

        btnNext = createControlBtn(android.R.drawable.ic_media_next, Gravity.END or Gravity.CENTER_VERTICAL)
        btnNext.setOnClickListener { changeChannel(true); resetHideTimer() }

        rootLayout.addView(btnPrev)
        rootLayout.addView(btnNext)
        val watermark = TextView(this).apply {
            text = "S"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            alpha = 0.3f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor("#22000000".toColorInt())
                setStroke(1, Color.WHITE)
            }
            val size = (35 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins(40, 0, 0, 30)
            }
        }
        rootLayout.addView(watermark)

        setContentView(rootLayout)
        showOverlayControls()
    }
    private fun loadChannelData() {
        val list = channelsList
        if (!list.isNullOrEmpty() && currentChannelIndex in list.indices) {
            val rawData = list[currentChannelIndex]
            val mediaInfo = extractMediaInfo(rawData)

            initializePlayer(
                mediaInfo["url"] ?: "",
                mediaInfo["drm"] ?: "",
                mediaInfo["referer"] ?: "",
                mediaInfo["userAgent"] ?: ""
            )
        } else {
            processImmediatePlayback()
        }
    }
    private fun processImmediatePlayback() {
        val streamUrl = intent.getStringExtra("streamUrl") ?: ""
        val drmLicense = intent.getStringExtra("drmLicense") ?: ""
        val referer = intent.getStringExtra("referer") ?: ""
        val userAgent = intent.getStringExtra("userAgent") ?: ""

        if (streamUrl.isNotEmpty()) {
            initializePlayer(streamUrl, drmLicense, referer, userAgent)
        }
    }

    private fun initializePlayer(url: String, drmKey: String, referer: String, userAgent: String) {
        player?.release()

        if (!StreamTypeConfig.isValidStreamUrl(url)) {
            android.util.Log.e("PlayerActivity", "[v0] Invalid stream URL: $url")
            return
        }

        val streamConfig = StreamTypeConfig.detectStreamType(url)
        val bandwidthMeter = DefaultBandwidthMeter.Builder(this).build()

        val trackSelector = DefaultTrackSelector(this, AdaptiveTrackSelection.Factory())
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setPreferredAudioLanguages("ar", "fr", "en")
            .setPreferredTextLanguage("ar")
            .build()

        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
        }
        val customHeaders = mutableMapOf<String, String>()
        customHeaders.putAll(StreamTypeConfig.getOptimalHeaders(streamConfig.type))

        var origin = referer
        var currentReferer = referer
        if (url.lowercase().contains("shahid") || url.lowercase().contains("edgenext")) {
            if (origin.isEmpty()) origin = "https://shahid.mbc.net"
            if (currentReferer.isEmpty()) currentReferer = "https://shahid.mbc.net/"
        }

        if (currentReferer.isNotEmpty()) customHeaders["Referer"] = currentReferer
        if (origin.isNotEmpty()) customHeaders["Origin"] = origin

        val finalUserAgent = userAgent.ifEmpty { StreamTypeConfig.getOptimalUserAgent(streamConfig.type) }

        val okHttpFactory = OkHttpDataSource.Factory(getOptimizedHttpClient(streamConfig.type))
            .setUserAgent(finalUserAgent)
            .setDefaultRequestProperties(customHeaders)

        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpFactory)
            .setTransferListener(bandwidthMeter)

        val extractorsFactory = DefaultExtractorsFactory().apply {
            setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES)
            setTsExtractorTimestampSearchBytes(225600)
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(errorHandlingPolicy)
            .setLoadErrorHandlingPolicy(DashErrorHandlingPolicy())
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(url.toUri())
            .setMimeType(streamConfig.mimeType)

        if (drmKey.isNotEmpty()) {
            try {
                android.util.Log.d("PlayerActivity", "[v0] Processing DRM configuration...")
                val licenseUri = parseClearKeyDRM(drmKey)

                if (licenseUri != null) {
                    mediaItemBuilder.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                            .setLicenseUri(licenseUri)
                            .build()
                    )
                    android.util.Log.d("PlayerActivity", "[v0] ✓ Applied ClearKey DRM configuration")
                } else if (drmKey.contains(":") && !drmKey.startsWith("http")) {
                    val parts = drmKey.split(":", limit = 2)
                    if (parts.size == 2) {
                        val kid = parts[0].trim()
                        val key = parts[1].trim()

                        val clearKeyJson = """
                        {
                          "keys": [
                            {
                              "kty": "oct",
                              "k": "${base64UrlEncode(key)}",
                              "kid": "${base64UrlEncode(kid)}"
                            }
                          ],
                          "type": "temporary"
                        }
                    """.trimIndent()

                        mediaItemBuilder.setDrmConfiguration(
                            MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                                .setLicenseUri("data:application/json,$clearKeyJson")
                                .build()
                        )
                        android.util.Log.d("PlayerActivity", "[v0] ✓ Applied Manual ClearKey DRM from kid:key")
                    }
                } else if (drmKey.startsWith("http")) {
                    mediaItemBuilder.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(drmKey)
                            .setMultiSession(true)
                            .setForceDefaultLicenseUri(true)
                            .build()
                    )
                    android.util.Log.d("PlayerActivity", "[v0] ✓ Applied Widevine DRM configuration")
                }
            } catch (e: Exception) {
                android.util.Log.e("PlayerActivity", "[v0] DRM Config Error: ${e.message}")
                e.printStackTrace()
            }
        }

        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setMediaSourceFactory(mediaSourceFactory)
            .build().apply {
                playerView.player = this
                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true
                addListener(playerListener)
            }
    }

    // Optimized HTTP client with timeout based on stream type
    private fun getOptimizedHttpClient(streamType: StreamTypeConfig.StreamType): OkHttpClient {
        val timeout = StreamTypeConfig.getOptimalTimeout(streamType)
        val timeoutSeconds = timeout / 1000L
        return getUnsafeOkHttpClient().newBuilder()
            .connectTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    private class DashErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy() {
        override fun getMinimumLoadableRetryCount(dataType: Int): Int = 8
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            return when {
                loadErrorInfo.exception is java.io.IOException &&
                        loadErrorInfo.exception?.message?.contains("timeout") == true -> 1500L
                loadErrorInfo.errorCount < 2 -> 800L
                loadErrorInfo.errorCount < 4 -> 1500L
                loadErrorInfo.errorCount < 6 -> 3000L
                else -> 5000L
            }
        }
    }
    private fun parseAdvancedDRM(drmInfo: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        try {
            val trimmed = drmInfo.trim()
            if (trimmed.contains("inputstream.adaptive.license_key=")) {
                val licenseKey = trimmed.substringAfter("inputstream.adaptive.license_key=")
                    .split("\n", "#")
                    .firstOrNull()?.trim() ?: ""

                if (licenseKey.isNotEmpty()) {
                    result["license_key"] = licenseKey
                    result["drm_type"] = "clearkey"
                    android.util.Log.d("PlayerActivity", "[v0] Extracted KODIPROP license key: ${licenseKey.take(30)}...")
                }
            }
            if (trimmed.contains("http-drm-scheme=")) {
                val drmScheme = trimmed.substringAfter("http-drm-scheme=")
                    .split(" ", "\n", "#")
                    .firstOrNull()?.trim() ?: ""

                if (drmScheme.isNotEmpty()) {
                    result["drm_scheme"] = drmScheme
                    android.util.Log.d("PlayerActivity", "[v0] Extracted DRM scheme: $drmScheme")
                }
            }
            if (trimmed.contains("drm-info=clearkey|") || trimmed.contains("drm-info=clearkey:")) {
                val drmPart = trimmed.substringAfter("drm-info=clearkey").trim()
                    .removePrefix("|").removePrefix(":")
                    .split("\n", " ", "#")
                    .firstOrNull()?.trim() ?: ""

                if (drmPart.isNotEmpty()) {
                    result["license_key"] = drmPart
                    result["drm_type"] = "clearkey"
                    android.util.Log.d("PlayerActivity", "[v0] Extracted drm-info license key")
                }
            }

        } catch (e: Exception) {
            android.util.Log.e("PlayerActivity", "[v0] Error parsing advanced DRM: ${e.message}")
        }

        return result
    }

    private fun parseClearKeyDRM(drmKey: String): String? {
        return try {
            val trimmed = drmKey.trim()
            val advancedDRM = parseAdvancedDRM(trimmed)
            if (advancedDRM.isNotEmpty()) {
                val licenseKey = advancedDRM["license_key"] ?: ""
                if (licenseKey.isNotEmpty()) {
                    return buildClearKeyLicense(licenseKey)
                }
            }

            android.util.Log.d("PlayerActivity", "[v0] Parsing DRM Key - Format: ${if (trimmed.contains(":")) "hex:hex" else if (trimmed.startsWith("{")) "JSON" else "Unknown"}")
            if (trimmed.contains(":") && !trimmed.startsWith("{")) {
                val parts = trimmed.split(":", limit = 2)
                if (parts.size == 2) {
                    val kid = parts[0].trim()
                    val k = parts[1].trim()
                    if (!isValidHex(kid) || !isValidHex(k)) {
                        android.util.Log.e("PlayerActivity", "[v0] Invalid hex in DRM key: kid=$kid, k=$k")
                        return null
                    }
                    val base64EncodedKey = Base64.encodeToString(hexStringToByteArray(k), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                    val base64EncodedKid = Base64.encodeToString(hexStringToByteArray(kid), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

                    val json = """{"keys":[{"kty":"oct","k":"$base64EncodedKey","kid":"$base64EncodedKid"}],"type":"temporary"}"""
                    val base64License = Base64.encodeToString(json.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

                    android.util.Log.d("PlayerActivity", "[v0] ✓ ClearKey DRM prepared (hex:hex format)")
                    return "data:application/json;base64,$base64License"
                }
            }
            if (trimmed.startsWith("{")) {
                val base64License = Base64.encodeToString(trimmed.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                android.util.Log.d("PlayerActivity", "[v0] ✓ ClearKey DRM prepared (JSON format)")
                return "data:application/json;base64,$base64License"
            }
            if (isBase64(trimmed)) {
                try {
                    val decoded = String(Base64.decode(trimmed, Base64.URL_SAFE or Base64.DEFAULT))

                    if (decoded.contains("drm-info=clearkey") && decoded.contains("|")) {
                        val drmPart = decoded.substringAfter("drm-info=clearkey|").split("&").firstOrNull() ?: ""
                        if (drmPart.contains(":")) {
                            android.util.Log.d("PlayerActivity", "[v0] Extracting DRM from base64 URL: $drmPart")
                            return parseClearKeyDRM(drmPart)
                        }
                    }

                    android.util.Log.d("PlayerActivity", "[v0] Base64 URL decoded, no DRM info found inside")
                } catch (e: Exception) {
                    android.util.Log.d("PlayerActivity", "[v0] Failed to decode base64 for DRM extraction: ${e.message}")
                }
            }

            android.util.Log.w("PlayerActivity", "[v0] ⚠ Unknown DRM format: ${trimmed.take(50)}")
            null
        } catch (e: Exception) {
            android.util.Log.e("PlayerActivity", "[v0] ✗ ClearKey DRM parse failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    private fun buildClearKeyLicense(licenseKey: String): String? {
        return try {
            val trimmed = licenseKey.trim()
            if (trimmed.contains(":") && !trimmed.startsWith("{")) {
                val parts = trimmed.split(":", limit = 2)
                if (parts.size == 2) {
                    val kid = parts[0].trim()
                    val key = parts[1].trim()

                    if (isValidHex(kid) && isValidHex(key)) {
                        val base64Key = Base64.encodeToString(hexStringToByteArray(key), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                        val base64Kid = Base64.encodeToString(hexStringToByteArray(kid), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

                        val json = """{"keys":[{"kty":"oct","k":"$base64Key","kid":"$base64Kid"}],"type":"temporary"}"""
                        val license = Base64.encodeToString(json.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

                        android.util.Log.d("PlayerActivity", "[v0] ✓ Built ClearKey license from kid:key")
                        return "data:application/json;base64,$license"
                    }
                }
            }
            if (trimmed.startsWith("{")) {
                val license = Base64.encodeToString(trimmed.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                android.util.Log.d("PlayerActivity", "[v0] ✓ Built ClearKey license from JSON")
                return "data:application/json;base64,$license"
            }

            null
        } catch (e: Exception) {
            android.util.Log.e("PlayerActivity", "[v0] Error building ClearKey license: ${e.message}")
            null
        }
    }

    /**
     * Convert hex string to byte array
     */
    fun extractM3uMediaInfo(m3uLine: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        try {
            if (m3uLine.contains("#EXTINF")) {
                val extinf = m3uLine.substringAfter("#EXTINF:").split("\n").firstOrNull() ?: ""
                result["extinf"] = extinf
            }
            val lines = m3uLine.split("\n")
            var streamUrl = ""
            var drmInfo = ""

            for (line in lines) {
                when {
                    line.startsWith("#EXTVLCOPT") -> {
                        drmInfo += "$line\n"
                    }
                    line.startsWith("#KODIPROP") -> {
                        drmInfo += "$line\n"
                    }
                    line.startsWith("http") -> {
                        streamUrl = line.trim()
                    }
                }
            }

            if (streamUrl.isNotEmpty()) result["url"] = streamUrl
            if (drmInfo.isNotEmpty()) result["drm"] = drmInfo

            android.util.Log.d("PlayerActivity", "[v0] Extracted M3U media info - URL: ${streamUrl.take(50)}, DRM: ${drmInfo.isNotEmpty()}")
        } catch (e: Exception) {
            android.util.Log.e("PlayerActivity", "[v0] Error extracting M3U media info: ${e.message}")
        }

        return result
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        for (i in 0 until len step 2) {
            data[i / 2] = ((s[i].toString().toInt(16) shl 4) + s[i + 1].toString().toInt(16)).toByte()
        }
        return data
    }

    /**
     * Validate if string is valid hexadecimal
     */
    private fun isValidHex(s: String): Boolean {
        return s.matches(Regex("[0-9a-fA-F]+"))
    }

    /**
     * Check if string is base64 encoded
     */
    private fun isBase64(s: String): Boolean {
        return try {
            Base64.decode(s, Base64.DEFAULT)
            true
        } catch (_: Exception) {
            false
        }
    }
    private fun base64UrlEncode(hexString: String): String {
        return try {
            val bytes = hexStringToByteArray(hexString.replace(" ", ""))
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("PlayerActivity", "Encoding error: ${e.message}")
            ""
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            bufferingHandler.removeCallbacks(bufferingRunnable)
            val errorMsg = error.message ?: "Unknown error"
            android.util.Log.e("PlayerActivity", "[v0] PlaybackException Code: ${error.errorCode}, Message: $errorMsg")
            error.printStackTrace()

            when {
                // DRM-related errors
                error.errorCode == PlaybackException.ERROR_CODE_DRM_UNSPECIFIED ||
                        errorMsg.contains("DRM", ignoreCase = true) ||
                        errorMsg.contains("license", ignoreCase = true) -> {
                    android.util.Log.e("PlayerActivity", "[v0] ❌ DRM Error: $errorMsg")
                    // DRM errors - usually need manual intervention
                    bufferingHandler.postDelayed({
                        player?.prepare()
                    }, 3000)
                }

                error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                    android.util.Log.d("PlayerActivity", "[v0] Behind live window, seeking to default position")
                    player?.seekToDefaultPosition()
                    player?.prepare()
                }

                error.errorCode == PlaybackException.ERROR_CODE_UNSPECIFIED ||
                        error.errorCode == PlaybackException.ERROR_CODE_REMOTE_ERROR ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> {
                    // Network/IO issues - retry with backoff
                    android.util.Log.d("PlayerActivity", "[v0] Network error, retrying in 2 seconds")
                    bufferingHandler.postDelayed({
                        player?.prepare()
                    }, 2000)
                }

                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> {
                    // DASH manifest parsing failed - try again
                    android.util.Log.e("PlayerActivity", "[v0] Manifest parsing failed, retrying...")
                    bufferingHandler.postDelayed({
                        player?.prepare()
                    }, 1500)
                }

                else -> {
                    android.util.Log.d("PlayerActivity", "[v0] Other error (${error.errorCode}), retrying playback")
                    bufferingHandler.postDelayed({
                        player?.prepare()
                    }, 1000)
                }
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_BUFFERING -> {
                    android.util.Log.d("PlayerActivity", "[v0] Buffering content...")
                    bufferingHandler.removeCallbacks(bufferingRunnable)
                    // Only timeout if buffering takes too long
                    bufferingHandler.postDelayed(bufferingRunnable, 30000)
                }
                Player.STATE_IDLE -> {
                    android.util.Log.d("PlayerActivity", "[v0] Player idle, preparing...")
                    player?.prepare()
                }
                Player.STATE_ENDED -> {
                    android.util.Log.d("PlayerActivity", "[v0] Playback ended, restarting...")
                    player?.seekToDefaultPosition()
                    player?.prepare()
                }
                Player.STATE_READY -> {
                    android.util.Log.d("PlayerActivity", "[v0] Playback ready")
                    bufferingHandler.removeCallbacks(bufferingRunnable)
                }
                else -> {}
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    seekPlayer(15000)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    seekPlayer(-15000)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    changeChannel(true)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    changeChannel(false)
                    return true
                }

                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    player?.let { if (it.isPlaying) it.pause() else it.play() }
                    return true
                }

                KeyEvent.KEYCODE_BACK -> {
                    finish()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun seekPlayer(offset: Long) {
        player?.let {
            val duration = it.duration
            val newPosition = (it.currentPosition + offset).coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
            it.seekTo(newPosition)
        }
    }

    private fun changeChannel(next: Boolean) {
        val list = channelsList ?: return
        if (list.isEmpty()) return
        currentChannelIndex = if (next) (currentChannelIndex + 1) % list.size else if (currentChannelIndex > 0) currentChannelIndex - 1 else list.size - 1
        player?.stop(); player?.clearMediaItems()
        loadChannelData()
    }

    private fun extractMediaInfo(fullLine: String): Map<String, String> {
        val info = mutableMapOf<String, String>()
        if (fullLine.contains("#EXTINF") || fullLine.contains("#EXTVLCOPT") || fullLine.contains("#KODIPROP")) {
            android.util.Log.d("PlayerActivity", "[v0] Detected M3U format with advanced tags")
            val m3uInfo = extractM3uMediaInfo(fullLine)

            if (m3uInfo.isNotEmpty()) {
                if (m3uInfo.containsKey("url")) {
                    info["url"] = m3uInfo["url"]!!
                }
                if (m3uInfo.containsKey("drm")) {
                    val drmInfo = m3uInfo["drm"]!!
                    val drmKey = parseAdvancedDRM(drmInfo)["license_key"]
                    if (drmKey != null) {
                        info["drm"] = drmKey
                        android.util.Log.d("PlayerActivity", "[v0] Extracted DRM from M3U tags")
                    }
                }
            }
        }
        if (info["url"].isNullOrEmpty()) {
            val parts = fullLine.split("|")

            if (parts.isNotEmpty()) {
                info["url"] = parts[0].trim()
                for (i in 1 until parts.size) {
                    val part = parts[i].trim()
                    when {
                        part.startsWith("userAgent=", ignoreCase = true) ->
                            info["userAgent"] = part.substringAfter("=").trim()

                        part.startsWith("referer=", ignoreCase = true) ->
                            info["referer"] = part.substringAfter("=").trim()
                        part.startsWith("drm=", ignoreCase = true) || part.startsWith("license_key=", ignoreCase = true) ->
                            info["drm"] = part.substringAfter("=").trim()
                    }
                }
            }
        }

        return info
    }

    @SuppressLint("CustomX509TrustManager")
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        return try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                @SuppressLint("TrustAllX509TrustManager")
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("SSL").apply { init(null, trustAllCerts, java.security.SecureRandom()) }
            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        } catch (_: Exception) {
            OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    private fun applyImmersiveMode() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    private fun updatePlayerViewMode() {
        val orientation = resources.configuration.orientation
        if (orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        } else {
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        bufferingHandler.removeCallbacksAndMessages(null)
        uiHandler.removeCallbacksAndMessages(null)
        player?.release()
    }
}
