package com.salarydance.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView

/** 配色 / 尺寸 / 通用控件，风格对齐网页版 */
object Ui {
    val BG = Color.parseColor("#f7f1e6")
    val CARD = Color.parseColor("#fffdf9")
    val INK = Color.parseColor("#43352a")
    val SUB = Color.parseColor("#a4917c")
    val LINE = Color.parseColor("#f0e4d2")
    val BRAND = Color.parseColor("#ff8a3d")
    val BRAND_DEEP = Color.parseColor("#e2711d")
    val GREEN = Color.parseColor("#2fa36b")
    val AMBER = Color.parseColor("#e6a23c")
    val RED = Color.parseColor("#e15554")
    val PURPLE = Color.parseColor("#7d6be0")
    val TEAL = Color.parseColor("#5bb8a8")
    val CHIP_BG = Color.parseColor("#f6efe2")
    val TRACK = Color.parseColor("#efe5d2")
    val TAB_OFF = Color.parseColor("#b6a48d")
    val NOTE = Color.parseColor("#bfae98")
    val BUBBLE_TEXT = Color.parseColor("#6b5a48")
    val EDIT_STROKE = Color.parseColor("#eddfc6")
    val BTN_GHOST_TEXT = Color.parseColor("#8a7a66")
    val BTN_GHOST_STROKE = Color.parseColor("#ecdfc8")
    val SOFT_BG = Color.parseColor("#faf4e8")

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()
    fun dp(c: Context, v: Float): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun roundBg(fill: Int, radiusPx: Float, strokeColor: Int = Color.TRANSPARENT,
                strokeWidthPx: Float = 0f): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.setColor(fill)
        g.cornerRadius = radiusPx
        if (strokeWidthPx > 0) {
            g.setStroke(strokeWidthPx.toInt(), strokeColor)
        }
        return g
    }

    fun ovalBg(fill: Int): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.OVAL
        g.setColor(fill)
        return g
    }

    fun gradBg(c1: Int, c2: Int, radiusPx: Float): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2))
        g.cornerRadius = radiusPx
        return g
    }

    fun text(c: Context, sizeSp: Int, color: Int, bold: Boolean = false): TextView =
        TextView(c).apply {
            setTextColor(color)
            textSize = sizeSp.toFloat()
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = roundBg(CARD, dp(c, 20).toFloat())
        val p = dp(c, 18)
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(c, 14) }
    }

    fun cardTitle(c: Context, s: String): TextView = text(c, 13, SUB, true).apply {
        text = s
        letterSpacing = 0.08f
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = dp(c, 12)
        layoutParams = lp
    }

    fun field(c: Context, label: String, control: View): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val p = dp(c, 11)
        setPadding(dp(c, 2), p, dp(c, 2), p)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        addView(TextView(c).apply {
            text = label
            setTextColor(INK)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(control)
    }

    fun edit(c: Context, widthDp: Int, numeric: Boolean = false): EditText =
        EditText(c).apply {
            background = roundBg(Color.WHITE, dp(c, 10).toFloat(), EDIT_STROKE, dp(c, 1).toFloat())
            setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8))
            setTextColor(INK)
            textSize = 14f
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            inputType = if (numeric) {
                android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            } else android.text.InputType.TYPE_CLASS_TEXT
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            layoutParams = LinearLayout.LayoutParams(dp(c, widthDp), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

    fun btn(c: Context, label: String, ghost: Boolean = false): Button = Button(c).apply {
        text = label
        textSize = 14.5f
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        isAllCaps = false
        if (ghost) {
            setTextColor(BTN_GHOST_TEXT)
            background = roundBg(Color.WHITE, dp(c, 14).toFloat(), BTN_GHOST_STROKE, dp(c, 1.5f).toInt().toFloat())
        } else {
            setTextColor(Color.WHITE)
            background = gradBg(Color.parseColor("#ffb25e"), BRAND, dp(c, 14).toFloat())
        }
        val p = dp(c, 12)
        setPadding(p, p, p, p)
    }

    fun switch(c: Context): Switch = Switch(c).apply {
        showText = false
    }

    fun caption(c: Context): TextView = text(c, 11, NOTE).apply {
        lineHeight = dp(c, 18)
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(c, 10)
        layoutParams = lp
    }

    fun ellipsize(tv: TextView) {
        tv.maxLines = 1
        tv.ellipsize = TextUtils.TruncateAt.END
    }
}
