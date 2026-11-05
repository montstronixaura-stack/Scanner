package com.example.scanner

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private lateinit var glowTop: View
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout
    private val navViews = ArrayList<TextView>()

    private var tab = 0
    private var consentOn = false
    private var scanning = false
    private var report: ReportView? = null
    private var progressView: TextView? = null
    private var historyFilter = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Colors.BG
        window.navigationBarColor = Colors.BG

        val root = FrameLayout(this).apply { setBackgroundColor(Colors.BG) }

        glowTop = View(this).apply { background = glow(Colors.ACCENT, 380) }
        root.addView(
            glowTop,
            FrameLayout.LayoutParams(dp(380), dp(380), Gravity.TOP or Gravity.END).apply {
                topMargin = -dp(150)
                rightMargin = -dp(150)
            }
        )
        root.addView(
            View(this).apply { background = glow(Colors.GREEN, 340) },
            FrameLayout.LayoutParams(dp(340), dp(340), Gravity.BOTTOM or Gravity.START).apply {
                bottomMargin = -dp(150)
                leftMargin = -dp(150)
            }
        )

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(44), dp(20), dp(16))
        }
        scroll = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            addView(body)
        }
        column.addView(scroll, LinearLayout.LayoutParams(MATCH, 0, 1f))
        column.addView(
            buildNav(),
            LinearLayout.LayoutParams(MATCH, WRAP).apply {
                leftMargin = dp(14)
                rightMargin = dp(14)
                topMargin = dp(4)
                bottomMargin = dp(12)
            }
        )

        root.addView(column, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
        render()
    }

    override fun onResume() {
        super.onResume()
        if (tab == 0 || tab == 3) render() else report?.refreshExtras()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (tab != 0) {
            tab = 0
            render()
        } else {
            super.onBackPressed()
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        render()
    }

    // ---------- Barra inferior ----------

    private fun buildNav(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(Color.argb(46, 255, 255, 255), 22, Colors.STROKE)
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        val names = listOf("Inicio", "Informe", "Historial", "Más")
        for ((i, n) in names.withIndex()) {
            val t = label(n, 13f, Colors.MUTED, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(11), 0, dp(11))
                setOnClickListener {
                    tab = i
                    render()
                }
            }
            navViews.add(t)
            bar.addView(t, LinearLayout.LayoutParams(0, WRAP, 1f))
        }
        return bar
    }

    private fun styleNav() {
        for ((i, v) in navViews.withIndex()) {
            val on = i == tab
            v.setTextColor(if (on) Colors.TEXT else Colors.MUTED)
            v.background = if (on) rounded(withAlpha(Colors.ACCENT, 200), 16) else null
        }
    }

    private fun render() {
        progressView = null
        body.removeAllViews()
        styleNav()
        var tint = Colors.ACCENT
        when (tab) {
            0 -> buildHome()
            1 -> {
                buildReportTab()
                val rv = report
                if (rv != null && !scanning) tint = rv.riskColor
            }
            2 -> buildHistory()
            else -> buildMore()
        }
        glowTop.background = glow(tint, 380)
        scroll.scrollTo(0, 0)
    }

    // ---------- Inicio ----------

    private fun buildHome() {
        body.addView(terminalHeader("$ loguen --inicio", "Un check y listo"))

        val info = card()
        info.addView(label("Qué se revisa", 16f, Colors.TEXT, true))
        info.addView(
            label(
                "• Apps instaladas (nombre y paquete)\n" +
                    "• Nombres de carpetas y archivos del almacenamiento\n" +
                    "• Archivos de script (.lua, .luau)\n" +
                    "• Tiempo de uso de Roblox (últimos 7 días, con permiso aparte)\n" +
                    "• Perfiles secundarios (apps duales, carpeta segura)",
                14f, Colors.MUTED
            ), params(8)
        )
        info.addView(
            label(
                "No lee el contenido de tus archivos, no accede a datos de otras apps " +
                    "y no envía nada por internet. Los resultados se muestran solo en tu pantalla.",
                13f, Colors.MUTED
            ), params(10)
        )
        body.addView(info, params(28))

        val step1 = card()
        step1.addView(label("Paso 1 · Consentimiento", 16f, Colors.TEXT, true))
        val sw = Switch(this).apply {
            text = "Acepto que se realice este escaneo"
            setTextColor(Colors.TEXT)
            textSize = 14f
            isChecked = consentOn
        }
        step1.addView(sw, params(12))
        body.addView(step1, params(14))

        val step2 = card()
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            label("Paso 2 · Permiso de archivos", 16f, Colors.TEXT, true),
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        val chip = label("", 12f, Colors.BG, true).apply {
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        head.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP))
        step2.addView(head)
        step2.addView(
            label(
                "Necesario para buscar carpetas y scripts. Se abre una pantalla de Ajustes: " +
                    "activa el interruptor y vuelve aquí. Es opcional: sin él solo se revisan las apps.",
                13f, Colors.MUTED
            ), params(8)
        )
        val permBtn = pillButton("Dar permiso de archivos", Colors.SECONDARY) { requestFilesPermission() }
        step2.addView(permBtn, params(12))
        body.addView(step2, params(14))

        val fullBtn = pillButton("Iniciar escaneo completo", Colors.ACCENT) { startScan(true) }
        body.addView(fullBtn, params(22))
        val appsBtn = pillButton("Escanear solo apps", Colors.SECONDARY) { startScan(false) }
        body.addView(appsBtn, params(10))

        fun refreshButtons() {
            val ok = PhoneStatus.filesPermission(this)
            chip.text = if (ok) "✔ Concedido" else "Pendiente"
            chip.background = rounded(if (ok) Colors.GREEN else Colors.AMBER, 20)
            permBtn.enable(!ok)
            fullBtn.enable(consentOn && ok && !scanning)
            appsBtn.enable(consentOn && !scanning)
        }
        sw.setOnCheckedChangeListener { _, checked ->
            consentOn = checked
            refreshButtons()
        }
        refreshButtons()
    }

    private fun requestFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
    }

    private fun openUsageSettings() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (e: Exception) {
        }
    }

    // ---------- Escaneo ----------

    private fun startScan(includeFiles: Boolean) {
        if (scanning) return
        scanning = true
        report = null
        tab = 1
        render()
        Thread {
            val res = ScanEngine.scan(this, includeFiles) { n ->
                runOnUiThread { progressView?.text = "$n elementos revisados" }
            }
            runOnUiThread { if (!isFinishing) finishScan(res) }
        }.start()
    }

    private fun finishScan(res: ScanResult) {
        scanning = false
        val code = (1..5)
            .map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }
            .joinToString("")
        val rv = ReportView(this, res, code)
        report = rv
        val risk = rv.riskInfo
        History.add(
            this,
            HistoryEntry(
                System.currentTimeMillis(),
                code,
                risk.score,
                risk.level,
                res.findings.count { it.kind == "APP" },
                res.findings.count { it.kind == "CARPETA" || it.kind == "ARCHIVO" },
                res.scriptTotal,
                res.checked
            )
        )
        render()
    }

    // ---------- Informe ----------

    private fun buildReportTab() {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            label("Informe", 26f, Colors.TEXT, true),
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        val rv = report
        if (rv != null && !scanning) {
            header.addView(label("Sesión ${rv.session}", 12f, Colors.MUTED))
        }
        body.addView(header)

        if (scanning) {
            val pc = card()
            pc.addView(
                ProgressBar(this).apply { isIndeterminate = true },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL }
            )
            pc.addView(
                label("Escaneando...", 16f, Colors.TEXT, true).apply { gravity = Gravity.CENTER },
                params(12)
            )
            val pt = label("Preparando...", 13f, Colors.MUTED).apply { gravity = Gravity.CENTER }
            progressView = pt
            pc.addView(pt, params(4))
            body.addView(pc, params(18))
            return
        }

        if (rv == null) {
            val ec = card()
            ec.addView(label("Todavía no hay informe", 16f, Colors.TEXT, true))
            ec.addView(
                label("Haz un escaneo desde Inicio y el resultado aparecerá aquí.", 13f, Colors.MUTED),
                params(6)
            )
            ec.addView(
                pillButton("Ir a Inicio", Colors.ACCENT) {
                    tab = 0
                    render()
                },
                params(14)
            )
            body.addView(ec, params(18))
            return
        }

        body.addView(rv.view, params(18))
    }

    // ---------- Historial ----------

    private fun buildHistory() {
        body.addView(label("Historial", 26f, Colors.TEXT, true))
        body.addView(label("Se guarda solo en este teléfono.", 13f, Colors.MUTED), params(4))
        val all = History.list(this)

        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val defs = listOf("ALL" to "Todos", "HIGH" to "Riesgo alto", "CLEAN" to "Sin señales")
        for ((key, name) in defs) {
            val chip = label(name, 13f, Colors.TEXT, true).apply {
                setPadding(dp(14), dp(8), dp(14), dp(8))
                background = rounded(if (key == historyFilter) Colors.ACCENT else Colors.SECONDARY, 20)
                setOnClickListener {
                    historyFilter = key
                    render()
                }
            }
            chips.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(8) })
        }
        body.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(chips)
            },
            params(14)
        )

        val list = all.filter {
            when (historyFilter) {
                "HIGH" -> it.score >= 60
                "CLEAN" -> it.score == 0
                else -> true
            }
        }
        if (list.isEmpty()) {
            val msg = if (all.isEmpty()) "Aún no hay escaneos guardados." else "Sin resultados con este filtro."
            body.addView(label(msg, 13f, Colors.MUTED), params(18))
        }
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        for (e in list) body.addView(historyCard(e, fmt), params(10))

        if (all.isNotEmpty()) {
            body.addView(
                pillButton("Borrar historial", Colors.SECONDARY) { confirmClear() },
                params(22)
            )
        }
    }

    private fun historyCard(e: HistoryEntry, fmt: SimpleDateFormat): LinearLayout {
        val color = when {
            e.score >= 60 -> Colors.RED
            e.score >= 30 -> Colors.AMBER
            else -> Colors.GREEN
        }
        val c = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            View(this).apply { background = rounded(color, 4) },
            LinearLayout.LayoutParams(dp(5), dp(44)).apply { rightMargin = dp(12) }
        )
        val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        mid.addView(label("Sesión ${e.session}", 15f, Colors.TEXT, true))
        mid.addView(label(fmt.format(Date(e.time)), 11f, Colors.MUTED), params(2))
        mid.addView(
            label("${e.apps} apps · ${e.files} archivos · ${e.scripts} scripts", 11f, Colors.MUTED),
            params(2)
        )
        row.addView(mid, LinearLayout.LayoutParams(0, WRAP, 1f))
        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        right.addView(label("${e.score}", 22f, color, true))
        right.addView(label(e.level, 11f, color))
        row.addView(right)
        c.addView(row)
        return c
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Borrar historial")
            .setMessage("Se borrarán todos los escaneos guardados en este teléfono.")
            .setPositiveButton("Borrar") { _, _ ->
                History.clear(this)
                render()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------- Más ----------

    private fun kv(name: String, value: String, valueColor: Int): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(label(name, 13f, Colors.MUTED), LinearLayout.LayoutParams(0, WRAP, 1f))
        row.addView(
            label(value, 13f, valueColor, true).apply { gravity = Gravity.END },
            LinearLayout.LayoutParams(0, WRAP, 1.4f)
        )
        return row
    }

    private fun permRow(name: String, granted: Boolean, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(label(name, 14f, Colors.TEXT), LinearLayout.LayoutParams(0, WRAP, 1f))
        val chip = label(if (granted) "✔ Concedido" else "Pendiente", 12f, Colors.BG, true).apply {
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = rounded(if (granted) Colors.GREEN else Colors.AMBER, 20)
            if (!granted) setOnClickListener { onClick() }
        }
        row.addView(chip)
        return row
    }

    private fun buildMore() {
        body.addView(label("Más", 26f, Colors.TEXT, true))

        val phone = card()
        phone.addView(label("Estado del teléfono", 16f, Colors.TEXT, true))
        phone.addView(kv("Dispositivo", PhoneStatus.model(), Colors.TEXT), params(10))
        phone.addView(kv("Sistema", PhoneStatus.android(), Colors.TEXT), params(6))
        val at = PhoneStatus.autoTime(this)
        val atText = if (at == null) "No disponible" else if (at) "Sí" else "No"
        phone.addView(
            kv("Hora automática", atText, if (at == false) Colors.AMBER else Colors.TEXT),
            params(6)
        )
        val rb = PhoneStatus.robloxVersion(this)
        phone.addView(
            kv("Roblox", if (rb == null) "No instalado" else "Instalado ($rb)", Colors.TEXT),
            params(6)
        )
        body.addView(phone, params(18))

        val perm = card()
        perm.addView(label("Permisos", 16f, Colors.TEXT, true))
        perm.addView(
            permRow("Acceso a archivos", PhoneStatus.filesPermission(this)) { requestFilesPermission() },
            params(12)
        )
        perm.addView(
            permRow("Uso de apps (Roblox)", PhoneStatus.usageAccess(this)) { openUsageSettings() },
            params(10)
        )
        perm.addView(
            label(
                "Toca «Pendiente» para activarlo en Ajustes. Puedes quitar los permisos cuando quieras.",
                12f, Colors.MUTED
            ), params(10)
        )
        body.addView(perm, params(14))

        val priv = card()
        priv.addView(label("Privacidad", 16f, Colors.TEXT, true))
        priv.addView(
            label(
                "• Solo lee nombres de apps, carpetas y archivos, y las extensiones de script.\n" +
                    "• No lee el contenido de tus archivos, historiales ni datos de otras apps.\n" +
                    "• Esta app no tiene permiso de internet: nada sale de tu teléfono.\n" +
                    "• El informe solo se comparte si tú tocas «Compartir informe» y confirmas.",
                13f, Colors.MUTED
            ), params(8)
        )
        body.addView(priv, params(14))

        val about = card()
        about.addView(label("Acerca de", 16f, Colors.TEXT, true))
        about.addView(kv("Versión", PhoneStatus.appVersion(this), Colors.TEXT), params(10))
        about.addView(label("Un check y listo", 12f, Colors.MUTED), params(8))
        body.addView(about, params(14))
    }
}
