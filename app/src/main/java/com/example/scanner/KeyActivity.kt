package com.example.scanner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class KeyActivity : Activity() {

    companion object {
        var fails = 0
        var lockUntil = 0L
    }

    private lateinit var input: EditText
    private lateinit var msg: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Colors.BG
        window.navigationBarColor = Colors.BG

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(28))
        }

        root.addView(label("🔑", 44f).apply { gravity = Gravity.CENTER })
        root.addView(label("Acceso", 26f, bold = true).apply { gravity = Gravity.CENTER }, params(4))
        root.addView(
            label("Ingresa la clave que te dio el verificador. Vence a los 4 minutos.", 14f, Colors.MUTED)
                .apply { gravity = Gravity.CENTER },
            params(4)
        )

        val c = card()
        input = EditText(this).apply {
            hint = "Clave"
            setHintTextColor(Colors.MUTED)
            setTextColor(Colors.TEXT)
            textSize = 18f
            gravity = Gravity.CENTER
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            background = rounded(Colors.SECONDARY, 12)
            setPadding(dp(12), dp(14), dp(12), dp(14))
        }
        c.addView(input, params())
        msg = label("", 13f, Colors.RED).apply { gravity = Gravity.CENTER }
        c.addView(msg, params(10))
        c.addView(pillButton("Entrar", Colors.ACCENT) { submit() }, params(12))
        root.addView(c, params(24))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Colors.BG)
            isFillViewport = true
            addView(root)
        })
    }

    private fun show(text: String, color: Int = Colors.RED) {
        msg.setTextColor(color)
        msg.text = text
    }

    private fun submit() {
        val now = System.currentTimeMillis()
        if (now < lockUntil) {
            show("Demasiados intentos. Espera ${(lockUntil - now) / 1000 + 1} s")
            return
        }
        val raw = input.text.toString()
        val prefs = getSharedPreferences("keys", MODE_PRIVATE)
        val norm = KeyAuth.normalize(raw)

        val result = KeyAuth.check(raw)
        if (result == KeyResult.OK && prefs.contains(norm)) {
            show("Esa clave ya fue usada. Pide una nueva.")
            return
        }
        when (result) {
            KeyResult.OK -> {
                fails = 0
                prefs.edit().putLong(norm, now).apply()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            KeyResult.EXPIRED -> show("La clave venció. Pide una nueva.")
            KeyResult.CLOCK -> show("Revisa que la fecha y hora de tu celular sean automáticas.")
            KeyResult.INVALID -> {
                fails++
                if (fails >= 5) {
                    fails = 0
                    lockUntil = now + 60_000
                    show("Demasiados intentos. Espera 60 s.")
                } else {
                    show("Clave incorrecta.")
                }
            }
        }
    }
}
