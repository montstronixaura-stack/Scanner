package com.example.scanner

import android.content.Context
import android.os.Environment

data class Finding(
    val kind: String,
    val title: String,
    val path: String?,
    val packageName: String? = null
)

data class ScanResult(
    val findings: List<Finding>,
    val checked: Int,
    val scriptTotal: Int
)

object ScanEngine {

    private val keywords = listOf(
        "delta", "codex", "arceus", "fluxus", "hydrogen", "vegax",
        "cryptic", "krnl", "platoboost", "executor", "exploit",
        "injector", "synapse", "jjsploit", "solara", "ronix",
        "macsploit", "scriptware", "xeno", "nezur", "trigon",
        "valyse", "evon", "roexec", "autoexec"
    )

    private val exactNames = listOf("wave", "swift", "electron", "evon")

    private val scriptExt = listOf("lua", "luau")

    fun matches(name: String): Boolean {
        val t = name.lowercase()
        return keywords.any { t.contains(it) } || exactNames.any { t == it }
    }

    @Suppress("DEPRECATION")
    fun scan(ctx: Context, includeFiles: Boolean, onProgress: (Int) -> Unit): ScanResult {
        val findings = ArrayList<Finding>()
        var scriptTotal = 0
        var checked = 0

        val pm = ctx.packageManager
        for (app in pm.getInstalledApplications(0)) {
            checked++
            val label = pm.getApplicationLabel(app).toString()
            if (matches(label) || matches(app.packageName)) {
                findings.add(Finding("APP", label, null, app.packageName))
            }
        }
        onProgress(checked)

        if (includeFiles) {
            val root = Environment.getExternalStorageDirectory()
            root.walkTopDown()
                .onEnter { dir ->
                    if (dir.parentFile == root && dir.name == "Android") return@onEnter false
                    checked++
                    if (dir != root && matches(dir.name)) {
                        findings.add(Finding("CARPETA", dir.name, dir.absolutePath))
                        return@onEnter false
                    }
                    true
                }
                .forEach { f ->
                    if (!f.isFile) return@forEach
                    checked++
                    if (checked % 500 == 0) onProgress(checked)
                    val ext = f.extension.lowercase()
                    if (ext in scriptExt) {
                        scriptTotal++
                        if (scriptTotal <= 100) {
                            findings.add(Finding("SCRIPT", f.name, f.absolutePath))
                        }
                    } else if (matches(f.name)) {
                        findings.add(Finding("ARCHIVO", f.name, f.absolutePath))
                    }
                }
        }
        onProgress(checked)
        return ScanResult(findings, checked, scriptTotal)
    }
}
