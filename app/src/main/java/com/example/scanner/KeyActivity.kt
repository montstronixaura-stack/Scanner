package com.example.scanner

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
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
    private lateinit var timerText: TextView
    private lateinit var barTrack: FrameLayout
    private lateinit var barFill: View
    private val boxes = ArrayList<TextView>()
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            updateTimer()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Colors.BG
        window.navigationBarColor = Colors.BG
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(60), dp(20), dp(28))
        }

        val tile = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            background = rounded(Colors.ACCENT, 16)
        }
        tile.addView(label("🛡️", 28f))
        root.addView(tile, LinearLayout.LayoutParams(dp(60), dp(60)))

        root.addView(
            label("Verificación de acceso", 22f, bold = true).apply { gravity = Gravity.CENTER },
            params(16)
        )
        root.addView(
            label("Escribe la clave que te dio el verificador", 14f, Colors.MUTED)
                .apply { gravity = Gravity.CENTER },
            params(4)
        )

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (i in 0 until 14) {
            if (i == 6) {
                row.addView(
                    label("-", 16f, Colors.MUTED).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(dp(12), WRAP)
                )
            }
            val b = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 15f
                setTextColor(Colors.TEXT)
                setTypeface(typeface, Typeface.BOLD)
            }
            boxes.add(b)
            row.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                leftMargin = dp(1)
                rightMargin = dp(1)
            })
        }

        input = EditText(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.TRANSPARENT)
            isCursorVisible = false
            inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            val clean = InputFilter { source, start, end, _, _, _ ->
                val sb = StringBuilder()
                for (i in start until end) {
                    val ch = source[i]
                    if (ch.isLetterOrDigit()) sb.append(ch.uppercaseChar())
                }
                sb.toString()
            }
            filters = arrayOf(clean, InputFilter.LengthFilter(14))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    msg.text = ""
                    refreshBoxes()
                    updateTimer()
                }
            })
        }

        val keyArea = FrameLayout(this)
        keyArea.addView(row, FrameLayout.LayoutParams(MATCH, dp(44)))
        keyArea.addView(input, FrameLayout.LayoutParams(MATCH, dp(44)))
        root.addView(keyArea, params(28))

        barTrack = FrameLayout(this).apply { background = rounded(Colors.STROKE, 4) }
        barFill = View(this)
        barTrack.addView(barFill, FrameLayout.LayoutParams(0, MATCH))
        root.addView(barTrack, LinearLayout.LayoutParams(MATCH, dp(4)).apply {
            topMargin = dp(18)
            leftMargin = dp(8)
            rightMargin = dp(8)
        })

        timerText = label("", 13f, Colors.MUTED).apply { gravity = Gravity.CENTER }
        root.addView(timerText, params(8))

        msg = label("", 13f, Colors.RED).apply { gravity = Gravity.CENTER }
        root.addView(msg, params(10))

        root.addView(pillButton("Entrar", Colors.ACCENT) { submit() }, params(16))

        root.addView(
            label(
                "Pide la clave por Discord. Cada clave sirve una sola vez.",
                12f, Colors.MUTED
            ).apply { gravity = Gravity.CENTER },
            params(16)
        )

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Colors.BG)
            isFillViewport = true
            addView(root)
        })

        refreshBoxes()
        updateTimer()
        input.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun refreshBoxes() {
        val t = input.text.toString()
        for (i in boxes.indices) {
            val b = boxes[i]
            b.text = if (i < t.length) t[i].toString() else ""
            val active = i == t.length && t.length < 14
            b.background = rounded(Colors.SECONDARY, 8, if (active) Colors.ACCENT else null)
        }
    }

    private fun setBar(frac: Float, color: Int) {
        val w = (barTrack.width * frac.coerceIn(0f, 1f)).toInt()
        barFill.background = rounded(color, 4)
        barFill.layoutParams = FrameLayout.LayoutParams(w, MATCH)
    }

    private fun updateTimer() {
        val t = input.text.toString()
        if (t.length < 6) {
            timerText.setTextColor(Colors.MUTED)
            timerText.text = "La clave vence 4 minutos después de generarla"
            setBar(0f, Colors.MUTED)
            return
        }
        val exp = t.substring(0, 6).toLongOrNull(36)
        if (exp == null) {
            timerText.text = ""
            setBar(0f, Colors.MUTED)
            return
        }
        val left = exp - System.currentTimeMillis() / 1000
        if (left <= 0) {
            timerText.setTextColor(Colors.RED)
            timerText.text = "Clave vencida"
            setBar(0f, Colors.RED)
            return
        }
        val color = when {
            left > 60 -> Colors.GREEN
            left > 20 -> Colors.AMBER
            else -> Colors.RED
        }
        timerText.setTextColor(color)
        timerText.text = "Vence en ${left / 60}:${(left % 60).toString().padStart(2, '0')}"
        setBar(left / 240f, color)
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
