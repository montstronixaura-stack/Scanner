package com.example.scanner

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
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

    private val cBg = Color.parseColor("#0B0E14")
    private val cText = Color.parseColor("#F2F4F8")
    private val cMuted = Color.parseColor("#9AA3B8")
    private val cAccent = Color.parseColor("#7C6CFF")
    private val cGreen = Color.parseColor("#2ECC8F")
    private val cRed = Color.parseColor("#FF5C6C")
    private val cAmber = Color.parseColor("#FFB84D")
    private val cGlass = Color.argb(36, 255, 255, 255)
    private val cStroke = Color.argb(46, 255, 255, 255)

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var input: EditText
    private lateinit var msg: TextView
    private lateinit var timerText: TextView
    private lateinit var barTrack: FrameLayout
    private lateinit var barFill: View
    private lateinit var cursor: TextView
    private var blink: ObjectAnimator? = null
    private val boxes = ArrayList<TextView>()
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            updateTimer()
            handler.postDelayed(this, 500)
        }
    }

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun rr(color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = px(radiusDp).toFloat()
            if (stroke != null) setStroke(maxOf(1, px(1) / 2), stroke)
        }

    private fun glow(color: Int, sizeDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = px(sizeDp / 2).toFloat()
            setGradientCenter(0.5f, 0.5f)
            colors = intArrayOf(
                Color.argb(120, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
            )
        }

    private fun tv(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun lp(top: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = px(top) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(56), px(20), px(28))
        }

        // Encabezado estilo terminal
        content.addView(
            tv("$ loguen --acceso", 12f, cMuted).apply { typeface = Typeface.MONOSPACE }
        )
        val row0 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        row0.addView(
            tv("loguen", 34f, cText, true).apply {
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            }
        )
        cursor = tv("_", 34f, cAccent, true).apply {
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        }
        row0.addView(cursor)
        content.addView(row0, lp(2))
        blink = ObjectAnimator.ofFloat(cursor, View.ALPHA, 1f, 0f).apply {
            duration = 600
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
        }
        content.addView(tv("Un check y listo", 14f, cMuted), lp(6))
        content.addView(
            tv("Escribe la clave que te dio el verificador", 13f, cMuted),
            lp(28)
        )

        // Casillas
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for (i in 0 until 14) {
            if (i == 6) {
                row.addView(
                    tv("-", 16f, cMuted).apply { gravity = Gravity.CENTER },
                    LinearLayout.LayoutParams(px(12), WRAP)
                )
            }
            val b = tv("", 15f, cText, true).apply { gravity = Gravity.CENTER }
            boxes.add(b)
            row.addView(b, LinearLayout.LayoutParams(0, px(44), 1f).apply {
                leftMargin = px(1)
                rightMargin = px(1)
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
        keyArea.addView(row, FrameLayout.LayoutParams(MATCH, px(44)))
        keyArea.addView(input, FrameLayout.LayoutParams(MATCH, px(44)))
        content.addView(keyArea, lp(14))

        // Barra de tiempo
        barTrack = FrameLayout(this).apply { background = rr(cStroke, 4) }
        barFill = View(this)
        barTrack.addView(barFill, FrameLayout.LayoutParams(0, MATCH))
        content.addView(barTrack, LinearLayout.LayoutParams(MATCH, px(4)).apply {
            topMargin = px(18)
            leftMargin = px(8)
            rightMargin = px(8)
        })

        timerText = tv("", 13f, cMuted).apply { gravity = Gravity.CENTER }
        content.addView(timerText, lp(8))

        msg = tv("", 13f, cRed).apply { gravity = Gravity.CENTER }
        content.addView(msg, lp(10))

        val enter = Button(this).apply {
            text = "Entrar"
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            background = rr(cAccent, 16)
            stateListAnimator = null
            setPadding(px(12), px(12), px(12), px(12))
            setOnClickListener { submit() }
        }
        content.addView(enter, lp(16))

        content.addView(
            tv("Pide la clave por Discord. Cada clave sirve una sola vez.", 12f, cMuted)
                .apply { gravity = Gravity.CENTER },
            lp(16)
        )

        // Fondo con luces
        val root = FrameLayout(this).apply { setBackgroundColor(cBg) }
        root.addView(
            View(this).apply { background = glow(cAccent, 380) },
            FrameLayout.LayoutParams(px(380), px(380), Gravity.TOP or Gravity.END).apply {
                topMargin = -px(150)
                rightMargin = -px(150)
            }
        )
        root.addView(
            View(this).apply { background = glow(cGreen, 340) },
            FrameLayout.LayoutParams(px(340), px(340), Gravity.BOTTOM or Gravity.START).apply {
                bottomMargin = -px(150)
                leftMargin = -px(150)
            }
        )
        root.addView(
            ScrollView(this).apply {
                isFillViewport = true
                setBackgroundColor(Color.TRANSPARENT)
                addView(content)
            },
            FrameLayout.LayoutParams(MATCH, MATCH)
        )
        setContentView(root)

        refreshBoxes()
        updateTimer()
        input.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        blink?.start()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        blink?.cancel()
        handler.removeCallbacks(ticker)
    }

    private fun refreshBoxes() {
        val t = input.text.toString()
        for (i in boxes.indices) {
            val b = boxes[i]
            b.text = if (i < t.length) t[i].toString() else ""
            val active = i == t.length && t.length < 14
            b.background = rr(cGlass, 10, if (active) cAccent else cStroke)
        }
    }

    private fun setBar(frac: Float, color: Int) {
        val w = (barTrack.width * frac.coerceIn(0f, 1f)).toInt()
        barFill.background = rr(color, 4)
        barFill.layoutParams = FrameLayout.LayoutParams(w, MATCH)
    }

    private fun updateTimer() {
        val t = input.text.toString()
        if (t.length < 6) {
            timerText.setTextColor(cMuted)
            timerText.text = "La clave vence 4 minutos después de generarla"
            setBar(0f, cMuted)
            return
        }
        val exp = t.substring(0, 6).toLongOrNull(36)
        if (exp == null) {
            timerText.text = ""
            setBar(0f, cMuted)
            return
        }
        val left = exp - System.currentTimeMillis() / 1000
        if (left <= 0) {
            timerText.setTextColor(cRed)
            timerText.text = "Clave vencida"
            setBar(0f, cRed)
            return
        }
        val color = when {
            left > 60 -> cGreen
            left > 20 -> cAmber
            else -> cRed
        }
        timerText.setTextColor(color)
        timerText.text = "Vence en ${left / 60}:${(left % 60).toString().padStart(2, '0')}"
        setBar(left / 240f, color)
    }

    private fun show(text: String, color: Int = cRed) {
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
