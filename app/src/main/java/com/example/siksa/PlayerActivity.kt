package com.example.siksa

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.security.cert.X509Certificate
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.core.graphics.toColorInt
import android.annotation.SuppressLint
import javax.net.ssl.*
import android.util.Base64
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import android.content.Intent
import androidx.media3.common.util.Util
import androidx.core.net.toUri
import androidx.media3.ui.CaptionStyleCompat
import android.util.TypedValue
import androidx.media3.exoplayer.DefaultRenderersFactory



@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var channelsList: ArrayList<String>? = null
    private var currentChannelIndex = 0
    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        applyImmersiveMode()
        setupUI()

        channelsList = intent.getStringArrayListExtra("channelsList")
        currentChannelIndex = intent.getIntExtra("channelIndex", 0)

        loadChannelData()
    }
    private fun setupUI() {
        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
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
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL // التعبئة الكاملة
            keepScreenOn = true
        }
        rootLayout.addView(playerView)

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
    }

    // 1. تصحيح دالة استقبال الروابط الجديدة أثناء فتح التطبيق
    override fun onNewIntent(intent: Intent) { // تأكد أن Intent هنا مستوردة
        super.onNewIntent(intent)
        setIntent(intent)

        // استلام البيانات مع التأكد من القيم الافتراضية
        channelsList = intent.getStringArrayListExtra("channelsList")
        currentChannelIndex = intent.getIntExtra("channelIndex", 0)

        loadChannelData()
    }

    // 2. تحديث دالة التحميل لتقرأ من الـ Intent data (الروابط الخارجية)
    private fun loadChannelData() {
        val currentIntent = intent // استخدام الـ intent الحالي للنشاط
        var drmLicense = currentIntent.getStringExtra("drmLicense") ?: ""
        val referer = currentIntent.getStringExtra("referer") ?: ""
        val userAgent = currentIntent.getStringExtra("userAgent") ?: ""

        val rawUrl: String = when {
            // القادم من قائمة القنوات الداخلية
            !channelsList.isNullOrEmpty() && currentChannelIndex < (channelsList?.size ?: 0) -> {
                channelsList!![currentChannelIndex]
            }
            // القادم كـ Extra مباشر
            currentIntent.hasExtra("streamUrl") -> {
                currentIntent.getStringExtra("streamUrl") ?: ""
            }
            // القادم من رابط خارجي (نقر من تليجرام أو متصفح)
            currentIntent.data != null -> {
                currentIntent.data.toString()
            }
            else -> ""
        }

        val urlToPlay: String
        if (rawUrl.contains("|")) {
            val parts = rawUrl.split("|")
            urlToPlay = parts[0].trim()
            if (parts.size > 1) {
                drmLicense = parts[1].trim()
            }
        } else {
            urlToPlay = rawUrl.trim()
        }

        if (urlToPlay.isNotEmpty()) {
            initializePlayer(urlToPlay, drmLicense, referer, userAgent)
        }
    }

    private fun initializePlayer(url: String, drmKey: String, referer: String, userAgent: String) {
        player?.release()

        val finalUA = when {
            userAgent.isNotEmpty() -> userAgent
            url.contains("mada") -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            else -> "VLC/3.0.0 LibVLC/3.0.0"
        }

        val customHeaders = mutableMapOf("Connection" to "keep-alive", "Icy-MetaData" to "1")
        if (referer.isNotEmpty()) customHeaders["Referer"] = referer
        if (url.contains("mada")) customHeaders["Referer"] = "https://mada.ps/"

        val okHttpFactory = OkHttpDataSource.Factory(getUnsafeOkHttpClient())
            .setUserAgent(finalUA)
            .setDefaultRequestProperties(customHeaders)

        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpFactory)

        // --- تنظيف الكود واكتشاف النوع تلقائياً ---
        val uri = url.toUri() // يتطلب import androidx.core.net.toUri
        val mediaItemBuilder = MediaItem.Builder().setUri(uri) // تعريف واحد فقط هنا

        // إعدادات الـ DRM
        if (drmKey.isNotEmpty() && drmKey.contains(":")) {
            try {
                val parts = drmKey.split(":")
                val kid = parts[0].trim()
                val key = parts[1].trim()
                val decodedKey =
                    if (key.length % 4 != 0) key + "=".repeat(4 - key.length % 4) else key
                val json =
                    """{"keys":[{"kty":"oct","k":"$decodedKey","kid":"$kid"}],"type":"temporary"}"""
                val base64Key = Base64.encodeToString(
                    json.toByteArray(),
                    Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                )

                mediaItemBuilder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                        .setLicenseUri("data:application/json;base64,$base64Key")
                        .setMultiSession(true)
                        .setPlayClearContentWithoutKey(true)
                        .build()
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // استخدام 'when' كـ subject لتحديد النوع (نظيف جداً)
        when (Util.inferContentType(uri)) {
            C.CONTENT_TYPE_DASH -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            C.CONTENT_TYPE_HLS -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            C.CONTENT_TYPE_RTSP -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_RTSP)
            else -> {
                // فحص يدوي للحالات التي لا تتبع المعايير القياسية
                when {
                    url.contains(".mpd") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                    url.contains(".m3u8") || url.contains("hls") -> mediaItemBuilder.setMimeType(
                        MimeTypes.APPLICATION_M3U8
                    )
                }
            }
        }
        // --------------------------------------------------------

        // 1. استخدام RenderersFactory لدعم فك تشفير كافة أنواع الترجمة (الاحترافي)
        val renderersFactory = DefaultRenderersFactory(this).apply {
            // يسمح للمشغل باستخدام فك التشفير البرمجي إذا فشل العتاد (مهم لترجمة MKV)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }

        // 2. إعداد اختيار المسارات (صوت عربي/فرنسي + ترجمة عربية)
        // 1. تحديد حجم الخط بناءً على نوع الجهاز (تلفاز أو هاتف/تابلت)
        val isTelevision = (resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_TYPE_MASK) ==
                android.content.res.Configuration.UI_MODE_TYPE_TELEVISION

        val fontSize = if (isTelevision) 34f else 22f // 34 للتلفاز و 22 للهاتف

// 2. إعداد TrackSelector للغات
        val trackSelector = DefaultTrackSelector(this).apply {
            parameters = buildUponParameters()
                .setPreferredAudioLanguages("ar", "fr", "en") // الأولوية: عربي، فرنسي، إنجليزي
                .setPreferredTextLanguage("ar")               // الترجمة: عربي دائماً
                .setSelectUndeterminedTextLanguage(true)      // تشغيل الترجمة حتى لو غير معرفة
                .build()
        }

// 3. إعدادات التحميل (Buffer)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 2500, 5000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(30000, true)
            .build()

        val errorHandlingPolicy = DefaultLoadErrorHandlingPolicy(3)

// 4. بناء المشغل النهائي
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

                // --- تنسيق مظهر الترجمة الاحترافي ---
                playerView.subtitleView?.apply {
                    visibility = View.VISIBLE

                    // تطبيق الحجم الديناميكي الذي حددناه بالأعلى
                    setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)

                    val style = CaptionStyleCompat(
                        Color.WHITE,              // لون النص
                        Color.TRANSPARENT,        // خلفية شفافة تماماً
                        Color.TRANSPARENT,        // نافذة شفافة
                        CaptionStyleCompat.EDGE_TYPE_OUTLINE, // حد خارجي أسود لبروز النص
                        Color.BLACK,              // لون الحد
                        null
                    )
                    setStyle(style)
                }
                // ------------------------------------

                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true
                addListener(playerListener)
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            super.onVideoSizeChanged(videoSize)
            playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
        }

        override fun onPlayerError(error: PlaybackException) {
            // حل مشكلة الخروج من نافذة البث المباشر (مهم جداً للثبات)
            if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                player?.seekToDefaultPosition()
                player?.prepare()
            } else {
                if (consecutiveErrors < maxConsecutiveErrors) {
                    consecutiveErrors++
                    player?.prepare() // إعادة المحاولة دون إعادة تحميل القناة بالكامل فوراً
                } else {
                    // إذا استمر الخطأ، نعيد تحميل بيانات القناة بالكامل
                    consecutiveErrors = 0
                    loadChannelData()
                }
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                consecutiveErrors = 0
                playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            } else if (state == Player.STATE_ENDED) {
                // إذا انتهى البث بشكل مفاجئ، أعد التشغيل من نقطة البداية الحية
                player?.seekToDefaultPosition()
                player?.prepare()
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
            // استخدام coerceIn هو الحل الأمثل والأنظف في كوتلن
            // فهو يضمن أن القيمة ستبقى حتماً بين 0 ومدة الفيديو
            val newPosition = (it.currentPosition + offset).coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
            it.seekTo(newPosition)
        }
    }

    private fun changeChannel(next: Boolean) {
        val list = channelsList ?: return
        if (list.isEmpty()) return
        currentChannelIndex = if (next) (currentChannelIndex + 1) % list.size else if (currentChannelIndex > 0) currentChannelIndex - 1 else list.size - 1
        player?.stop()
        player?.clearMediaItems()
        loadChannelData()
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
        } catch (_: Exception) { // استخدمنا _ هنا لجعل الكود نظيفاً
            OkHttpClient()
        }
    }

    private fun applyImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
    }
}
