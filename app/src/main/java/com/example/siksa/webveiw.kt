
package com.example.siksa

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.net.toUri
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
@SuppressLint("SetJavaScriptEnabled")
class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var urlToLoad: String = ""
    private val supportedExtensions = listOf(".m3u8", ".ts", ".mpd", ".mp4", ".mkv", ".avi")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        urlToLoad = intent.getStringExtra("url") ?: return

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true

                // --- تحسينات الجودة والسرعة ---
                // 1. إجبار المتصفح على طلب جودة سطح المكتب (Desktop Mode)
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                // 2. تفعيل التخزين المؤقت لتسريع التحميل
                databaseEnabled = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT

                // 3. تحسين عرض الفيديو
                loadWithOverviewMode = true
                useWideViewPort = true
                mediaPlaybackRequiresUserGesture = false

                // 4. السماح بالمحتوى المختلط (ضروري لروابط الترخيص والمفاتيح)
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                // 5. تسريع الرندرة
                setEnableSmoothTransition(true)
            }

            // تفعيل تسريع العتاد على مستوى الـ WebView
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            webChromeClient = WebChromeClient()

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // تحسين CSS لضمان عدم وجود بكسلة (Anti-aliasing)
                    val css = """
                        javascript:(function() {
                            var style = document.createElement('style');
                            style.innerHTML = 'video { width: 100% !important; height: 100% !important; object-fit: contain !important; image-rendering: auto !important; }';
                            document.head.appendChild(style);
                        })()
                    """.trimIndent()
                    view?.loadUrl(css)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val clickedUrl = request?.url.toString()
                    return if (shouldOpenInXpola(clickedUrl)) {
                        openInXpola(clickedUrl)
                        true
                    } else false
                }
            }
            loadUrl(urlToLoad)
        }

        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(webView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        })
    }

    private fun shouldOpenInXpola(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.startsWith("intent://") ||
                lowerUrl.contains("com.xpola.player") ||
                supportedExtensions.any { lowerUrl.contains(it) }
    }

    private fun openInXpola(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = url.toUri()
                setPackage("com.xpola.player")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val fallback = Intent(Intent.ACTION_VIEW, url.toUri())
            startActivity(Intent.createChooser(fallback, "اختر مشغل الفيديو"))
        }
        finish()
    }
}
