package com.example.scanner

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScoreRing(ctx: Context) : View(ctx) {
    var score = 0
    var ringColor = Colors.GREEN
    private val track = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        track.style = Paint.Style.STROKE
        track.color = Colors.STROKE
        arc.style = Paint.Style.STROKE
        arc.strokeCap = Paint.Cap.ROUND
        txt.color = Colors.TEXT
        txt.textAlign = Paint.Align.CENTER
        txt.typeface = Typeface.DEFAULT_BOLD
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val stroke = context.dp(9).toFloat()
        track.strokeWidth = stroke
        arc.strokeWidth = stroke
        arc.color = ringColor
        val pad = stroke / 2f + 1f
        val rect = RectF(pad, pad, width - pad, height - pad)
        canvas.drawArc(rect, 0f, 360f, false, track)
        if (score > 0) canvas.drawArc(rect, -90f, 360f * score / 100f, false, arc)
        txt.textSize = context.dp(26).toFloat()
        val y = height / 2f - (txt.ascent() + txt.descent()) / 2f
        canvas.drawText(score.toString(), width / 2f, y, txt)
    }
}

class Risk(val score: Int, val level: String, val color: Int, val reasons: List<String>)

fun computeRisk(r: ScanResult): Risk {
    var s = 0
    val why = ArrayList<String>()
    val apps = r.findings.count { it.kind == "APP" }
    val folders = r.findings.count { it.kind == "CARPETA" }
    val files = r.findings.count { it.kind == "ARCHIVO" }
    val sus = r.findings.count { it.kind == "SCRIPT" && ScanEngine.matches(it.path ?: "") }

    if (apps > 0) {
        s += 45 + minOf(apps - 1, 2) * 10
        why.add("App de ejecutor instalada ($apps)")
    }
    if (folders > 0) {
        s += minOf(folders, 2) * 20
        why.add("Carpetas con nombre de ejecutor ($folders)")
    }
    if (files > 0) {
        s += minOf(files, 2) * 8
        why.add("Archivos con nombre sospechoso ($files)")
    }
    if (sus > 0) {
        s += 20
        why.add("Scripts en rutas sospechosas ($sus)")
    }
    if (r.scriptTotal > 0) {
        s += 5
        why.add("Scripts .lua/.luau en total (${r.scriptTotal})")
    }
    if (apps > 0 && (folders > 0 || files > 0)) {
        s += 10
        why.add("Varias señales coinciden entre sí")
    }
    s = minOf(s, 100)

    return when {
        s == 0 -> Risk(s, "Sin señales", Colors.GREEN, why)
        s < 30 -> Risk(s, "Riesgo bajo", Colors.GREEN, why)
        s < 60 -> Risk(s, "Riesgo medio", Colors.AMBER, why)
        else -> Risk(s, "Riesgo alto", Colors.RED, why)
    }
}

