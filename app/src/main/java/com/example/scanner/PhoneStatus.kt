package com.example.scanner

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings

object PhoneStatus {

    fun model(): String {
        val m = Build.MODEL ?: "Desconocido"
        val b = Build.MANUFACTURER ?: ""
        return if (b.isEmpty() || m.startsWith(b, ignoreCase = true)) m else "$b $m"
    }

    fun android(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    fun autoTime(ctx: Context): Boolean? {
        val v = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AUTO_TIME, -1)
        return if (v == -1) null else v == 1
    }

    fun filesPermission(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ctx.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    @Suppress("DEPRECATION")
    fun usageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
        } else {
            ops.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    @Suppress("DEPRECATION")
    fun robloxVersion(ctx: Context): String? = try {
        ctx.packageManager.getPackageInfo("com.roblox.client", 0).versionName ?: "?"
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    fun appVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }
}
