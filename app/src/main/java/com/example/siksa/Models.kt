package com.example.siksa

import android.os.Parcelable
import android.util.Base64
import kotlinx.parcelize.Parcelize

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

// 👈 تضع الدالة هنا (خارج الكلاس ولكن في نفس الملف)
fun Channel.toPlayerString(): String {
    val realUrl = try {
        if (url.isNotEmpty() && !url.startsWith("http")) {
            String(Base64.decode(url, Base64.DEFAULT))
        } else {
            url
        }
    } catch (_: Exception) {
        url
    }

    val builder = StringBuilder(realUrl)
    val params = mutableListOf<String>()

    if (drmLicense.isNotEmpty()) params.add("drmLicense=$drmLicense")
    if (referer.isNotEmpty()) params.add("referer=$referer")
    if (userAgent.isNotEmpty()) params.add("userAgent=$userAgent")

    return if (params.isNotEmpty()) {
        builder.append("|").append(params.joinToString("&")).toString()
    } else {
        builder.toString()
    }
}

// إذا كان لديك كلاس PackageItem ضعه هنا أيضاً
@Parcelize
data class PackageItem(
    val name: String,
    val logo: String,
    val url: String
) : Parcelable
