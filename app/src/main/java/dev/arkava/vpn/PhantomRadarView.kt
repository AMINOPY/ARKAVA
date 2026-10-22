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
 * Canvas-rendered tactical radar with a 2.5D holographic core.
 * It is a visual instrument only; connection truth must come from the VPN service.
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

        // Precision rings and ticks form the flat instrument layer behind the orb.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = withAlpha(tone, 130)
        for (i in 1..4) canvas.drawCircle(cx, cy, radius * i / 4f, paint)
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

        // A perspective ellipse adds depth without a heavy 3D engine.
        val orbitRadius = radius * 0.62f
        canvas.save()
        canvas.rotate(-24f, cx, cy)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = withAlpha(tone, 105)
        canvas.drawOval(
            RectF(cx - orbitRadius, cy - orbitRadius * 0.28f, cx + orbitRadius, cy + orbitRadius * 0.28f),
            paint
        )
        paint.color = withAlpha(tone, 45)
        canvas.drawOval(
            RectF(cx - orbitRadius * 0.82f, cy - orbitRadius * 0.42f, cx + orbitRadius * 0.82f, cy + orbitRadius * 0.42f),
            paint
        )
        canvas.restore()

        // The sweep runs in standby too, but at a restrained intensity.
        val sweepAlpha = if (active || danger) 64 else 24
        val sweepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = android.graphics.SweepGradient(
                cx, cy,
                intArrayOf(Color.TRANSPARENT, withAlpha(tone, 4), withAlpha(tone, sweepAlpha), Color.TRANSPARENT),
                floatArrayOf(0f, 0.68f, 0.96f, 1f)
            )
        }
        canvas.save()
        canvas.rotate(phase, cx, cy)
        canvas.drawCircle(cx, cy, radius, sweepPaint)
        canvas.restore()

        // Orbital telemetry particles. Decorative only; they never imply a measured node state.
        paint.shader = null
        paint.style = Paint.Style.FILL
        dots.forEachIndexed { index, point ->
            val orbit = radius * (0.48f + (index % 3) * 0.13f)
            val angle = Math.toRadians((phase * (if (index % 2 == 0) 0.45f else -0.31f) + index * 61f).toDouble())
            val dx = cx + cos(angle).toFloat() * orbit
            val dy = cy + sin(angle).toFloat() * orbit * 0.78f
            val pulse = (sin(Math.toRadians((phase * 2f + index * 57f).toDouble())) + 1.0) * 0.5
            paint.color = withAlpha(tone, (75 + pulse * if (active || danger) 175 else 65).toInt())
            canvas.drawCircle(dx, dy, dp((1.5f + pulse.toFloat() * 1.8f)), paint)
            if (active || danger) {
                paint.color = withAlpha(tone, (28 + pulse * 55).toInt())
                canvas.drawCircle(dx, dy, dp(5f + pulse.toFloat() * 2f), paint)
            }
        }

        // Glass sphere: layered radial highlights, dark limb and a specular reflection.
        val breathing = 1f + (sin(Math.toRadians(phase.toDouble())).toFloat() * 0.035f)
        val orb = radius * 0.19f * breathing
        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx - orb * 0.42f, cy - orb * 0.48f, orb * 1.9f,
            intArrayOf(withAlpha(Color.WHITE, 105), withAlpha(tone, 105), withAlpha(tone, 32), withAlpha(Color.BLACK, 210)),
            floatArrayOf(0f, 0.23f, 0.62f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, orb, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1f)
        paint.color = withAlpha(tone, 210)
        canvas.drawCircle(cx, cy, orb, paint)

        // Thin orbit passes in front of the glass sphere to sell the layered 3D effect.
        canvas.save()
        canvas.rotate(18f + phase * 0.25f, cx, cy)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(1.1f)
        paint.color = withAlpha(tone, 200)
        canvas.drawOval(RectF(cx - orb * 1.72f, cy - orb * 0.46f, cx + orb * 1.72f, cy + orb * 0.46f), paint)
        canvas.restore()

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx - orb * 0.35f, cy - orb * 0.45f, orb * 0.48f,
            intArrayOf(withAlpha(Color.WHITE, 205), withAlpha(tone, 45), Color.TRANSPARENT),
            null, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx - orb * 0.34f, cy - orb * 0.38f, orb * 0.25f, paint)
        paint.shader = null
        paint.color = tone
        canvas.drawCircle(cx, cy, dp(2.5f), paint)

        if (attached) {
            // Gentle standby drift; faster motion only when a real state is supplied.
            phase = (phase + if (active || danger) 1.35f else 0.42f) % 360f
            postInvalidateOnAnimation()
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
