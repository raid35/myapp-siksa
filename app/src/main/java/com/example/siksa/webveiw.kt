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

@SuppressLint("SetJavaScriptEnabled")
class WebViewActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var urlToLoad: String = ""

    // الصيغ المدعومة
    private val supportedExtensions = listOf(".m3u8", ".ts", ".mpd", ".mp4", ".mkv", ".avi")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        urlToLoad = intent.getStringExtra("url") ?: return

        webView = WebView(this).apply {
            setBackgroundColor(Color.BLACK)

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            settings.displayZoomControls = false
            settings.builtInZoomControls = false
            settings.mediaPlaybackRequiresUserGesture = false

            webChromeClient = WebChromeClient()

            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val clickedUrl = request?.url.toString()
                    if (shouldOpenInXpola(clickedUrl)) {
                        openInXpola(clickedUrl)
                        return true
                    }
                    return false
                }

                @Suppress("DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    if (url.isNullOrEmpty()) return false
                    if (shouldOpenInXpola(url)) {
                        openInXpola(url)
                        return true
                    }
                    return false
                }
            }

            loadUrl(urlToLoad)
        }

        val layout = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        setContentView(layout)
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
                data = Uri.parse(url)
                setPackage("com.xpola.player") // إجبار التشغيل في Xpola Player
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(Intent.createChooser(fallback, "اختر مشغل الفيديو"))
        }
        finish()
    }
}
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                url ?: return false

                return when {
                    url.startsWith("https://play/stream") -> {
                        val uri = Uri.parse(url)
                        val streamUrl = uri.getQueryParameter("url")
                        val channelName = uri.getQueryParameter("name") ?: "Live Channel"

                        if (!streamUrl.isNullOrEmpty()) {
                            val intent = Intent(this@WebViewActivity, PlayerActivity::class.java).apply {
                                putExtra("streamUrl", streamUrl)
                                putExtra("channelName", channelName)
                            }
                            startActivity(intent)
                        }
                        true
                    }

                    url.startsWith("intent://") ||
                            url.startsWith("xmtv://") ||
                            url.startsWith("vlc://") ||
                            url.startsWith("ssiptv://") -> {

                        val streamUrl = convertCustomSchemeToStream(url)
                        val drmLicense = extractDrmLicense(url)

                        if (streamUrl != null) {
                            val intent = Intent(this@WebViewActivity, PlayerActivity::class.java).apply {
                                putExtra("streamUrl", streamUrl)
                                putExtra("channelName", "Live Stream")
                                putExtra("drmLicense", drmLicense)
                            }
                            startActivity(intent)
                        }
                        true
                    }

                    else -> {
                        // إذا كان رابط YouTube عادي، نحوله إلى embed
                        val finalUrl = if (url.contains("youtube.com/watch")) {
                            val uri = Uri.parse(url)
                            val videoId = uri.getQueryParameter("v")
                            "https://www.youtube.com/embed/$videoId?autoplay=1"
                        } else {
                            url
                        }

                        view?.loadUrl(finalUrl)
                        true
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(
                    """
                    (function() {
                        try {
                            var videos = document.getElementsByTagName('video');
                            if (videos.length > 0) {
                                videos[0].muted = false;
                                videos[0].autoplay = true;
                                videos[0].play();
                            }

                            // Facebook video click
                            var fbPlayer = document.querySelector('[data-sigil*="inlineVideo"]');
                            if (fbPlayer) {
                                fbPlayer.click();
                            }

                            // YouTube autoplay (if embedded)
                            if (window.location.hostname.includes('youtube.com')) {
                                var ytVideo = document.querySelector('video');
                                if (ytVideo) {
                                    ytVideo.muted = false;
                                    ytVideo.play();
                                }
                            }
                        } catch(e) {}
                    })();
                    """.trimIndent(), null
                )
            }
        }

        val url = intent.getStringExtra("url") ?: ""
        webView.loadUrl(url)

        setContentView(webView)
    }

    // دالة لتحويل البروتوكولات المخصصة إلى روابط فيديو مباشرة
    private fun convertCustomSchemeToStream(customUrl: String): String? {
        return try {
            when {
                customUrl.startsWith("intent://") -> {
                    val uri = Uri.parse(customUrl)
                    uri.getQueryParameter("url")
                        ?: customUrl.replace("intent://", "http://").substringBefore("#Intent")
                }

                customUrl.startsWith("xmtv://") ||
                        customUrl.startsWith("vlc://") ||
                        customUrl.startsWith("ssiptv://") -> {
                    customUrl.replaceFirst(Regex("^[a-z]+://"), "http://")
                }

                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    // استخراج المفتاح DRM من الرابط إذا كان موجودًا
    private fun extractDrmLicense(url: String): String {
        return try {
            if (url.contains("license_key=")) {
                val uri = Uri.parse(url)
                uri.getQueryParameter("license_key") ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}
