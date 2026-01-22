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

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var channelsList: ArrayList<String>? = null
    private var currentChannelIndex = 0

    private var isRecovering = false
    private var lastPlaybackPosition = 0L
    private var stallCheckRunnable: Runnable? = null
    private val stallCheckInterval = 2000L

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

        // 2. مشغل الفيديو
        playerView = PlayerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            useController = false
            setOnTouchListener { _, _ -> true }
            isFocusable = false
        }
        rootLayout.addView(playerView)

        // --- إضافة العلامة المائية المحدثة (جهة اليسار، أصغر، وأكثر شفافية) ---
        val watermark = TextView(this).apply {
            text = "S"
            textSize = 14f // تصغير حجم الخط قليلاً
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            alpha = 0.3f // زيادة الشفافية (خفيفة جداً الآن)

            // تصميم الدائرة خلف الحرف
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#22000000")) // لون أسود شبه شفاف
                setStroke(1, Color.WHITE) // إطار أبيض أنحف (1 بدل 2)
            }

            // تحديد الحجم والموقع (الزاوية اليسرى السفلى)
            val size = (35 * resources.displayMetrics.density).toInt() // تصغير قطر الدائرة
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.BOTTOM or Gravity.START // جهة اليسار
                setMargins(40, 0, 0, 30) // 40 من اليسار و 30 من الأسفل
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

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15000, 50000, 1500, 3000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setUserAgent("VLC/3.0.0 (Windows NT 10.0; Win64; x64)")
            setAllowCrossProtocolRedirects(true)
            setConnectTimeoutMs(15000)
            setReadTimeoutMs(15000)
        }

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
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
                        if (state == Player.STATE_READY) {
                            startStallDetection()
                        } else if (state == Player.STATE_BUFFERING) {
                            playerView.postDelayed({
                                if (playbackState == Player.STATE_BUFFERING) handleRecovery()
                            }, 15000)
                        }
                    }
                })
            }
    }

    private fun startStallDetection() {
        stopStallDetection()
        stallCheckRunnable = Runnable {
            val p = player ?: return@Runnable
            if (p.isPlaying && p.playbackState == Player.STATE_READY) {
                val currentPos = p.currentPosition
                if (currentPos == lastPlaybackPosition && currentPos > 0) {
                    handleRecovery()
                    return@Runnable
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
        player?.let {
            val currentMediaItem = it.currentMediaItem
            it.stop()
            it.clearMediaItems()
            if (currentMediaItem != null) it.setMediaItem(currentMediaItem)
            it.prepare()
            it.play()
        }
        playerView.postDelayed({ isRecovering = false }, 3000)
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
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { changeChannel(true); return true }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { changeChannel(false); return true }
                KeyEvent.KEYCODE_BACK -> { finish(); return true }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() { super.onResume(); applyImmersiveMode(); player?.play() }
    override fun onPause() { super.onPause(); player?.pause() }
    override fun onDestroy() { super.onDestroy(); stopStallDetection(); player?.release(); player = null }
}
