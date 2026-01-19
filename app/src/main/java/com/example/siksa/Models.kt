package com.example.siksa

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Channel(
    val name: String,
    val url: String,
    val logo: String = "",
    val drmLicense: String = "",
    val group: String = ""
) : Parcelable

@Parcelize
data class PackageItem(
    val name: String,
    val logo: String,
    val url: String
) : Parcelable
