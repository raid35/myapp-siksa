package com.example.siksa

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView

@OptIn(UnstableApi::class)
class PlayerActivity : AppCompatActivity() {

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView

    private var channelsList: ArrayList<String>? = null
    private var currentChannelIndex = 0
    private var currentUrl: String = ""
    private var bufferingStartTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyImmersiveMode()

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
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            useController = false
            setOnTouchListener { _, _ -> true }
            isFocusable = false
            isFocusableInTouchMode = false
            setShutterBackgroundColor(Color.BLACK)
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            videoSurfaceView?.setKeepScreenOn(true)
        }

        rootLayout.addView(playerView)
        setContentView(rootLayout)

        channelsList = intent.getStringArrayListExtra("channelsList")
        currentChannelIndex = intent.getIntExtra("channelIndex", 0)
        val singleUrl = intent.getStringExtra("streamUrl")

        when {
            !channelsList.isNullOrEmpty() ->
                initializePlayer(channelsList!![currentChannelIndex])

            !singleUrl.isNullOrEmpty() ->
                initializePlayer(singleUrl)
        }
    }

    private fun applyImmersiveMode() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    private fun initializePlayer(url: String) {
        currentUrl = url
        player?.release()

        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
            )
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                3000,  // minBuffer
                7000,  // maxBuffer
                1500,  // bufferForPlayback
                2000   // bufferForPlaybackAfterRebuffer
            )
            .build()

        player = ExoPlayer.Builder(this)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()
            .apply {
                playerView.player = this
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true

                addListener(object : Player.Listener {

                    override fun onPlayerError(error: PlaybackException) {
                        // حذف أي رسالة، إعادة التحميل مباشرة
                        stop()
                        clearMediaItems()
                        setMediaItem(MediaItem.fromUri(currentUrl))
                        prepare()
                        playWhenReady = true
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> bufferingStartTime = System.currentTimeMillis()
                            Player.STATE_READY -> bufferingStartTime = 0
                        }

                        if (bufferingStartTime > 0 &&
                            System.currentTimeMillis() - bufferingStartTime > 5000
                        ) {
                            stop()
                            prepare()
                            playWhenReady = true
                        }
                    }
                })
            }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
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
                else -> return true
            }
        }
        return true
    }

    private fun changeChannel(next: Boolean) {
        val list = channelsList ?: return
        currentChannelIndex = if (next) {
            (currentChannelIndex + 1) % list.size
        } else {
            if (currentChannelIndex > 0) currentChannelIndex - 1 else list.size - 1
        }
        initializePlayer(list[currentChannelIndex])
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveMode()
        player?.play()
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
