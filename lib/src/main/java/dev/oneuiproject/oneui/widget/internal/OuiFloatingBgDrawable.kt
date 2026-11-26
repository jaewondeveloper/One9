package dev.oneuiproject.oneui.widget.internal

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * Floating background of the toolbar buttons.
 *  - [circle]: a perfect circle of at most [sizePx] centred in the bounds (never an ellipse),
 *  - otherwise a capsule of at most [sizePx] height spanning the bounds' width.
 * Its outline follows the shape, so the elevation shadow is round too.
 */
internal class OuiFloatingBgDrawable(
    private val color: Int,
    private val circle: Boolean,
    private val sizePx: Int,
    /** Area (in view coordinates) the shape is fitted into; defaults to the drawable bounds. */
    private val anchor: (() -> RectF)? = null
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var alphaF = 1f

    private fun updateRect() {
        val area = anchor?.invoke() ?: RectF(bounds)
        val cx = area.centerX()
        val cy = area.centerY()
        if (circle) {
            val d = min(min(area.width(), area.height()), sizePx.toFloat())
            rect.set(cx - d / 2f, cy - d / 2f, cx + d / 2f, cy + d / 2f)
        } else {
            val h = min(area.height(), sizePx.toFloat())
            rect.set(area.left, cy - h / 2f, area.right, cy + h / 2f)
        }
    }

    override fun draw(canvas: Canvas) {
        if (alphaF <= 0f) return
        updateRect()
        paint.color = color
        paint.alpha = (Color.alpha(color) * alphaF).toInt()
        val r = min(rect.width(), rect.height()) / 2f
        canvas.drawRoundRect(rect, r, r, paint)
    }

    override fun getOutline(outline: Outline) {
        updateRect()
        val r = min(rect.width(), rect.height()) / 2f
        outline.setRoundRect(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt(), r)
        outline.alpha = alphaF
    }

    override fun setAlpha(alpha: Int) {
        alphaF = alpha / 255f
        invalidateSelf()
    }

    override fun getAlpha(): Int = (alphaF * 255).toInt()
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
