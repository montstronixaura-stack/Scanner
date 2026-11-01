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
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var consent: Switch
    private lateinit var permChip: TextView
    private lateinit var permBtn: Button
    private lateinit var fullBtn: Button
    private lateinit var appsBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Colors.BG
        window.navigationBarColor = Colors.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(28))
        }

        // Encabezado
        root.addView(label("🛡️", 44f).apply { gravity = Gravity.CENTER })
        root.addView(label("Escáner Anti-Cheat", 26f, bold = true).apply {
            gravity = Gravity.CENTER
        }, params(4))
        root.addView(
            label("Revisión voluntaria y superficial de tu dispositivo", 14f, Colors.MUTED)
                .apply { gravity = Gravity.CENTER },
            params(4)
        )

        // Qué se revisa
        val info = card()
        info.addView(label("Qué se revisa", 16f, bold = true))
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
        root.addView(info, params(24))

        // Paso 1: consentimiento
        val step1 = card()
        step1.addView(label("Paso 1 · Consentimiento", 16f, bold = true))
        consent = Switch(this).apply {
            text = "Acepto que se realice este escaneo"
            setTextColor(Colors.TEXT)
            textSize = 14f
            setOnCheckedChangeListener { _, _ -> refresh() }
        }
        step1.addView(consent, params(12))
        root.addView(step1, params(14))

        // Paso 2: permiso
        val step2 = card()
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(
            label("Paso 2 · Permiso de archivos", 16f, bold = true),
            LinearLayout.LayoutParams(0, WRAP, 1f)
        )
        permChip = label("", 12f, Colors.BG, bold = true).apply {
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        head.addView(permChip, LinearLayout.LayoutParams(WRAP, WRAP))
        step2.addView(head)
        step2.addView(
            label(
                "Necesario para buscar carpetas y scripts. Se abre una pantalla de Ajustes: " +
                    "activa el interruptor y vuelve aquí. Es opcional: sin él solo se revisan las apps.",
                13f, Colors.MUTED
            ), params(8)
        )
        permBtn = pillButton("Dar permiso de archivos", Colors.SECONDARY) { requestFilesPermission() }
        step2.addView(permBtn, params(12))
        root.addView(step2, params(14))

        // Acciones
        fullBtn = pillButton("Iniciar escaneo completo", Colors.ACCENT) { launch(true) }
        root.addView(fullBtn, params(22))
        appsBtn = pillButton("Escanear solo apps", Colors.SECONDARY) { launch(false) }
        root.addView(appsBtn, params(10))

        setContentView(glassScreen(root))
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun hasFilesPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
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

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        refresh()
    }

    private fun refresh() {
        val ok = hasFilesPermission()
        permChip.text = if (ok) "✔ Concedido" else "Pendiente"
        permChip.background = rounded(if (ok) Colors.GREEN else Colors.AMBER, 20)
        permBtn.enable(!ok)
        fullBtn.enable(consent.isChecked && ok)
        appsBtn.enable(consent.isChecked)
    }

    private fun launch(includeFiles: Boolean) {
        startActivity(
            Intent(this, ResultsActivity::class.java).putExtra("files", includeFiles)
        )
    }
}
