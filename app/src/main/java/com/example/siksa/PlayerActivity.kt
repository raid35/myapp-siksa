package com.example.siksa

import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import android.util.Base64
import android.util.Log
import androidx.media3.ui.PlayerView
import android.view.KeyEvent
import androidx.media3.exoplayer.source.MediaSource
import android.view.WindowManager
import android.content.Intent
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.drm.MediaDrmCallback
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.*
import java.util.*
import android.graphics.Color as AndroidColor
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private var playerViewRef: PlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // إبقاء الشاشة تعمل
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // جعل الشاشة كاملة
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val streamUrl = intent?.getStringExtra("streamUrl") ?: ""
        val drmLicense = intent?.getStringExtra("drmLicense") ?: ""  // أخذ المفتاح مباشرة من الـ Intent

        if (streamUrl.isNotEmpty()) {
            setContent {
                Box(modifier = Modifier.fillMaxSize()) {

                    // ✅ مشغل الفيديو
                    ExoPlayerScreen(streamUrl = streamUrl, drmLicense = drmLicense) { playerView ->
                        playerViewRef = playerView
                    }

                    // ✅ عنوان Watermark "SIKSA"
                    Text(
                        text = "SIKSA",
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp),
                        color = Color.White.copy(alpha = 0.2f),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                playerViewRef?.player?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                }
                playerViewRef?.showController()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_DPAD_LEFT -> {
                playerViewRef?.let { playerView ->
                    val focusedView = playerView.findFocus()

                    if (focusedView == null || focusedView == playerView) {
                        playerView.player?.let { player ->
                            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                                val seekPosition = player.currentPosition + 10_000
                                player.seekTo(seekPosition.coerceAtMost(player.duration))
                            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
                                val seekPosition = player.currentPosition - 10_000
                                player.seekTo(seekPosition.coerceAtLeast(0))
                            }
                            playerView.showController()
                            return true
                        }
                    } else {
                        return false
                    }
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                playerViewRef?.showController()
                return false
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                playerViewRef?.showController()
                return false
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun ExoPlayerScreen(
    streamUrl: String,
    drmLicense: String,  // أخذ drmLicense مباشرة من الـ Intent
    onPlayerViewReady: (PlayerView) -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var retryCount by remember { mutableStateOf(0) }
    val maxRetryCount = 3

    val license = drmLicense
    val player = remember {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                5000,   // minBufferMs
                15000,  // maxBufferMs
                1000,   // bufferForPlaybackMs
                5000    // bufferForPlaybackAfterRebufferMs
            )
            .build()

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setForceHighestSupportedBitrate(true)
                    .setMaxVideoSize(Integer.MAX_VALUE, Integer.MAX_VALUE)
            )
        }

        val userAgent = "ExoPlayerDemo/1.0"
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)

        val subtitle =
            MediaItem.SubtitleConfiguration.Builder(Uri.parse("https://example.com/subtitle.vtt"))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage("ar")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(streamUrl))
            .setSubtitleConfigurations(listOf(subtitle))

        // إضافة دعم DRM إذا كان المفتاح موجودًا
        if (license.isNotEmpty()) {
            try {
                // محاولة قراءة الترخيص (DRM) بناءً على نوعه
                val parts = license.split(":")
                if (parts.size == 2) {
                    val kid = parts[0]
                    val key = parts[1]

                    val clearkeyJson = """
                        {
                            "keys": [ {
                                "kty": "oct",
                                "kid": "$kid",
                                "k": "$key"
                            }],
                            "type": "temporary"
                        }
                    """.trimIndent()

                    val clearkeyBase64 = Base64.encodeToString(clearkeyJson.toByteArray(), Base64.NO_WRAP)
                    val clearkeyUri = Uri.parse("data:application/json;base64,$clearkeyBase64")

                    // إذا كان المفتاح هو ClearKey
                    val drmConfiguration = MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID)
                        .setLicenseUri(clearkeyUri)
                        .build()
                    mediaItemBuilder.setDrmConfiguration(drmConfiguration)
                } else {
                    // تحقق من إذا كان DRM من النوع Widevine أو PlayReady
                    // Widevine
                    if (license.contains("Widevine")) {
                        val drmConfiguration = MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(Uri.parse("https://widevine.license.server/"))
                            .build()
                        mediaItemBuilder.setDrmConfiguration(drmConfiguration)
                    }
                    // PlayReady
                    else if (license.contains("PlayReady")) {
                        val drmConfiguration = MediaItem.DrmConfiguration.Builder(C.PLAYREADY_UUID)
                            .setLicenseUri(Uri.parse("https://playready.license.server/"))
                            .build()
                        mediaItemBuilder.setDrmConfiguration(drmConfiguration)
                    }
                }
            } catch (e: Exception) {
                Log.e("DRM", "خطأ في معالجة مفتاح DRM: ${e.message}")
            }
        }

        val mediaItem = mediaItemBuilder.build()

        // إنشاء MediaSource
        val mediaSource: MediaSource = when {
            streamUrl.endsWith(".m3u8") -> HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)

            streamUrl.endsWith(".mpd") -> DashMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)

            else -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }

        ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build().apply {
                setMediaSource(mediaSource)
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isLoading = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE
                if (state == Player.STATE_READY) isError = false
            }

            override fun onPlayerError(error: PlaybackException) {
                isError = true
                isLoading = false
                errorMessage = "فشل تشغيل القناة"
                if (retryCount < maxRetryCount) {
                    retryCount++
                    player.prepare()
                } else {
                    errorMessage = "فشل الاتصال بعد عدة محاولات."
                }
            }
        }

        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    this.player = player
                    useController = true
                    controllerShowTimeoutMs = 3000
                    controllerHideOnTouch = true
                    setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                    setKeepContentOnPlayerReset(true)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setShowSubtitleButton(true)
                    setOnTouchListener { _, _ ->
                        showController()
                        false
                    }
                    onPlayerViewReady(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = Color.Green,
                    modifier = Modifier.size(50.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "جارٍ التحميل...",
                    color = Color.Green,
                    fontSize = 16.sp
                )
            }

            if (isError) {
                Text(
                    text = errorMessage,
                    color = Color.Red,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                )
            }
        }
    }
}
