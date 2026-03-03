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
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.DefaultRenderersFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import android.content.Intent
import android.widget.TextView
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.toColorInt
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter


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
        override fun getMinimumLoadableRetryCount(dataType: Int): Int = Int.MAX_VALUE
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long = 1000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()
        setupUI()
        handleIncomingIntent(intent)
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
            if (fullUrl.contains("|")) {
                val mediaInfo = extractMediaInfo(fullUrl)
                val urlToPlay = mediaInfo["url"] ?: ""
                val userAgent = mediaInfo["userAgent"] ?: "VLC/3.0.0"
                val referer = mediaInfo["referer"] ?: ""
                val drmLicense = mediaInfo["drm"] ?: ""
                initializePlayer(urlToPlay, drmLicense, referer, userAgent)
            } else {
                initializePlayer(fullUrl, "", "", "VLC/3.0.0")
            }
        } else {
            channelsList = intent.getStringArrayListExtra("channelsList")
            currentChannelIndex = intent.getIntExtra("channelIndex", 0)
            loadChannelData()
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
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
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

    @Suppress("unused")
    suspend fun loadChannels(url: String): List<Channel> {
        return withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient()
                val realUrl = decodeBase64Url(url)

                val request = Request.Builder()
                    .url(realUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()

                    val content = response.body?.string()?.trim() ?: ""
                    val channels = mutableListOf<Channel>()
                    var currentName = ""
                    var currentLogo = ""
                    var currentGroup = ""
                    var currentDrm = ""
                    var currentReferer = ""
                    var currentUserAgent = ""

                    content.lines().forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            when {
                                trimmed.startsWith("#EXTINF") -> {
                                    currentName = trimmed.substringAfterLast(",").trim()
                                    currentLogo = Regex("""tvg-logo=["'](.*?)["']""").find(trimmed)?.groupValues?.get(1) ?: ""
                                    currentGroup = Regex("""group-title=["'](.*?)["']""").find(trimmed)?.groupValues?.get(1) ?: ""
                                }
                                trimmed.lowercase().contains("referer") || trimmed.lowercase().contains("referrer") -> {
                                    currentReferer = trimmed.substringAfter("=").trim()
                                }
                                trimmed.lowercase().contains("user-agent") -> {
                                    currentUserAgent = trimmed.substringAfter("=").trim()
                                }
                                trimmed.contains("license_key=") || trimmed.contains("inputstream.adaptive.license_key=") -> {
                                    currentDrm = trimmed.substringAfter("=").trim()
                                }
                                !trimmed.startsWith("#") && (trimmed.startsWith("http") || trimmed.startsWith("rtmp")) -> {
                                    channels.add(
                                        Channel(
                                            name = currentName,
                                            url = trimmed,
                                            logo = currentLogo,
                                            drmLicense = currentDrm,
                                            referer = currentReferer,
                                            userAgent = currentUserAgent,
                                            group = currentGroup
                                        )
                                    )
                                    currentName = ""; currentLogo = ""; currentGroup = ""; currentDrm = ""; currentReferer = ""; currentUserAgent = ""
                                }
                            }
                        }
                    }
                    channels
                }
            } catch (_: Exception) {
                emptyList()
            }
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

        val bandwidthMeter = DefaultBandwidthMeter.Builder(this).build()

        val trackSelectionFactory = AdaptiveTrackSelection.Factory()
        val trackSelector = DefaultTrackSelector(this, trackSelectionFactory)
        trackSelector.parameters = trackSelector.buildUponParameters()
            .setPreferredAudioLanguages("ar", "fr", "en")
            .setPreferredTextLanguage("ar")
            .setSelectUndeterminedTextLanguage(true)
            .setForceHighestSupportedBitrate(false)
            .build()

        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            setEnableDecoderFallback(true)
        }
        val customHeaders = mutableMapOf("Connection" to "keep-alive", "Accept" to "*/*")
        if (referer.isNotEmpty()) customHeaders["Referer"] = referer

        val finalUserAgent = when {
            url.contains("dash") || url.contains(".mpd") ->
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            else -> userAgent.ifEmpty { "VLC/3.0.0" }
        }

        val okHttpFactory = OkHttpDataSource.Factory(getUnsafeOkHttpClient())
            .setUserAgent(finalUserAgent)
            .setDefaultRequestProperties(customHeaders)

        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpFactory)
            .setTransferListener(bandwidthMeter)

        val mediaItemBuilder = MediaItem.Builder().setUri(url.toUri())
        url.lowercase().let { lowerUrl ->
            when {
                lowerUrl.contains(".m3u8") || lowerUrl.contains("#ext-x-stream-inf") ||
                        (lowerUrl.contains(".php") && !lowerUrl.contains("extension=ts")) -> {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                }
                lowerUrl.contains(".mpd") || lowerUrl.contains("dash") || lowerUrl.contains("manifest") -> {
                    mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                }
                lowerUrl.contains(".ts") || lowerUrl.contains("f=ts") || lowerUrl.contains("extension=ts") -> {
                    mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
                }
            }
        }
        if (drmKey.isNotEmpty() && drmKey.contains(":")) {
            try {
                val parts = drmKey.split(":")
                if (parts.size >= 2) {
                    val json = """{"keys":[{"kty":"oct","k":"${parts[1].trim()}","kid":"${parts[0].trim()}"}],"type":"temporary"}"""
                    val base64Key = Base64.encodeToString(json.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                    mediaItemBuilder.setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                            .setLicenseUri("data:application/json;base64,$base64Key")
                            .setMultiSession(true)
                            .setForceDefaultLicenseUri(true)
                            .build()
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory).setLoadErrorHandlingPolicy(errorHandlingPolicy))
            .build().apply {
                playerView.player = this
                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true
                addListener(playerListener)
            }

        setupSubtitleStyle()
    }
    private fun setupSubtitleStyle() {
        val isTelevision = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        playerView.subtitleView?.apply {
            setApplyEmbeddedStyles(false)
            setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, if (isTelevision) 34f else 22f)
            setStyle(CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK, null))
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            bufferingHandler.removeCallbacks(bufferingRunnable)
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) player?.seekToDefaultPosition()
            player?.prepare()
        }
        override fun onPlaybackStateChanged(state: Int) {
            bufferingHandler.removeCallbacks(bufferingRunnable)
            when (state) {
                Player.STATE_BUFFERING -> bufferingHandler.postDelayed(bufferingRunnable, 15000)
                Player.STATE_IDLE -> player?.prepare()
                Player.STATE_ENDED -> { player?.seekToDefaultPosition(); player?.prepare() }
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
            OkHttpClient.Builder().sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager).hostnameVerifier { _, _ -> true }.build()
        } catch (_: Exception) { OkHttpClient() }
    }

    private fun applyImmersiveMode() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun decodeBase64Url(url: String): String {
        return try {
            if (url.isNotEmpty() && !url.startsWith("http") && url.length % 4 == 0) String(Base64.decode(url, Base64.DEFAULT)) else url
        } catch (_: Exception) { url }
    }

    override fun onDestroy() {
        super.onDestroy()
        bufferingHandler.removeCallbacksAndMessages(null)
        uiHandler.removeCallbacksAndMessages(null)
        player?.release()
    }
}
