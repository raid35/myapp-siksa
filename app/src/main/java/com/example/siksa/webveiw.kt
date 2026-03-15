package com.example.siksa

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@SuppressLint("SetJavaScriptEnabled")
class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val supportedExtensions = listOf(
        ".m3u8", ".ts", ".mpd", ".mp4", ".mkv", ".avi",
        ".mov", ".flv", ".webm", ".3gp", ".m4a", ".m3u"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupFullPlayerScreen()
        val urlToLoad = intent.getStringExtra("url") ?: "file:///android_asset/movies.html"
        initializeWebView()
        setupBackNavigation()
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(webView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        })

        webView.loadUrl(urlToLoad)
    }

    private fun initializeWebView() {
        webView = WebView(this).apply {
            configureAppearance()
            configureSettings()
            setupClients()
        }
    }
    private fun WebView.configureAppearance() {
        setBackgroundColor(Color.BLACK)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }
    private fun WebView.configureSettings() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            mediaPlaybackRequiresUserGesture = false

            @Suppress("SpellCheckingInspection")
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

            allowFileAccess = false
            allowContentAccess = true
        }
    }
    private fun WebView.setupClients() {
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()

                return when {
                    url.startsWith("intent://") || url.startsWith("intent:") -> handleIntentUrl(url)
                    isVideoUrl(url) -> {
                        openInPlayer(url)
                        true
                    }
                    else -> false
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectOptimizedStyles(view, url)
            }
        }

        webChromeClient = WebChromeClient()
    }

    private fun handleIntentUrl(url: String): Boolean {
        return try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            val targetPackage = intent.`package`
            if (targetPackage != null && targetPackage != "com.example.sis") {
                startActivity(intent)
                true
            } else {
                val videoUrl = intent.dataString
                if (videoUrl != null) {
                    openInPlayer(videoUrl)
                    true
                } else false
            }
        } catch (e: Exception) {
            Log.e("WebView", "Error parsing intent: ${e.message}")
            false
        }
    }

    private fun injectOptimizedStyles(view: WebView?, url: String?) {
        val script = if (url?.contains("android_asset") == true) {
            """
            document.body.style.backgroundColor = 'black';
            document.body.style.userSelect = 'none';
            """.trimIndent()
        } else {
            """
            (function() {
                var v = document.querySelector('video');
                if(v) { 
                    v.style.width = '100vw'; 
                    v.style.height = '100vh'; 
                    v.style.objectFit = 'fill';
                    v.play();
                }
                var style = document.createElement('style');
                style.innerHTML = 'video { image-rendering: -webkit-optimize-contrast !important; }';
                document.head.appendChild(style);
            })()
            """.trimIndent()
        }
        view?.evaluateJavascript(script, null)
    }

    private fun setupFullPlayerScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    @Suppress("SpellCheckingInspection")
    private fun isVideoUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return supportedExtensions.any { lowerUrl.contains(it) } ||
                lowerUrl.contains("googlevideo.com") ||
                lowerUrl.contains("/manifest") ||
                lowerUrl.contains("player_api.php")
    }

    private fun openInPlayer(url: String) {
        try {
            val intent = Intent(this, PlayerActivity::class.java).apply {
                data = url.toUri()
                putExtra("url", url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("WebView", "PlayerActivity not found: ${e.message}")
        }
    }
    override fun onPause() {
        webView.onPause()
        webView.pauseTimers()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
