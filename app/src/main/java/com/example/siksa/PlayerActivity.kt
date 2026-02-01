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
import javax.net.ssl.*

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var channelsList: ArrayList<String>? = null
    private var currentChannelIndex = 0
    private var isRecovering = false
    private var lastPlaybackPosition = 0L

    // --- التعديلات هنا ---

    // فحص التعطل كل 2 ثانية بدل 3 لسرعة الاستجابة
    private var stallCheckRunnable: Runnable? = null
    private val stallCheckInterval = 2000L

    // إذا توقف العداد مرتين (4 ثوانٍ ثبات) نعتبر البث متوقفاً
    private var stallCounter = 0
    private val maxStallCount = 2

    // زيادة عدد محاولات الاستعادة لأن روابط IPTV كثيرة الانقطاع
    private var recoveryAttempts = 0
    private val maxRecoveryAttempts = 20

    private var lastErrorTime = 0L
    private val minErrorInterval = 1000L // تقليل الفاصل الزمني بين الأخطاء
    private var consecutiveErrors = 0
    private val maxConsecutiveErrors = 10

    // تقليل مهلة التحميل (Buffering) لـ 10 ثوانٍ
    // إذا بقي يحمل أكثر من ذلك، يقوم بإعادة الاتصال فوراً
    private var bufferingTimeout: Runnable? = null
    private val bufferingTimeoutInterval = 10000L

    // ----------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
        }

        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
                setColor(Color.parseColor("#22000000"))
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

        channelsList = intent.getStringArrayListExtra("channelsList")
        currentChannelIndex = intent.getIntExtra("channelIndex", 0)
        val singleUrl = intent.getStringExtra("streamUrl")

        when {
            !channelsList.isNullOrEmpty() -> initializePlayer(channelsList!![currentChannelIndex])
            !singleUrl.isNullOrEmpty() -> initializePlayer(singleUrl)
        }
    }

    // عميل HTTP مرن جداً يتجاهل أخطاء الشهادات ليدعم المنفذ 4443
    @android.annotation.SuppressLint("CustomX509TrustManager", "TrustAllX509TrustManager")
    private fun getUnsafeOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, java.security.SecureRandom())

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    private fun initializePlayer(inputUrl: String) {
        stopStallDetection()
        stopBufferingTimeout()
        player?.release()
        isRecovering = false
        recoveryAttempts = 0
        consecutiveErrors = 0

        // 2. تحليل الرابط واستخراج البيانات
        // 2. تحليل الرابط واستخراج البيانات
        val fullPath = inputUrl.trim().split("\n").find { it.startsWith("http") } ?: return
        android.util.Log.e("DEBUG_FULLPATH", fullPath)

        val parts = fullPath.split("|")
        val url = parts[0].trim()

// 👈 إضافة تعريف المتغيرات الخاصة بالـ DRM
        var drmType = ""
        var drmKey = ""
        if (parts.size > 1) {
            val drmParts = parts.subList(1, parts.size)
            drmParts.forEach { part ->
                if (part.startsWith("drm-info=")) drmType = part.substringAfter("drm-info=")
                else drmKey = part
            }
        }
        // 3. إعداد محدد المسارات (صوت وترجمة)
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredAudioLanguages("ar", "fr", "en")
                    .setPreferredTextLanguage("ar")
                    .setSelectUndeterminedTextLanguage(true)
            )
        }

        // 4. إعداد التحكم في التخزين المؤقت (لمنع فصل السيرفر)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2500, 30000, 1000, 1500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 5. إعداد الاتصال والـ User-Agent
        val dynamicUserAgent = if (url.contains("mada")) {
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        } else {
            "VLC/3.0.0 LibVLC/3.0.0"
        }

        val okHttpFactory = OkHttpDataSource.Factory(getUnsafeOkHttpClient())
            .setUserAgent(dynamicUserAgent)
            .setDefaultRequestProperties(mapOf(
                "Connection" to "keep-alive",
                "Icy-MetaData" to "1"
            ))

        if (url.contains("mada")) {
            okHttpFactory.setDefaultRequestProperties(mapOf(
                "Referer" to "https://mada.ps/",
                "Origin" to "https://mada.ps"
            ))
        }

        // 6. بناء MediaItem مع دعم DRM ونوع الملف
        val mediaItemBuilder = MediaItem.Builder().setUri(url)

        if (drmType.equals("clearkey", ignoreCase = true) && drmKey.contains(":")) {
            val (kid, key) = drmKey.split(":")
            val json = """{"keys":[{"kty":"oct","k":"$key","kid":"$kid"}],"type":"temporary"}"""
            val base64 = android.util.Base64.encodeToString(json.toByteArray(), android.util.Base64.NO_WRAP)
            mediaItemBuilder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                    .setLicenseUri("data:application/json;base64,$base64")
                    .build()
            )
        }

        if (url.contains(".mpd")) mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
        else if (url.contains(".m3u8")) mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)

        // 7. بناء المشغل النهائي (مرة واحدة فقط)
        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this)
                    .setDataSourceFactory(DefaultDataSource.Factory(this, okHttpFactory))
                    .setLiveTargetOffsetMs(5000)
            )
            .build()
            .apply {
                playerView.player = this
                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true

                // إضافة المستمع (Listener) داخل دالة initializePlayer
                addListener(object : Player.Listener {
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width <= 0 || videoSize.height <= 0) return
                        val videoRatio = videoSize.width.toFloat() / videoSize.height.toFloat()
                        val metrics = resources.displayMetrics
                        val screenRatio = metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
                        val diff = kotlin.math.abs(videoRatio - screenRatio)
                        playerView.resizeMode = if (diff > 0.35f)
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        else
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        consecutiveErrors++
                        logError("خطأ رقم $consecutiveErrors: ${error.message}")
                        if (consecutiveErrors <= maxConsecutiveErrors) {
                            handleRecovery()
                        }
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                stopBufferingTimeout()
                                startStallDetection()
                                isRecovering = false
                                recoveryAttempts = 0
                                consecutiveErrors = 0 // تصفير الأخطاء عند العمل بنجاح
                            }
                            Player.STATE_BUFFERING -> startBufferingTimeout()
                            Player.STATE_ENDED -> handleRecovery()
                            else -> { /* حالات أخرى */ }
                        }
                    }
                }) // نهاية الـ addListener
            } // نهاية الـ apply
    } // نهاية دالة initializePlayer بالكامل