class ReportView(
    private val act: Activity,
    private val r: ScanResult,
    val session: String
) {
    private val risk = computeRisk(r)
    private val timeMs = System.currentTimeMillis()
    val riskInfo: Risk get() = risk
    val riskColor: Int get() = risk.color
    val view: LinearLayout = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }

    private var extrasHolder: LinearLayout? = null
    private var listHolder: LinearLayout? = null
    private var filter = "ALL"
    private val chipViews = LinkedHashMap<String, TextView>()

    init {
        build()
    }

    fun refreshExtras() {
        extrasHolder?.let { Extras.fill(act, it) }
    }

    private fun build() {
        // Tarjeta de puntaje
        val sc = act.card()
        val top = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val ring = ScoreRing(act).apply {
            score = risk.score
            ringColor = risk.color
        }
        top.addView(ring, LinearLayout.LayoutParams(act.dp(96), act.dp(96)))
        val side = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        side.addView(act.label(risk.level, 18f, risk.color, true))
        side.addView(act.label("de 100 puntos de riesgo", 12f, Colors.MUTED), act.params(2))
        top.addView(side, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = act.dp(16) })
        sc.addView(top)

        if (risk.reasons.isEmpty()) {
            sc.addView(
                act.label("No se encontraron señales con las palabras clave actuales.", 12f, Colors.MUTED),
                act.params(12)
            )
        } else {
            sc.addView(act.label("Por qué este puntaje", 12f, Colors.MUTED, true), act.params(14))
            for (why in risk.reasons) {
                sc.addView(act.label("• $why", 12f, Colors.TEXT), act.params(4))
            }
        }
        view.addView(sc)

        // Métricas
        val m = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        m.addView(
            metric("Elementos revisados", "${r.checked}"),
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = act.dp(5) }
        )
        m.addView(
            metric("Scripts .lua/.luau", "${r.scriptTotal}"),
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = act.dp(5) }
        )
        view.addView(m, act.params(10))

        // Roblox y perfiles
        val holder = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        extrasHolder = holder
        view.addView(holder, act.params(10))
        Extras.fill(act, holder)

        // Filtros y lista
        view.addView(act.label("Hallazgos", 15f, Colors.MUTED, true), act.params(22))
        view.addView(buildChips(), act.params(10))
        val lh = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        listHolder = lh
        view.addView(lh, act.params(6))
        renderList()

        if (r.scriptTotal > 100) {
            view.addView(
                act.label("Se listan 100 de ${r.scriptTotal} scripts.", 12f, Colors.MUTED),
                act.params(10)
            )
        }
        view.addView(
            act.label(
                "Una coincidencia no es prueba definitiva: otra app o archivo puede llamarse " +
                    "\"Delta\", y algunos juegos legítimos usan archivos .lua. " +
                    "Confirma el código de sesión $session con quien verifica.",
                12f, Colors.MUTED
            ), act.params(18)
        )
        view.addView(
            act.pillButton("Compartir informe", Colors.ACCENT) { showShare() },
            act.params(18)
        )
    }

    private fun metric(name: String, value: String): LinearLayout {
        val c = act.card()
        c.addView(act.label(name, 11f, Colors.MUTED))
        c.addView(act.label(value, 20f, Colors.TEXT, true), act.params(2))
        return c
    }

    private fun buildChips(): HorizontalScrollView {
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val defs = listOf(
            Triple("ALL", "Todo", r.findings.size),
            Triple("APP", "Apps", r.findings.count { it.kind == "APP" }),
            Triple("DIR", "Carpetas", r.findings.count { it.kind == "CARPETA" || it.kind == "ARCHIVO" }),
            Triple("SCRIPT", "Scripts", r.findings.count { it.kind == "SCRIPT" })
        )
        for ((key, name, n) in defs) {
            val chip = act.label("$name $n", 13f, Colors.TEXT, true).apply {
                setPadding(act.dp(14), act.dp(8), act.dp(14), act.dp(8))
                setOnClickListener {
                    filter = key
                    styleChips()
                    renderList()
                }
            }
            chipViews[key] = chip
            row.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = act.dp(8) })
        }
        styleChips()
        return HorizontalScrollView(act).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun styleChips() {
        for ((k, v) in chipViews) {
            v.background = act.rounded(if (k == filter) Colors.ACCENT else Colors.SECONDARY, 20)
        }
    }

    private fun severity(f: Finding): Int = when (f.kind) {
        "APP", "CARPETA" -> 2
        "ARCHIVO" -> 1
        else -> if (ScanEngine.matches(f.path ?: "")) 1 else 0
    }

    private fun renderList() {
        val h = listHolder ?: return
        h.removeAllViews()
        val list = r.findings
            .filter {
                when (filter) {
                    "ALL" -> true
                    "APP" -> it.kind == "APP"
                    "DIR" -> it.kind == "CARPETA" || it.kind == "ARCHIVO"
                    else -> it.kind == "SCRIPT"
                }
            }
            .sortedByDescending { severity(it) }
        if (list.isEmpty()) {
            h.addView(act.label("Sin elementos en esta categoría.", 13f, Colors.MUTED), act.params(4))
            return
        }
        for (f in list) h.addView(findingCard(f), act.params(8))
    }

    private fun smallButton(text: String, onClick: () -> Unit): Button =
        act.pillButton(text, Colors.SECONDARY, onClick).apply {
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            setPadding(act.dp(8), act.dp(9), act.dp(8), act.dp(9))
        }

    private fun findingCard(f: Finding): LinearLayout {
        val sev = severity(f)
        val sevColor = when (sev) {
            2 -> Colors.RED
            1 -> Colors.AMBER
            else -> Colors.MUTED
        }
        val sevName = when (sev) {
            2 -> "Alta"
            1 -> "Media"
            else -> "Baja"
        }
        val kindName = when (f.kind) {
            "APP" -> "App instalada"
            "CARPETA" -> "Carpeta"
            "ARCHIVO" -> "Archivo"
            else -> "Script"
        }

        val c = act.card()
        val top = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = View(act).apply { background = act.rounded(sevColor, 6) }
        top.addView(dot, LinearLayout.LayoutParams(act.dp(8), act.dp(8)).apply { rightMargin = act.dp(10) })

        val titles = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(act.label(f.title, 15f, Colors.TEXT, true))
        titles.addView(act.label(kindName, 11f, Colors.MUTED), act.params(2))
        top.addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f))

        top.addView(act.label(sevName, 11f, Colors.BG, true).apply {
            background = act.rounded(sevColor, 8)
            setPadding(act.dp(8), act.dp(3), act.dp(8), act.dp(3))
        })
        c.addView(top)

        val sub = f.path ?: f.packageName ?: ""
        c.addView(
            act.label(sub, 11f, Colors.MUTED).apply { typeface = Typeface.MONOSPACE },
            act.params(8)
        )

        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        val half = { LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = act.dp(6) } }
        if (f.kind == "APP") {
            row.addView(smallButton("Ajustes de la app") { openAppSettings(f.packageName) }, half())
        } else {
            row.addView(smallButton("Abrir carpeta") { openFolder(f.path) }, half())
            row.addView(smallButton("Copiar ruta") { copyText(f.path, "Ruta copiada") }, half())
        }
        c.addView(row, act.params(12))
        return c
    }

    private fun openAppSettings(pkg: String?) {
        if (pkg == null) return
        try {
            act.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            )
        } catch (e: Exception) {
            Toast.makeText(act, "No se pudo abrir los ajustes", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFolder(path: String?) {
        if (path == null) return
        val file = File(path)
        val dir = if (file.isDirectory) file else file.parentFile ?: return
        val rootPath = Environment.getExternalStorageDirectory().absolutePath
        val rel = dir.absolutePath.removePrefix(rootPath).trim('/')
        val uri = Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3A" + Uri.encode(rel)
        )
        try {
            act.startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.document/directory")
            )
        } catch (e: Exception) {
            copyText(path, "No se pudo abrir; la ruta fue copiada")
        }
    }

    private fun copyText(text: String?, message: String) {
        if (text == null) return
        val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("loguen", text))
        Toast.makeText(act, message, Toast.LENGTH_SHORT).show()
    }

    private fun shareText(): String {
        val apps = r.findings.count { it.kind == "APP" }
        val files = r.findings.count { it.kind == "CARPETA" || it.kind == "ARCHIVO" }
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timeMs))
        return "Loguen · Informe\n" +
            "Sesión: $session\n" +
            "Dispositivo: ${PhoneStatus.model()} · ${PhoneStatus.android()}\n" +
            "Fecha: $date\n" +
            "Resultado: ${risk.score} · ${risk.level}\n" +
            "Apps: $apps · Carpetas y archivos: $files · Scripts: ${r.scriptTotal}\n" +
            "Elementos revisados: ${r.checked}"
    }

    private fun showShare() {
        val text = shareText()
        AlertDialog.Builder(act)
            .setTitle("Compartir informe")
            .setMessage(
                "Esto es exactamente lo que se compartirá. " +
                    "No incluye rutas ni nombres de archivos.\n\n$text"
            )
            .setPositiveButton("Copiar") { _, _ -> copyText(text, "Informe copiado") }
            .setNeutralButton("Enviar…") { _, _ ->
                val i = Intent(Intent.ACTION_SEND)
                i.type = "text/plain"
                i.putExtra(Intent.EXTRA_TEXT, text)
                act.startActivity(Intent.createChooser(i, "Enviar informe"))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}
