package com.example.scanner

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

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

class ResultsActivity : Activity() {

    private lateinit var content: LinearLayout
    private lateinit var progressCard: LinearLayout
    private lateinit var progressText: TextView
    private var extrasHolder: LinearLayout? = null
    private var listHolder: LinearLayout? = null
    private var result: ScanResult? = null
    private var filter = "ALL"
    private val chipViews = LinkedHashMap<String, TextView>()

    private val sessionCode = (1..5)
        .map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }
        .joinToString("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Colors.BG
        window.navigationBarColor = Colors.BG

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(28))
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            label("Informe", 26f, bold = true),
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        header.addView(label("Sesión $sessionCode", 12f, Colors.MUTED))
        content.addView(header)

        progressCard = card()
        progressCard.addView(
            ProgressBar(this).apply { isIndeterminate = true },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { gravity = Gravity.CENTER_HORIZONTAL }
        )
        progressCard.addView(label("Escaneando...", 16f, bold = true).apply {
            gravity = Gravity.CENTER
        }, params(12))
        progressText = label("Preparando...", 13f, Colors.MUTED).apply { gravity = Gravity.CENTER }
        progressCard.addView(progressText, params(4))
        content.addView(progressCard, params(18))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Colors.BG)
            addView(content)
        })

        val includeFiles = intent.getBooleanExtra("files", false)
        Thread {
            val res = ScanEngine.scan(this, includeFiles) { n ->
                runOnUiThread { progressText.text = "$n elementos revisados" }
            }
            runOnUiThread { if (!isFinishing) render(res) }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        extrasHolder?.let { Extras.fill(this, it) }
    }

    private fun render(r: ScanResult) {
        result = r
        content.removeView(progressCard)
        val risk = computeRisk(r)

        // Tarjeta de puntaje
        val sc = card()
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val ring = ScoreRing(this).apply {
            score = risk.score
            ringColor = risk.color
        }
        top.addView(ring, LinearLayout.LayoutParams(dp(96), dp(96)))
        val side = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        side.addView(label(risk.level, 18f, risk.color, bold = true))
        side.addView(label("de 100 puntos de riesgo", 12f, Colors.MUTED), params(2))
        top.addView(side, LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(16) })
        sc.addView(top)

        if (risk.reasons.isEmpty()) {
            sc.addView(
                label("No se encontraron señales con las palabras clave actuales.", 12f, Colors.MUTED),
                params(12)
            )
        } else {
            sc.addView(label("Por qué este puntaje", 12f, Colors.MUTED, bold = true), params(14))
            for (why in risk.reasons) {
                sc.addView(label("• $why", 12f, Colors.TEXT), params(4))
            }
        }
        content.addView(sc, params(18))

        // Métricas
        val m = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        m.addView(
            metric("Elementos revisados", "${r.checked}"),
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(5) }
        )
        m.addView(
            metric("Scripts .lua/.luau", "${r.scriptTotal}"),
            LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = dp(5) }
        )
        content.addView(m, params(10))

        // Roblox y perfiles
        val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        extrasHolder = holder
        content.addView(holder, params(10))
        Extras.fill(this, holder)

        // Filtros y lista
        content.addView(label("Hallazgos", 15f, Colors.MUTED, bold = true), params(22))
        content.addView(buildChips(r), params(10))
        val lh = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listHolder = lh
        content.addView(lh, params(6))
        renderList()

        if (r.scriptTotal > 100) {
            content.addView(
                label("Se listan 100 de ${r.scriptTotal} scripts.", 12f, Colors.MUTED),
                params(10)
            )
        }
        content.addView(
            label(
                "Una coincidencia no es prueba definitiva: otra app o archivo puede llamarse " +
                    "\"Delta\", y algunos juegos legítimos usan archivos .lua. " +
                    "Confirma el código de sesión $sessionCode con quien verifica.",
                12f, Colors.MUTED
            ), params(18)
        )
        content.addView(pillButton("Volver", Colors.ACCENT) { finish() }, params(18))
    }

    private fun metric(name: String, value: String): LinearLayout {
        val c = card()
        c.addView(label(name, 11f, Colors.MUTED))
        c.addView(label(value, 20f, bold = true), params(2))
        return c
    }

    private fun buildChips(r: ScanResult): HorizontalScrollView {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val defs = listOf(
            Triple("ALL", "Todo", r.findings.size),
            Triple("APP", "Apps", r.findings.count { it.kind == "APP" }),
            Triple("DIR", "Carpetas", r.findings.count { it.kind == "CARPETA" || it.kind == "ARCHIVO" }),
            Triple("SCRIPT", "Scripts", r.findings.count { it.kind == "SCRIPT" })
        )
        for ((key, name, n) in defs) {
            val chip = label("$name $n", 13f, Colors.TEXT, bold = true).apply {
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener {
                    filter = key
                    styleChips()
                    renderList()
                }
            }
            chipViews[key] = chip
            row.addView(chip, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(8) })
        }
        styleChips()
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun styleChips() {
        for ((k, v) in chipViews) {
            v.background = rounded(if (k == filter) Colors.ACCENT else Colors.SECONDARY, 20)
        }
    }

    private fun severity(f: Finding): Int = when (f.kind) {
        "APP", "CARPETA" -> 2
        "ARCHIVO" -> 1
        else -> if (ScanEngine.matches(f.path ?: "")) 1 else 0
    }

    private fun renderList() {
        val h = listHolder ?: return
        val r = result ?: return
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
            h.addView(label("Sin elementos en esta categoría.", 13f, Colors.MUTED), params(4))
            return
        }
        for (f in list) h.addView(findingCard(f), params(8))
    }

    private fun smallButton(text: String, onClick: () -> Unit): Button =
        pillButton(text, Colors.SECONDARY, onClick).apply {
            textSize = 12f
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), dp(9), dp(8), dp(9))
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

        val c = card()
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = View(this).apply { background = rounded(sevColor, 6) }
        top.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(10) })

        val titles = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(label(f.title, 15f, bold = true))
        titles.addView(label(kindName, 11f, Colors.MUTED), params(2))
        top.addView(titles, LinearLayout.LayoutParams(0, WRAP, 1f))

        top.addView(label(sevName, 11f, Colors.BG, bold = true).apply {
            background = rounded(sevColor, 8)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        })
        c.addView(top)

        val sub = f.path ?: f.packageName ?: ""
        c.addView(
            label(sub, 11f, Colors.MUTED).apply { typeface = Typeface.MONOSPACE },
            params(8)
        )

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val half = { LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(6) } }
        if (f.kind == "APP") {
            row.addView(smallButton("Ajustes de la app") { openAppSettings(f.packageName) }, half())
        } else {
            row.addView(smallButton("Abrir carpeta") { openFolder(f.path) }, half())
            row.addView(smallButton("Copiar ruta") { copy(f.path) }, half())
        }
        c.addView(row, params(12))
        return c
    }

    private fun openAppSettings(pkg: String?) {
        if (pkg == null) return
        try {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            )
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir los ajustes", Toast.LENGTH_SHORT).show()
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
            startActivity(
                Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.document/directory")
            )
        } catch (e: Exception) {
            copy(path)
            Toast.makeText(
                this, "No se pudo abrir; la ruta fue copiada", Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun copy(path: String?) {
        if (path == null) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ruta", path))
        Toast.makeText(this, "Ruta copiada", Toast.LENGTH_SHORT).show()
    }
}
