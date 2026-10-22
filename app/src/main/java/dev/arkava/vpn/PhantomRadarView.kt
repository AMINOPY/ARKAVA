package dev.arkava.vpn

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Lightweight Canvas-rendered radar for PHANTOM COMMAND.
 * It is intentionally decorative: connection truth comes from the VPN service,
 * never from this animation.
 */
class PhantomRadarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val green = Color.rgb(91, 255, 176)
    private val muted = Color.rgb(84, 111, 105)
    private val red = Color.rgb(255, 83, 96)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var active = false
    private var danger = false
    private var phase = 0f
    private var attached = false
    private val dots = listOf(
        0.24f to 0.18f, 0.67f to 0.29f, 0.76f to 0.64f,
        0.34f to 0.76f, 0.17f to 0.49f, 0.56f to 0.53f
    )

    fun setMode(isActive: Boolean, isDanger: Boolean = false) {
        active = isActive
        danger = isDanger
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        attached = true
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        attached = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val radius = size * 0.43f
        val tone = when {
            danger -> red
            active -> green
            else -> muted
        }

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, radius * 1.1f,
            intArrayOf(withAlpha(tone, 36), withAlpha(tone, 9), Color.TRANSPARENT),
            floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 1.1f, paint)
        paint.shader = null

        // Perspective rings and precision ticks create a physical instrument feel.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = withAlpha(tone, 130)
        for (i in 1..4) {
            canvas.drawCircle(cx, cy, radius * i / 4f, paint)
        }
        paint.color = withAlpha(tone, 70)
        paint.strokeWidth = dp(0.7f)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint)
        for (i in 0 until 72) {
            val a = Math.toRadians(i * 5.0)
            val inner = if (i % 3 == 0) radius * 0.91f else radius * 0.96f
            canvas.drawLine(
                cx + cos(a).toFloat() * inner, cy + sin(a).toFloat() * inner,
                cx + cos(a).toFloat() * radius, cy + sin(a).toFloat() * radius, paint
            )
        }

        // Sweep beam is deliberately subtle while disconnected.
        if (active || danger) {
            val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                shader = android.graphics.SweepGradient(
                    cx, cy,
                    intArrayOf(Color.TRANSPARENT, withAlpha(tone, 6), withAlpha(tone, 58), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.68f, 0.96f, 1f)
                )
            }
            canvas.save()
            canvas.rotate(phase, cx, cy)
            canvas.drawCircle(cx, cy, radius, sweepPaint)
            canvas.restore()
        }

        // Dots are visual node markers, not claims that remote servers are reachable.
        paint.style = Paint.Style.FILL
        dots.forEachIndexed { index, point ->
            val dx = cx + (point.first - 0.5f) * radius * 1.72f
            val dy = cy + (point.second - 0.5f) * radius * 1.72f
            if (dx in (cx - radius)..(cx + radius) && dy in (cy - radius)..(cy + radius)) {
                paint.color = withAlpha(tone, if (active && index % 2 == 0) 230 else 110)
                canvas.drawCircle(dx, dy, dp(if (active && index % 2 == 0) 3.2f else 2f), paint)
            }
        }

        // Central glass core and fine crosshair.
        paint.color = withAlpha(tone, 28)
        canvas.drawCircle(cx, cy, radius * 0.19f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.2f)
        paint.color = withAlpha(tone, 220)
        canvas.drawCircle(cx, cy, radius * 0.16f, paint)
        paint.style = Paint.Style.FILL
        paint.color = tone
        canvas.drawCircle(cx, cy, dp(3.5f), paint)
        paint.color = withAlpha(tone, 150)
        canvas.drawCircle(cx, cy, dp(9f), paint.apply { style = Paint.Style.STROKE; strokeWidth = dp(0.8f) })
        paint.style = Paint.Style.FILL

        if (attached) {
            phase = (phase + 1.3f) % 360f
            postInvalidateOnAnimation()
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