// --- الآن الدوال التالية تكون خارج initializePlayer ومستقلة بذاتها ---

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val p = player ?: return super.dispatchKeyEvent(event)
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (p.isPlaying) p.pause() else p.play()
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
                KeyEvent.KEYCODE_BACK -> {
                    finish()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun changeChannel(next: Boolean) {
        val list = channelsList ?: return
        currentChannelIndex = if (next) (currentChannelIndex + 1) % list.size else if (currentChannelIndex > 0) currentChannelIndex - 1 else list.size - 1
        initializePlayer(list[currentChannelIndex])
    }

    private fun startStallDetection() {
        stopStallDetection()
        stallCheckRunnable = Runnable {
            player?.let {
                if (it.isPlaying && it.currentPosition == lastPlaybackPosition && it.currentPosition > 0) {
                    stallCounter++
                    if (stallCounter >= maxStallCount) { handleRecovery(); return@Runnable }
                } else stallCounter = 0
                lastPlaybackPosition = it.currentPosition
                playerView.postDelayed(stallCheckRunnable!!, stallCheckInterval)
            }
        }
        playerView.postDelayed(stallCheckRunnable!!, stallCheckInterval)
    }

    private fun stopStallDetection() { stallCheckRunnable?.let { playerView.removeCallbacks(it) }; stallCounter = 0 }
    private fun startBufferingTimeout() { stopBufferingTimeout(); bufferingTimeout = Runnable { handleRecovery() }; playerView.postDelayed(bufferingTimeout!!, bufferingTimeoutInterval) }
    private fun stopBufferingTimeout() { bufferingTimeout?.let { playerView.removeCallbacks(it) } }

    private fun handleRecovery() {
        if (isRecovering) return
        isRecovering = true

        player?.let { p ->
            val currentPos = p.currentPosition
            val currentMediaItem = p.currentMediaItem

            logError("إعادة إنعاش البث لتجنب التوقف...")

            // نقوم بإيقاف المشغل تماماً وإعادة تهيئته
            p.stop()
            p.clearMediaItems()

            if (currentMediaItem != null) {
                p.setMediaItem(currentMediaItem)
                // نعود للخلف قليلاً لضمان استمرارية التدفق من السيرفر
                p.seekTo(if (currentPos > 1000) currentPos - 1000 else 0)
            }

            p.prepare()
            p.play()

            // نترك المشغل قليلاً قبل السماح بمحاولة استعادة أخرى
            playerView.postDelayed({
                isRecovering = false
            }, 3000)
        } ?: run {
            isRecovering = false
        }
    }

    // هذه الدالة يجب أن تكون خارج قوس handleRecovery
    private fun logError(message: String) {
        android.util.Log.e("PlayerActivity", message)
    }

    override fun onDestroy() { super.onDestroy(); player?.release(); player = null }

    private fun applyImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}
