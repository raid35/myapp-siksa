package com.example.siksa

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class WebViewActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val webView = WebView(this)

        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.domStorageEnabled = true
        webSettings.loadsImagesAutomatically = true
        webSettings.useWideViewPort = true
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.allowFileAccess = true
        webSettings.allowContentAccess = true
        webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        webSettings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {

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
