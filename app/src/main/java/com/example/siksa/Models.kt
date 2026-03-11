package com.example.siksa

import android.os.Parcelable
import android.util.Base64
import kotlinx.parcelize.Parcelize

// 1. كائن لتخزين القنوات في الذاكرة لمنع الانهيار (Crash) في القوائم الكبيرة
object ChannelData {
    var list: List<Channel> = emptyList()
}

@Parcelize
data class Channel(
    val name: String,
    val url: String,
    val logo: String = "",
    val drmLicense: String = "",
    val referer: String = "",
    val userAgent: String = "",
    val group: String = ""
) : Parcelable

@Parcelize
data class PackageItem(
    val name: String,
    val logo: String,
    val url: String
) : Parcelable

// 2. دالة تحويل احترافية تعالج الروابط وتجهزها للمشغل
fun Channel.toPlayerString(): String {
    val realUrl = try {
        // فك التشفير فقط إذا لم يبدأ بـ http وكان طوله مناسباً
        if (url.isNotEmpty() && !url.startsWith("http") && url.length > 10) {
            String(Base64.decode(url, Base64.DEFAULT))
        } else {
            url
        }
    } catch (_: Exception) {
        url
    }

    // بناء النص بالصيغة: URL|drm=...&userAgent=...
    val params = mutableListOf<String>()
    if (drmLicense.isNotEmpty()) params.add("drm=$drmLicense")
    if (referer.isNotEmpty()) params.add("referer=$referer")
    if (userAgent.isNotEmpty()) params.add("userAgent=$userAgent")

    return if (params.isNotEmpty()) {
        "$realUrl|${params.joinToString("|")}"
    } else {
        realUrl
    }
}
