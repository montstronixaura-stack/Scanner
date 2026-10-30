package com.example.scanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private val keywords = listOf(
        "delta", "codex", "arceus", "fluxus", "hydrogen", "vegax",
        "cryptic", "krnl", "platoboost", "executor", "exploit",
        "injector", "synapse", "jjsploit", "solara", "ronix",
        "macsploit", "scriptware", "xeno", "nezur", "trigon",
        "valyse", "evon", "roexec", "autoexec"
    )

    // Palabras cortas o comunes: solo coinciden si el nombre ES exactamente esto.
    private val exactNames = listOf("wave", "swift", "electron", "evon")

    // Extensiones de script que se reportan siempre.
    private val scriptExt = listOf("lua", "luau")

    private lateinit var consent: CheckBox
    private lateinit var fullBtn: Button
    private lateinit var appsBtn: Button
    private lateinit var status: TextView
    private lateinit var results: TextView

    private var pendingFullScan = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Escáner Anti-Cheat"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "\nEsta app revisa SOLO nombres y extensiones, no el contenido:\n" +
                "• Apps instaladas (nombre y paquete)\n" +
                "• Nombres de carpetas y archivos de todo el almacenamiento, " +
                "para buscar coincidencias con ejecutores conocidos y archivos de script (.lua, .luau)\n\n" +
                "No lee el contenido de tus archivos, no accede a datos de otras apps " +
                "y no envía nada por internet. El resultado se muestra solo en esta pantalla."
            textSize = 15f
        })

        consent = CheckBox(this).apply {
            text = "Acepto que se realice este escaneo"
            setOnCheckedChangeListener { _, _ -> refreshButtons() }
        }
        root.addView(consent)

        fullBtn = Button(this).apply {
            text = "Iniciar escaneo completo"
            setOnClickListener { onFullClicked() }
        }
        root.addView(fullBtn)

        appsBtn = Button(this).apply {
            text = "Escanear solo apps (sin permiso de archivos)"
            setOnClickListener { runScan(includeFiles = false) }
        }
        root.addView(appsBtn)

        status = TextView(this).apply { textSize = 14f; setPadding(0, pad, 0, pad) }
        root.addView(status)

        results = TextView(this).apply { textSize = 14f }
        root.addView(results)

        setContentView(ScrollView(this).apply { addView(root) })
        refreshButtons()
    }

    override fun onResume() {
        super.onResume()
        if (pendingFullScan && consent.isChecked && hasFilesPermission()) {
            pendingFullScan = false
            runScan(includeFiles = true)
        } else {
            refreshButtons()
        }
    }

    private fun hasFilesPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    private fun onFullClicked() {
        if (hasFilesPermission()) {
            runScan(includeFiles = true)
            return
        }
        pendingFullScan = true
        status.text = "Activa el permiso de archivos y vuelve a esta app. " +
            "El escaneo empezará solo."
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

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        pendingFullScan = false
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
            runScan(includeFiles = true)
        } else {
            status.text = "Permiso no concedido. Puedes escanear solo las apps."
        }
    }

    private fun refreshButtons() {
        fullBtn.isEnabled = consent.isChecked
        appsBtn.isEnabled = consent.isChecked
    }

    private fun matches(name: String): Boolean {
        val t = name.lowercase()
        return keywords.any { t.contains(it) } || exactNames.any { t == it }
    }

    @Suppress("DEPRECATION")
    private fun runScan(includeFiles: Boolean) {
        fullBtn.isEnabled = false
        appsBtn.isEnabled = false
        results.text = ""
        status.text = "Revisando apps instaladas..."

        Thread {
            val found = StringBuilder()
            val scripts = StringBuilder()
            var scriptCount = 0
            var checked = 0

            // 1) Apps instaladas
            val pm = packageManager
            for (app in pm.getInstalledApplications(0)) {
                checked++
                val label = pm.getApplicationLabel(app).toString()
                if (matches(label) || matches(app.packageName)) {
                    found.append("APP: $label (${app.packageName})\n")
                }
            }

            if (includeFiles) {
                // 2) Recorrido de todo el almacenamiento (solo nombres)
                val root = Environment.getExternalStorageDirectory()
                root.walkTopDown()
                    .onEnter { dir ->
                        // Android/data y Android/obb están bloqueadas: se omiten.
                        if (dir.parentFile == root && dir.name == "Android") return@onEnter false
                        checked++
                        if (dir != root && matches(dir.name)) {
                            found.append("CARPETA: ${dir.absolutePath}\n")
                            return@onEnter false // no entra a la carpeta
                        }
                        true
                    }
                    .forEach { f ->
                        if (!f.isFile) return@forEach
                        checked++
                        if (checked % 500 == 0) {
                            runOnUiThread { status.text = "Revisando archivos... ($checked revisados)" }
                        }
                        val ext = f.extension.lowercase()
                        if (ext in scriptExt) {
                            scriptCount++
                            if (scriptCount <= 100) scripts.append("SCRIPT: ${f.absolutePath}\n")
                        } else if (matches(f.name)) {
                            found.append("ARCHIVO: ${f.absolutePath}\n")
                        }
                    }
            }

            runOnUiThread {
                status.text = "Escaneo terminado. Elementos revisados: $checked"
                val out = StringBuilder()
                if (found.isNotEmpty()) out.append("Coincidencias:\n\n$found\n")
                if (scriptCount > 0) {
                    out.append("Scripts (.lua/.luau) encontrados: $scriptCount\n\n$scripts")
                    if (scriptCount > 100) out.append("... y ${scriptCount - 100} más\n")
                    out.append("\n")
                }
                if (out.isEmpty()) {
                    out.append("Sin coincidencias.")
                } else {
                    out.append(
                        "Nota: una coincidencia no es prueba definitiva " +
                            "(por ejemplo, otra app o archivo puede llamarse \"Delta\", " +
                            "y algunos juegos legítimos usan archivos .lua)."
                    )
                }
                results.text = out.toString()
                refreshButtons()
            }
        }.start()
    }
}
