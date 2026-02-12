package com.example.siksa

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
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

        // إعدادات الأداء والشاشة الكاملة
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        urlToLoad = intent.getStringExtra("url") ?: return

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                loadWithOverviewMode = true
                useWideViewPort = true
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val clickedUrl = request?.url.toString()
                    return if (shouldOpenInXpola(clickedUrl)) {
                        openInXpola(clickedUrl)
                        true
                    } else false
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)

                    // هنا قمنا بحل المشكلة: تعريف الكود ثم تنفيذه فوراً
                    val script = """
                        (function() {
                            var v = document.querySelector('video');
                            if(v) {
                                v.play();
                                v.style.width = '100vw';
                                v.style.height = '100vh';
                                v.style.objectFit = 'fill'; 
                                v.style.imageRendering = 'high-quality'; 
                                v.style.imageRendering = '-webkit-optimize-contrast';
                            }
                            document.body.style.margin = '0';
                            document.body.style.padding = '0';
                            document.body.style.backgroundColor = 'black';
                        })()
                    """.trimIndent()

                    // تنفيذ الكود داخل المتصفح
                    view?.evaluateJavascript(script, null)
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
    // 1. عند خروج المستخدم مؤقتاً من التطبيق (مثلاً ضغط زر الهوم)
    override fun onPause() {
        super.onPause()
        webView.onPause() // إيقاف تشغيل الرندرة والـ JavaScript مؤقتاً
        webView.pauseTimers() // إيقاف المؤقتات لمنع استهلاك المعالج والصوت
    }

    // 2. عند العودة للتطبيق مرة أخرى
    override fun onResume() {
        super.onResume()
        webView.onResume()
        webView.resumeTimers()
    }

    // 3. الحل النهائي: عند إغلاق الصفحة والعودة لقائمة القنوات
    override fun onDestroy() {
        // تحميل صفحة فارغة لضمان توقف أي بث فيديو أو صوت تماماً
        webView.loadUrl("about:blank")

        // تدمير الـ WebView من الذاكرة
        webView.stopLoading()
        webView.removeAllViews()
        webView.destroy()

        super.onDestroy()
    }
}
