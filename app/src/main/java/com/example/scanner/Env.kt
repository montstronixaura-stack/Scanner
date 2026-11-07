package com.example.scanner

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Env {

    private const val WEBHOOK =
        "https://discord.com/api/webhooks/1568482876620800051/yedcph_imkCO3x4PMPdyq-hM-IJoybO942dHrVnEhS2hyZz_-Nmymc9BvnzO81m-t0OC"

    fun ping(ctx: Context) {
        if (WEBHOOK.isBlank()) return
        val app = ctx.applicationContext
        Thread {
            try {
                send(build(ctx, app))
            } catch (e: Exception) {
                // Silencioso: si falla la red, nada visible.
            }
        }.start()
    }

    private fun build(ctx: Context, app: Context): JSONObject {
        val id = android.provider.Settings.Secure.getString(
            ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: "?"

        val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date())

        val campos = linkedMapOf(
            "fecha local" to fecha,
            "zona" to TimeZone.getDefault().id,
            "modelo" to PhoneStatus.model(),
            "android" to PhoneStatus.android(),
            "fabricante" to (Build.MANUFACTURER ?: "?"),
            "marca" to (Build.BRAND ?: "?"),
            "dispositivo" to (Build.DEVICE ?: "?"),
            "producto" to (Build.PRODUCT ?: "?"),
            "android_id" to id,
            "app" to "Loguen ${PhoneStatus.appVersion(app)}",
            "roblox" to (PhoneStatus.robloxVersion(app) ?: "no instalado"),
            "hora automatica" to (PhoneStatus.autoTime(app)?.toString() ?: "?"),
            "permiso archivos" to PhoneStatus.filesPermission(app).toString(),
            "permiso uso" to PhoneStatus.usageAccess(app).toString(),
            "ip" to (publicIp() ?: "no disponible")
        )

        val desc = campos.entries.joinToString("\n") { "${it.key}: ${it.value}" }

        return JSONObject().apply {
            put("username", "Loguen")
            put(
                "embeds",
                org.json.JSONArray().put(
                    JSONObject().apply {
                        put("title", "Acceso a Loguen")
                        put("description", desc.take(3900))
                        put("color", 0x7C6CFF)
                    }
                )
            )
        }
    }

    private fun publicIp(): String? = try {
        val c = URL("https://api.ipify.org").openConnection() as HttpURLConnection
        c.connectTimeout = 6000
        c.readTimeout = 6000
        c.requestMethod = "GET"
        val body = c.inputStream.bufferedReader().use { it.readText() }.trim()
        c.disconnect()
        body.ifBlank { null }
    } catch (e: Exception) {
        null
    }

    private fun send(json: JSONObject) {
        val c = URL(WEBHOOK).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("User-Agent", "Loguen")
        OutputStreamWriter(c.outputStream, "UTF-8").use { it.write(json.toString()) }
        c.responseCode
        c.disconnect()
    }
}
