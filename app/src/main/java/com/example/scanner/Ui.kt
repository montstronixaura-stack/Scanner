package com.example.scanner

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object Colors {
    val BG = Color.parseColor("#0E1117")
    val CARD = Color.parseColor("#171C27")
    val STROKE = Color.parseColor("#262D3D")
    val TEXT = Color.parseColor("#F2F4F8")
    val MUTED = Color.parseColor("#8B93A7")
    val ACCENT = Color.parseColor("#7C6CFF")
    val GREEN = Color.parseColor("#2ECC8F")
    val RED = Color.parseColor("#FF5C6C")
    val AMBER = Color.parseColor("#FFB84D")
    val SECONDARY = Color.parseColor("#232A3A")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.rounded(color: Int, radiusDp: Int, strokeColor: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != null) setStroke(dp(1), strokeColor)
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
    background = rounded(Colors.CARD, 16, Colors.STROKE)
}

fun Context.pillButton(text: String, bg: Int, onClick: () -> Unit): Button =
    Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 15f
        setTextColor(Color.WHITE)
        background = rounded(bg, 14)
        stateListAnimator = null
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener { onClick() }
    }

fun Button.enable(on: Boolean) {
    isEnabled = on
    alpha = if (on) 1f else 0.4f
}
