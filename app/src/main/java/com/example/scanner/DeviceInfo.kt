package com.example.scanner

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Environment
import android.os.StatFs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

object DeviceInfo {

    fun installationId(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("loguen", Context.MODE_PRIVATE)
        var id = prefs.getString("install_id", null)
        if (id == null) {
            id = UUID.randomUUID().toString().take(8)
            prefs.edit().putString("install_id", id).apply()
        }
        return id
    }

    fun alreadySent(ctx: Context): Boolean =
        ctx.getSharedPreferences("loguen", Context.MODE_PRIVATE)
            .getBoolean("sent", false)

    fun markSent(ctx: Context) {
        ctx.getSharedPreferences("loguen", Context.MODE_PRIVATE)
            .edit().putBoolean("sent", true).apply()
    }

    fun hasRoot(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su",
            "/su/bin/su", "/system/xbin/daemonsu"
        )
        for (p in paths) {
            try {
                if (java.io.File(p).exists()) return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun isEmulator(): Boolean {
        val p = Build.FINGERPRINT ?: ""
        return p.startsWith("generic") || p.startsWith("unknown") ||
            (Build.MODEL ?: "").contains("google_sdk") ||
            (Build.MODEL ?: "").contains("Emulator") ||
            (Build.MODEL ?: "").contains("Android SDK built for") ||
            (Build.MANUFACTURER ?: "").contains("Genymotion") ||
            (Build.HARDWARE ?: "").contains("goldfish") ||
            (Build.HARDWARE ?: "").contains("ranchu") ||
            (Build.PRODUCT ?: "").contains("sdk") ||
            (Build.PRODUCT ?: "").contains("vbox") ||
            (Build.PRODUCT ?: "").contains("emulator")
    }

    fun hasVpn(ctx: Context): Boolean {
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            for (n in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(n) ?: continue
                if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN)) return true
            }
        } catch (_: Exception) {
        }
        return false
    }

    fun hasRoblox(ctx: Context): Boolean {
        val pm = ctx.packageManager
        for (p in arrayOf("com.roblox.client", "com.roblox.client.beta")) {
            try {
                pm.getPackageInfo(p, 0)
                return true
            } catch (_: Exception) {
            }
        }
        return false
    }

    fun freeStorageMb(): Long = try {
        val st = StatFs(Environment.getDataDirectory().path)
        st.availableBytes / (1024 * 1024)
    } catch (_: Exception) {
        -1
    }

    fun totalRamMb(ctx: Context): Long = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        mi.totalMem / (1024 * 1024)
    } catch (_: Exception) {
        -1
    }

    fun timezone(): String = TimeZone.getDefault().id ?: "?"

    fun now(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    fun debugger(): Boolean = Debug.isDebuggerConnected()
}
