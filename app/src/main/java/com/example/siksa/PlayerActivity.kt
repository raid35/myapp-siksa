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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    // قائمة الـ User-Agents المأخوذة من كود Catch-up TV (بايثون)
    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Edg/138.0.3351.121",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.5 Safari/605.1.15",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Vivaldi/7.0.3495.26"
    )

    private fun getRandomUserAgent(): String {
        return userAgents.random()
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var channelsList: ArrayList<String>? = null
    private var currentChannelIndex = 0

    private var isRecovering = false
    private var lastPlaybackPosition = 0L
    private var stallCheckRunnable: Runnable? = null
    private val stallCheckInterval = 3000L // زيادة من 2000 إلى 3000ms
    private var stallCounter = 0 // عداد التوقف المتكرر
    private val maxStallCount = 3 // عدد مرات التوقف قبل Recovery

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

        // 1. الحاوية الرئيسية
        val rootLayout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
        }

        // 2. مشغل الفيديو - محسّن للأبعاد
        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            useController = false
            setOnTouchListener { _, _ -> true }
            isFocusable = false
            // تحسين دالة الأبعاد الاحترافية
            resizeMode = getOptimalResizeMode()
        }
        rootLayout.addView(playerView)

        setContentView(rootLayout)
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

    private fun initializePlayer(inputUrl: String) {
        stopStallDetection()
        player?.release()
        isRecovering = false

        val finalUrl = inputUrl.trim().split("\n").find { it.startsWith("http") } ?: return

        // 1. إعداد محدد المسارات
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredTextLanguage("ar")
                    .setPreferredAudioLanguages("ar", "fr")
                    .setSelectUndeterminedTextLanguage(true)
            )
        }

        // 2. إعداد مصدر البيانات (التبديل الذكي بين VLC والمتصفح)
        val httpDataSourceFactory = DefaultHttpDataSource.Factory().apply {

            // اختيار الهوية بناءً على الرابط
            val selectedUA = if (finalUrl.contains("mada") || finalUrl.contains(":4443")) {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            } else {
                "VLC/3.0.0 LibVLC/3.0.0" // العودة لـ VLC لبقية الروابط لضمان عملها
            }

            setUserAgent(selectedUA)

            val headers = mutableMapOf<String, String>()
            headers["User-Agent"] = selectedUA

            // إعداد الـ Referer ذكياً
            if (finalUrl.contains("mada") || finalUrl.contains(":4443")) {
                headers["Accept"] = "*/*"
                // روابط mada غالباً لا تحتاج referer أو Origin
            } else {
                try {
                    val uri = android.net.Uri.parse(finalUrl)
                    val baseUrl = "${uri.scheme}://${uri.host}/"
                    headers["Referer"] = baseUrl
                    headers["Origin"] = baseUrl
                } catch (e: Exception) {
                }
            }

            setDefaultRequestProperties(headers)
            setAllowCrossProtocolRedirects(true)
            setConnectTimeoutMs(15000)
            setReadTimeoutMs(15000)
        }

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        // 3. بناء المشغل
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setTrackSelector(trackSelector)
            .setLoadControl(
                DefaultLoadControl.Builder().setBufferDurationsMs(30000, 60000, 2500, 5000).build()
            )
            .build()
            .apply {
                playerView.player = this

                val mediaItemBuilder = MediaItem.Builder().setUri(finalUrl)
                if (finalUrl.contains("m3u8", true)) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                }

                setMediaItem(mediaItemBuilder.build())
                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        handleRecovery()
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_READY -> {
                                stallCounter = 0
                                startStallDetection()
                            }

                            Player.STATE_BUFFERING -> {
                                playerView.postDelayed({
                                    if (playbackState == Player.STATE_BUFFERING) handleRecovery()
                                }, 30000)
                            }

                            Player.STATE_ENDED -> {
                                if (!channelsList.isNullOrEmpty()) {
                                    seekToDefaultPosition()
                                    prepare()
                                }
                            }
                        }
                    }
                })
            }
    }

    private fun startStallDetection() {
        stopStallDetection()
        stallCounter = 0
        stallCheckRunnable = Runnable {
            val p = player ?: return@Runnable
            if (p.isPlaying && p.playbackState == Player.STATE_READY) {
                val currentPos = p.currentPosition
                // التحقق من التوقف فقط للبث المباشر (التحقق من أن المسافة المتقدمة صغيرة جداً)
                if (currentPos == lastPlaybackPosition && currentPos > 0) {
                    stallCounter++
                    // فقط بعد 3 توقفات متتالية نقوم ب Recovery
                    if (stallCounter >= maxStallCount) {
                        handleRecovery()
                        return@Runnable
                    }
                } else {
                    stallCounter = 0 // إعادة تعيين إذا تقدم التشغيل
                }
                lastPlaybackPosition = currentPos
            }
            playerView.postDelayed(stallCheckRunnable!!, stallCheckInterval)
        }
        playerView.postDelayed(stallCheckRunnable!!, stallCheckInterval)
    }

    private fun stopStallDetection() {
        stallCheckRunnable?.let { playerView.removeCallbacks(it) }
        stallCheckRunnable = null
    }

    private fun handleRecovery() {
        if (isRecovering) return
        isRecovering = true
        stallCounter = 0

        player?.let {
            val currentMediaItem = it.currentMediaItem
            val currentPos = it.currentPosition

            try {
                it.stop()
                it.clearMediaItems()
                if (currentMediaItem != null) {
                    it.setMediaItem(currentMediaItem)
                    // الانتظار قليلاً قبل استئناف التشغيل
                    it.seekTo(currentPos)
                }
                it.prepare()
                it.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // زيادة وقت انتظار الاستقرار من 3 إلى 5 ثوان
        playerView.postDelayed({ isRecovering = false }, 5000)
    }

    private fun getOptimalResizeMode(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        // حساب نسبة العرض إلى الارتفاع للشاشة
        val screenAspectRatio = screenWidth.toFloat() / screenHeight.toFloat()

        // إذا كانت الشاشة بنسبة عريضة جداً (عرضية) أو مربعة تقريباً
        return when {
            screenAspectRatio > 1.5f || screenAspectRatio < 0.67f -> {
                // للهواتف العريضة جداً أو الطويلة جداً: استخدم FILL
                AspectRatioFrameLayout.RESIZE_MODE_FILL
            }

            screenWidth < 540 || screenHeight < 960 -> {
                // للهواتف الصغيرة جداً: استخدم ZOOM قليل
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }

            else -> {
                // للهواتف العادية والكبيرة: استخدم FIT للحفاظ على النسبة
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
    }

    private fun changeChannel(next: Boolean) {
        val list = channelsList ?: return
        currentChannelIndex = if (next) (currentChannelIndex + 1) % list.size
        else if (currentChannelIndex > 0) currentChannelIndex - 1 else list.size - 1
        initializePlayer(list[currentChannelIndex])
    }

    private fun applyImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val p = player ?: return super.dispatchKeyEvent(event)

            when (event.keyCode) {
                // زر OK أو Enter للإيقاف والتشغيل
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (p.isPlaying) {
                        p.pause()
                    } else {
                        p.play()
                    }
                    return true
                }

                // زر الأعلى والأسفل لتغيير القنوات
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    changeChannel(true); return true
                }

                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    changeChannel(false); return true
                }

                // زر اليمين: تقديم 10 ثوانٍ
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (p.isCurrentMediaItemSeekable) {
                        p.seekTo(minOf(p.currentPosition + 10000, p.duration))
                    }
                    return true
                }

                // زر اليسار: تأخير 10 ثوانٍ
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (p.isCurrentMediaItemSeekable) {
                        p.seekTo(maxOf(0, p.currentPosition - 10000))
                    }
                    return true
                }

                KeyEvent.KEYCODE_BACK -> {
                    finish(); return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        player?.play()
        startStallDetection()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
        stopStallDetection()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStallDetection()
        player?.stop()
        player?.release()
        player = null
    }

}
