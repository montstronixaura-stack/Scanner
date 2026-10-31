package com.example.scanner

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class ResultsActivity : Activity() {

    private lateinit var content: LinearLayout
    private lateinit var progressCard: LinearLayout
    private lateinit var progressText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Colors.BG
        window.navigationBarColor = Colors.BG

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(36), dp(20), dp(28))
        }
        content.addView(label("Resultados", 26f, bold = true))

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
            val result = ScanEngine.scan(this, includeFiles) { n ->
                runOnUiThread { progressText.text = "$n elementos revisados" }
            }
            runOnUiThread { if (!isFinishing) render(result) }
        }.start()
    }

    private fun render(r: ScanResult) {
        content.removeView(progressCard)

        val apps = r.findings.count { it.kind == "APP" }
        val files = r.findings.count { it.kind == "CARPETA" || it.kind == "ARCHIVO" }
        val total = apps + files

        val summary = card()
        val verdictColor = if (total == 0 && r.scriptTotal == 0) Colors.GREEN else Colors.AMBER
        val verdict = when {
            total > 0 -> "Se encontraron $total coincidencias"
            r.scriptTotal > 0 -> "Sin coincidencias, pero hay scripts"
            else -> "Sin coincidencias"
        }
        summary.addView(label(verdict, 18f, verdictColor, bold = true))
        summary.addView(label("${r.checked} elementos revisados", 13f, Colors.MUTED), params(2))

        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(stat(apps, "Apps"), LinearLayout.LayoutParams(0, WRAP, 1f))
        stats.addView(stat(files, "Carpetas/archivos"), LinearLayout.LayoutParams(0, WRAP, 1f))
        stats.addView(stat(r.scriptTotal, "Scripts"), LinearLayout.LayoutParams(0, WRAP, 1f))
        summary.addView(stats, params(14))
        content.addView(summary, params(18))

        section("Apps instaladas", "APP", r)
        section("Carpetas y archivos sospechosos", "CARPETA", r)
        section("Archivos de script (.lua / .luau)", "SCRIPT", r)

        if (r.scriptTotal > 100) {
            content.addView(
                label("Se muestran 100 de ${r.scriptTotal} scripts.", 12f, Colors.MUTED),
                params(8)
            )
        }

        content.addView(
            label(
                "Una coincidencia no es prueba definitiva: otra app o archivo puede llamarse " +
                    "\"Delta\", y algunos juegos legítimos usan archivos .lua.",
                12f, Colors.MUTED
            ), params(18)
        )
        content.addView(pillButton("Volver", Colors.ACCENT) { finish() }, params(18))
    }

    private fun stat(n: Int, name: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(label("$n", 24f, if (n > 0) Colors.AMBER else Colors.GREEN, bold = true))
            addView(label(name, 11f, Colors.MUTED))
        }

    private fun section(title: String, kind: String, r: ScanResult) {
        val list = r.findings.filter {
            if (kind == "CARPETA") it.kind == "CARPETA" || it.kind == "ARCHIVO" else it.kind == kind
        }
        if (list.isEmpty()) return
        content.addView(label(title, 15f, Colors.MUTED, bold = true), params(22))
        for (f in list) content.addView(findingCard(f), params(10))
    }

    private fun findingCard(f: Finding): LinearLayout {
        val c = card()
        val tagColor = when (f.kind) {
            "APP" -> Colors.ACCENT
            "SCRIPT" -> Colors.AMBER
            else -> Colors.RED
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(label(f.kind, 10f, Colors.BG, bold = true).apply {
            background = rounded(tagColor, 8)
            setPadding(dp(8), dp(3), dp(8), dp(3))
        }, LinearLayout.LayoutParams(WRAP, WRAP).apply { rightMargin = dp(10) })
        top.addView(label(f.title, 15f, bold = true), LinearLayout.LayoutParams(0, WRAP, 1f))
        c.addView(top)

        val sub = f.path ?: f.packageName ?: ""
        c.addView(label(sub, 11f, Colors.MUTED).apply { typeface = Typeface.MONOSPACE }, params(8))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val half = { LinearLayout.LayoutParams(0, WRAP, 1f).apply { rightMargin = dp(6) } }
        if (f.kind == "APP") {
            row.addView(pillButton("Ajustes de la app", Colors.SECONDARY) {
                openAppSettings(f.packageName)
            }, half())
        } else {
            row.addView(pillButton("Abrir carpeta", Colors.SECONDARY) { openFolder(f.path) }, half())
            row.addView(pillButton("Copiar ruta", Colors.SECONDARY) { copy(f.path) }, half())
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
