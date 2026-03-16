package com.example.siksa

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@SuppressLint("SetJavaScriptEnabled")
class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private val supportedExtensions = listOf(
        ".m3u8", ".ts", ".mpd", ".mp4", ".mkv", ".avi",
        ".mov", ".flv", ".webm", ".3gp", ".m4a", ".m3u"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SecurityUtils.isSecurityRiskDetected(this)) {
            finish()
            return
        }

        setupFullPlayerScreen()
        val rawUrl = intent.getStringExtra("url") ?: "file:///android_asset/movies.html"
        val urlToLoad = transformToEmbedUrl(rawUrl)

        initializeProgressBar()
        initializeWebView()

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(webView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(progressBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 10).apply {
                gravity = android.view.Gravity.TOP
            })
        })

        setupBackNavigation()
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
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
        }
    }

    private fun WebView.setupClients() {
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                injectOptimizedStyles(view, url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()

                return when {
                    url.startsWith("intent://") || url.startsWith("intent:") -> handleIntentUrl(url)
                    isVideoUrl(url) -> {
                        openInPlayer(url)
                        true
                    }
                    url.contains("facebook.com") || url.contains("twitter.com") -> true

                    else -> false
                }
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
            }
        }
    }

    private fun transformToEmbedUrl(url: String): String {
        return try {
            when {
                url.contains("youtube") || url.contains("youtu.be") -> {
                    val pattern = "(?<=watch\\?v=|/videos/|embed\\/|youtu.be\\/|\\/v\\/|\\/e\\/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%‌​2F|youtu.be%2F|%2Fv%2F|live\\/)[^#\\&\\?\\n]*"
                    val matcher = java.util.regex.Pattern.compile(pattern).matcher(url)
                    if (matcher.find()) "https://www.youtube-nocookie.com/embed/${matcher.group()}?autoplay=1&controls=0&modestbranding=1&rel=0" else url
                }
                url.contains("ok.ru") && url.contains("/video/") -> url.replace("/video/", "/videoembed/") + "?autoplay=1"
                else -> url
            }
        } catch (e: Exception) { url }
    }

    private fun initializeProgressBar() {
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressDrawable.setTint(Color.parseColor("#FFD700"))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#44FFFFFF"))
            visibility = View.GONE
            max = 100
        }
    }

    private fun handleIntentUrl(url: String): Boolean {
        return try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            startActivity(intent)
            true
        } catch (e: Exception) { false }
    }

    private fun isVideoUrl(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return supportedExtensions.any { lowerUrl.contains(it) } || lowerUrl.contains("googlevideo.com")
    }

    private fun openInPlayer(url: String) {
        val intent = Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", url)
        }
        startActivity(intent)
    }

    private fun injectOptimizedStyles(view: WebView?, url: String?) {
        val script = "javascript:(function() { " +
                "document.getElementsByTagName('header')[0].style.display='none'; " +
                "document.getElementsByTagName('footer')[0].style.display='none'; " +
                "})()"
        view?.loadUrl(script)
    }

    private fun setupFullPlayerScreen() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    override fun onPause() { webView.onPause(); super.onPause() }
    override fun onResume() { super.onResume(); webView.onResume() }
    override fun onDestroy() { webView.destroy(); super.onDestroy() }
}
