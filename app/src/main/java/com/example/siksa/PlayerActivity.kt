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
import androidx.media3.exoplayer.DefaultLoadControl
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

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var channelsList: ArrayList<String>? = null
    private var currentChannelIndex = 0

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

    private fun setupUI() {
        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
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

        // إضافة العلامة المائية - تم تحديد النوع TextView صراحةً هنا
        val watermark = TextView(this).apply {
            text = "S"
            textSize = 14f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            alpha = 0.3f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                // استخدام KTX: String.toColorInt()
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
    }
    private fun loadChannelData() {
        val list = channelsList
        if (!list.isNullOrEmpty() && currentChannelIndex in list.indices) {
            val rawData = list[currentChannelIndex]
            val mediaInfo = extractMediaInfo(rawData)

            val urlToPlay = mediaInfo["url"] ?: ""
            val userAgent = mediaInfo["userAgent"] ?: "VLC/3.0.0"
            val referer = mediaInfo["referer"] ?: ""
            val drmLicense = mediaInfo["drm"] ?: ""

            if (urlToPlay.isNotEmpty()) {
                initializePlayer(urlToPlay, drmLicense, referer, userAgent)
            }
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
                                trimmed.contains("referrer=") || trimmed.contains("referer=") -> {
                                    currentReferer = trimmed.substringAfter("=").trim()
                                }
                                trimmed.contains("user-agent=") -> {
                                    currentUserAgent = trimmed.substringAfter("=").trim()
                                }

                                // استخراج DRM
                                trimmed.contains("license_key=") -> {
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
        val currentIntent = intent
        val streamUrl = currentIntent.getStringExtra("streamUrl") ?: ""
        val drmLicense = currentIntent.getStringExtra("drmLicense") ?: ""
        val referer = currentIntent.getStringExtra("referer") ?: ""
        val userAgent = currentIntent.getStringExtra("userAgent") ?: "VLC/3.0.0"

        if (streamUrl.isNotEmpty()) {
            initializePlayer(streamUrl, drmLicense, referer, userAgent)
        }
    }

    private fun initializePlayer(url: String, drmKey: String, referer: String, userAgent: String) {
        player?.release()
        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguages("ar", "fr", "en")
                .setPreferredTextLanguage("ar")
                .setSelectUndeterminedTextLanguage(true)
                .setForceHighestSupportedBitrate(false)
                .build()
        }

        val renderersFactory = DefaultRenderersFactory(this).apply {
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }
        val isTelevision = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK) == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        val fontSize = if (isTelevision) 34f else 22f
        val captionStyle = CaptionStyleCompat(Color.WHITE, Color.TRANSPARENT, Color.TRANSPARENT, CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW, Color.BLACK, null)

        playerView.subtitleView?.let {
            it.setApplyEmbeddedStyles(false)
            it.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, fontSize)
            it.setStyle(captionStyle)
        }
        val customHeaders = mutableMapOf("Connection" to "keep-alive", "Accept" to "*/*")
        if (referer.isNotEmpty()) {
            customHeaders["Referer"] = referer
        } else if (url.contains("on-tv.site") || url.contains("hilal1.sbs")) {
            customHeaders["Referer"] = "https://${url.toUri().host}/"
        }
        val okHttpFactory = OkHttpDataSource.Factory(getUnsafeOkHttpClient())
            .setUserAgent(userAgent.ifEmpty { "VLC/3.0.0" })
            .setDefaultRequestProperties(customHeaders)

        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpFactory)
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(url.toUri())

        val lowerUrl = url.lowercase()
        when {
            lowerUrl.contains("#ext-x-stream-inf") || lowerUrl.contains(".m3u8") || lowerUrl.contains("action=stream") -> {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            lowerUrl.contains("extension=ts") || lowerUrl.contains("f=ts") || lowerUrl.contains(".ts") -> {
                mediaItemBuilder.setMimeType(MimeTypes.VIDEO_MP2T)
            }
            lowerUrl.contains(".mpd") -> {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            }
            lowerUrl.contains(".php") -> {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
        }
        if (drmKey.isNotEmpty() && drmKey.contains(":")) {
            try {
                val parts = drmKey.split(":")
                val json = """{"keys":[{"kty":"oct","k":"${parts[1].trim()}","kid":"${parts[0].trim()}"}],"type":"temporary"}"""
                val base64Key = Base64.encodeToString(json.toByteArray(), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
                mediaItemBuilder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                        .setLicenseUri("data:application/json;base64,$base64Key").build()
                )
            } catch (e: Exception) { e.printStackTrace() }
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000,
                60000,
                2000,
                4000
            )
            .setBackBuffer(10000, true)
            .build()
        player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(dataSourceFactory)
                    .setLoadErrorHandlingPolicy(errorHandlingPolicy)
            )
            .build()
            .apply {
                playerView.player = this
                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true
                addListener(playerListener)
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            bufferingHandler.removeCallbacks(bufferingRunnable)
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player?.seekToDefaultPosition()
            }
            player?.prepare()
        }

        override fun onPlaybackStateChanged(state: Int) {
            bufferingHandler.removeCallbacks(bufferingRunnable)
            when (state) {
                Player.STATE_BUFFERING -> bufferingHandler.postDelayed(bufferingRunnable, 15000)
                Player.STATE_IDLE -> player?.prepare()
                Player.STATE_ENDED -> { player?.seekToDefaultPosition(); player?.prepare() }
                Player.STATE_READY -> { /* OK */ }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> { seekPlayer(10000); return true }
                KeyEvent.KEYCODE_DPAD_LEFT -> { seekPlayer(-10000); return true }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { changeChannel(true); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { changeChannel(false); return true }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    player?.let { if (it.isPlaying) it.pause() else it.play() }
                    return true
                }
                KeyEvent.KEYCODE_BACK -> { finish(); return true }
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
            info["url"] = parts[0]
            parts.forEach { part ->
                when {
                    part.startsWith("userAgent=") -> info["userAgent"] = part.substringAfter("=")
                    part.startsWith("referer=") -> info["referer"] = part.substringAfter("=")
                    part.startsWith("drm=") -> info["drm"] = part.substringAfter("=")
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

            val sslContext = SSLContext.getInstance("SSL").apply {
                init(null, trustAllCerts, java.security.SecureRandom())
            }

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()
        } catch (_: Exception) {
            OkHttpClient()
        }
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
            if (url.isNotEmpty() && !url.startsWith("http") && url.length % 4 == 0) {
                String(Base64.decode(url, Base64.DEFAULT))
            } else url
        } catch (_: Exception) {
            url
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bufferingHandler.removeCallbacksAndMessages(null)
        player?.release()
    }
}
