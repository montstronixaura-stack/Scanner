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
        Env.ping(this)
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
        barTrack.addView(barFill, FrameLayout.LayoutParams(
