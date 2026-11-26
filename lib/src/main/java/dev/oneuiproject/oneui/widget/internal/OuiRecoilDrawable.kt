package dev.oneuiproject.oneui.widget.internal

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import kotlin.math.min

/**
 * One UI 9 press feedback ("recoil"): a capsule shaped highlight that fades in while pressed and
 * eases out on release. Timings and curves follow SESL 9:
 *  - press:   100ms, linear
 *  - release: 350ms, PathInterpolator(0.17, 0.17, 0.67, 1.0)
 */
internal class OuiRecoilDrawable(
    private val color: Int,
    private val circleSizePx: Int = 0,
    /** Area (in view coordinates) the circle is centred in; defaults to the drawable bounds. */
    private val anchor: (() -> RectF)? = null
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var level = 0f
    private var pressed = false
    private var animator: ValueAnimator? = null

    override fun isStateful() = true

    override fun onStateChange(state: IntArray): Boolean {
        val isPressed = state.contains(android.R.attr.state_pressed) && state.contains(android.R.attr.state_enabled)
        if (isPressed == pressed) return false
        pressed = isPressed
        animateTo(if (isPressed) 1f else 0f)
        return true
    }

    private fun animateTo(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(level, target).apply {
            if (target > level) {
                duration = PRESS_DURATION
                interpolator = LinearInterpolator()
            } else {
                duration = RELEASE_DURATION
                interpolator = PathInterpolator(0.17f, 0.17f, 0.67f, 1f)
            }
            addUpdateListener {
                level = it.animatedValue as Float
                invalidateSelf()
            }
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        if (level <= 0f) return
        paint.color = color
        paint.alpha = (Color.alpha(color) * level).toInt()
        if (circleSizePx > 0) {
            // perfect circle centred in the (possibly non square) bounds
            val area = anchor?.invoke() ?: RectF(bounds)
            val d = min(min(area.width(), area.height()), circleSizePx.toFloat())
            rect.set(area.centerX() - d / 2f, area.centerY() - d / 2f, area.centerX() + d / 2f, area.centerY() + d / 2f)
        } else {
            rect.set(bounds)
        }
        val r = min(rect.width(), rect.height()) / 2f
        canvas.drawRoundRect(rect, r, r, paint)
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private companion object {
        const val PRESS_DURATION = 100L
        const val RELEASE_DURATION = 350L
    }
}
