package com.example.siksa

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Base64
import java.io.File

object SecurityUtils {
    private val forbiddenApps = listOf(
        "Y29tLmd1b3NoaS5odHRwY2FuYXJ5",
        "Y29tLmd1b3NoaS5odHRwY2FuYXJ5LnByZW1pdW0=",
        "YXBwLmdyZXlzaGlydHMuc3NsY2FwdHVyZQ==",
        "Y29tLm1pbmh1aS5uZXR3b3JrY2FwdHVyZQ==",
        "Y29tLnZuZXQucGNhcHJlbW90ZQ==",
        "Y29tLmVtYW51ZWxlZi5yZW1vdGVfcGNhcA=="
    )

    /**
     * الدالة الرئيسية لفحص الأمان (VPN + Root + Sniffers)
     */
    fun isSecurityRiskDetected(context: Context): Boolean {
        return isVpnActive(context) || isForbiddenAppInstalled(context) || isDeviceRooted()
    }

    private fun isVpnActive(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ?: false
    }

    private fun isForbiddenAppInstalled(context: Context): Boolean {
        val pm = context.packageManager
        for (encodedName in forbiddenApps) {
            val realName = try {
                String(Base64.decode(encodedName, Base64.DEFAULT), Charsets.UTF_8)
            } catch (e: Exception) { "" }

            try {
                pm.getPackageInfo(realName, 0)
                return true
            } catch (e: Exception) { }
        }
        return false
    }

    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }
        val buildTags = Build.TAGS
        return buildTags != null && buildTags.contains("test-keys")
    }
}
