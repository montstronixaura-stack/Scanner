package com.example.scanner

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object Colors {
    val BG = Color.parseColor("#0B0E14")
    val CARD = Color.argb(26, 255, 255, 255)
    val STROKE = Color.argb(46, 255, 255, 255)
    val TEXT = Color.parseColor("#F2F4F8")
    val MUTED = Color.parseColor("#9AA3B8")
    val ACCENT = Color.parseColor("#7C6CFF")
    val GREEN = Color.parseColor("#2ECC8F")
    val RED = Color.parseColor("#FF5C6C")
    val AMBER = Color.parseColor("#FFB84D")
    val SECONDARY = Color.argb(36, 255, 255, 255)
}

fun withAlpha(color: Int, alpha: Int): Int =
    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(maxOf(1, dp(1) / 2), strokeColor)
    }

fun Context.glow(color: Int, sizeDp: Int): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        gradientType = GradientDrawable.RADIAL_GRADIENT
        gradientRadius = dp(sizeDp / 2).toFloat()
        setGradientCenter(0.5f, 0.5f)
        colors = intArrayOf(withAlpha(color, 120), withAlpha(color, 0))
    }

fun Context.params(topDp: Int = 0): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(topDp) }

fun Context.label(
    text: String,
    sizeSp: Float,
    color: Int = Colors.TEXT,
    bold: Boolean = false
): TextView = TextView(this).apply {
    this.text = text
    textSize = sizeSp
    setTextColor(color)
    if (bold) setTypeface(typeface, Typeface.BOLD)
}

fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(16), dp(16), dp(16), dp(16))
    background = rounded(Colors.CARD, 20, Colors.STROKE)
}

fun Context.pillButton(text: String, bg: Int, onClick: () -> Unit): Button =
    Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.WHITE)
        background = rounded(bg, 16)
        stateListAnimator = null
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { onClick() }
    }

fun Button.enable(on: Boolean) {
    isEnabled = on
    alpha = if (on) 1f else 0.4f
}

// Fondo oscuro con dos manchas de luz y el contenido encima.
fun Activity.glassScreen(content: View, tint: Int = Colors.ACCENT): FrameLayout {
    val root = FrameLayout(this).apply { setBackgroundColor(Colors.BG) }

    val g1 = View(this).apply { background = glow(tint, 380) }
    root.addView(
        g1,
        FrameLayout.LayoutParams(dp(380), dp(380), Gravity.TOP or Gravity.END).apply {
            topMargin = -dp(150)
            rightMargin = -dp(150)
        }
    )

    val g2 = View(this).apply { background = glow(Colors.GREEN, 340) }
    root.addView(
        g2,
        FrameLayout.LayoutParams(dp(340), dp(340), Gravity.BOTTOM or Gravity.START).apply {
            bottomMargin = -dp(150)
            leftMargin = -dp(150)
        }
    )

    val sv = ScrollView(this).apply {
        isFillViewport = true
        setBackgroundColor(Color.TRANSPARENT)
        addView(content)
    }
    root.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
    return root
}

// Cambia el color de la mancha de arriba (se usa en Resultados).
fun tintGlow(screen: FrameLayout, color: Int) {
    screen.getChildAt(0).background = screen.context.glow(color, 380)
}
