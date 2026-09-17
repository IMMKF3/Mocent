package com.salarydance.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/** 今天的时间线：轨道 + 午休段 + 进度条 + 当前时刻圆点 */
class TimelineView(c: Context) : View(c) {
    var startMin = 540
    var endMin = 1080
    var lunchA = 720
    var lunchB = 780
    var hasLunch = true
    private var pct = 0f

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEFE5D2.toInt() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lunch = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x59E6A23C.toInt() }
    private val dotFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val dotRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.BRAND
        style = Paint.Style.STROKE
    }
    private val clipPath = Path()

    init {
        fill.shader = LinearGradient(0f, 0f, 400f, 0f,
            0xFFFFC06E.toInt(), 0xFFFF8A3D.toInt(), Shader.TileMode.CLAMP)
    }

    fun setProgress(p: Float) {
        pct = p.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w > 0) fill.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
            0xFFFFC06E.toInt(), 0xFFFF8A3D.toInt(), Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        val rect = RectF(0f, 0f, w, h)

        clipPath.reset()
        clipPath.addRoundRect(rect, r, r, Path.Direction.CW)
        canvas.drawRoundRect(rect, r, r, track)

        val span = (endMin - startMin).coerceAtLeast(1)
        if (hasLunch && lunchB > lunchA) {
            val x1 = w * (lunchA - startMin) / span
            val x2 = w * (lunchB - startMin) / span
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawRect(x1, 0f, x2, h, lunch)
            canvas.restore()
        }

        val fw = (w * pct).coerceAtLeast(h)
        canvas.drawRoundRect(RectF(0f, 0f, fw, h), r, r, fill)

        val cx = w * pct
        val cy = h / 2f
        val dotR = h * 0.9f
        dotRing.strokeWidth = Ui.dp(context, 2).toFloat()
        canvas.drawCircle(cx, cy, dotR - dotRing.strokeWidth / 2f, dotFill)
        canvas.drawCircle(cx, cy, dotR - dotRing.strokeWidth / 2f, dotRing)
    }
}

/** 圆角进度条（心愿单等用） */
class BarView(c: Context, trackColor: Int, fillColor: Int) : View(c) {
    private var p = 0f
    private val trackP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = trackColor }
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }

    fun setProgress(f: Float) {
        p = f.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val r = h / 2f
        canvas.drawRoundRect(RectF(0f, 0f, w, h), r, r, trackP)
        if (p > 0.005f) {
            val fw = maxOf(h, w * p)
            canvas.drawRoundRect(RectF(0f, 0f, fw, h), r, r, fillP)
        }
    }
}
