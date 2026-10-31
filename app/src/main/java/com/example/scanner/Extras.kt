package com.example.scanner

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Typeface
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object Extras {

    private const val ROBLOX = "com.roblox.client"

    private fun fmt(min: Long): String =
        if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"

    @Suppress("DEPRECATION")
    private fun hasUsageAccess(ctx: Context): Boolean {
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

    private fun robloxInstalled(ctx: Context): Boolean = try {
        ctx.packageManager.getPackageInfo(ROBLOX, 0)
        true
    } catch (e: Exception) {
        false
    }

    private fun dailyMinutes(ctx: Context): List<Pair<String, Long>> {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val dayFmt = SimpleDateFormat("EEE d MMM", Locale("es"))
        val out = ArrayList<Pair<String, Long>>()
        for (i in 6 downTo 0) {
            val start = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -i)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
            val stats = usm.queryAndAggregateUsageStats(
                start.timeInMillis,
                minOf(end.timeInMillis, System.currentTimeMillis())
            )
            val ms = stats[ROBLOX]?.totalTimeInForeground ?: 0L
            out.add(dayFmt.format(start.time) to ms / 60000)
        }
        return out
    }

    private class ProfileScan(val userId: Int, val others: Int, val matches: List<String>)

    private fun scanProfiles(ctx: Context): ProfileScan {
        val userId = Process.myUid() / 100000
        val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val me = Process.myUserHandle()
        val others = la.profiles.filter { it != me }
        val matches = ArrayList<String>()
        for (p in others) {
            try {
                for (a in la.getActivityList(null, p)) {
                    val label = a.label.toString()
                    val pkg = a.applicationInfo.packageName
                    if (ScanEngine.matches(label) || ScanEngine.matches(pkg)) {
                        matches.add("$label ($pkg)")
                    }
                }
            } catch (e: Exception) {
            }
        }
        return ProfileScan(userId, others.size, matches)
    }

    fun fill(act: Activity, holder: LinearLayout) {
        holder.removeAllViews()
        holder.addView(robloxCard(act), act.params())
        holder.addView(profilesCard(act), act.params(10))
    }

    private fun robloxCard(act: Activity): LinearLayout {
        val c = act.card()
        c.addView(act.label("Tiempo de pantalla de Roblox", 15f, bold = true))
        when {
            !robloxInstalled(act) -> {
                c.addView(
                    act.label("Roblox no está instalado en este teléfono.", 13f, Colors.AMBER),
                    act.params(8)
                )
            }
            !hasUsageAccess(act) -> {
                c.addView(
                    act.label(
                        "Para ver cuánto se usó Roblox en este teléfono (últimos 7 días) hace " +
                            "falta el permiso \"Acceso a datos de uso\". Se activa en Ajustes y " +
                            "solo lo lee esta app.",
                        13f, Colors.MUTED
                    ), act.params(8)
                )
                c.addView(act.pillButton("Dar acceso de uso", Colors.SECONDARY) {
                    try {
                        act.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (e: Exception) {
                    }
                }, act.params(12))
            }
            else -> {
                val days = dailyMinutes(act)
                val total = days.sumOf { it.second }
                c.addView(
                    act.label(
                        "Total 7 días: ${fmt(total)}  ·  promedio ${fmt(total / 7)} por día",
                        13f, Colors.TEXT
                    ), act.params(8)
                )
                for ((d, m) in days) {
                    c.addView(
                        act.label("$d   ${fmt(m)}", 12f, Colors.MUTED)
                            .apply { typeface = Typeface.MONOSPACE },
                        act.params(4)
                    )
                }
                c.addView(
                    act.label(
                        "Solo cuenta este teléfono. Compáralo con las horas que la persona dice jugar.",
                        12f, Colors.MUTED
                    ), act.params(10)
                )
            }
        }
        return c
    }

    private fun profilesCard(act: Activity): LinearLayout {
        val c = act.card()
        c.addView(act.label("Espacios y perfiles", 15f, bold = true))
        val p = scanProfiles(act)

        if (p.userId != 0) {
            c.addView(
                act.label(
                    "⚠ Esta app se está ejecutando en un espacio secundario (usuario ${p.userId}). " +
                        "Este escaneo NO cubre el espacio principal: hay que repetirlo allí.",
                    13f, Colors.AMBER
                ), act.params(8)
            )
        }
        if (p.others > 0) {
            c.addView(
                act.label(
                    "Se detectaron ${p.others} perfil(es) adicional(es) " +
                        "(apps duales, carpeta segura o perfil de trabajo).",
                    13f, Colors.TEXT
                ), act.params(8)
            )
            if (p.matches.isEmpty()) {
                c.addView(
                    act.label("Sin coincidencias en sus apps.", 13f, Colors.GREEN),
                    act.params(6)
                )
            } else {
                for (m in p.matches) {
                    c.addView(act.label("• $m", 13f, Colors.RED), act.params(6))
                }
            }
        } else {
            c.addView(
                act.label("No se detectaron perfiles adicionales accesibles.", 13f, Colors.MUTED),
                act.params(8)
            )
        }
        c.addView(
            act.label(
                "Un \"Segundo espacio\" completo (Xiaomi y otras marcas) es un usuario aparte y " +
                    "no es visible desde aquí. Pide a la persona que muestre Ajustes → " +
                    "Funciones especiales → Segundo espacio (o Ajustes → Sistema → Varios usuarios).",
                12f, Colors.MUTED
            ), act.params(10)
        )
        return c
    }
}
