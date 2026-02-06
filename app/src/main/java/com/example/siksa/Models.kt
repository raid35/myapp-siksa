package com.example.siksa

import android.os.Parcelable
import android.util.Base64   // 👈 هنا تضيف الاستيراد
import kotlinx.parcelize.Parcelize

@Parcelize
data class Channel(
    val name: String,
    val url: String,
    val logo: String = "",
    val drmLicense: String = "",
    val referer: String = "",    // 👈 أضف هذا السطر
    val userAgent: String = "",  // 👈 وأضف هذا أيضاً للمستقبل
    val group: String = ""
) : Parcelable
@Parcelize
data class PackageItem(
    val name: String,
    val logo: String,
    val url: String
) : Parcelable

fun Channel.toPlayerString(): String {
    val realUrl = try {
        String(Base64.decode(url, Base64.DEFAULT))
    } catch (_: Exception) {
        url
    }

    return if (drmLicense.isNotEmpty()) {
        "$realUrl|drmLicense=$drmLicense"
    } else {
        realUrl
    }
}
