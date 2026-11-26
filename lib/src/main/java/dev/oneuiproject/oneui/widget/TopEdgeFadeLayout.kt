package dev.oneuiproject.oneui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import dev.oneuiproject.oneui.design.R

/**
 * One UI 9 style top scroll edge effect. While the attached scrolling view is scrolled, the
 * content gradually dissolves into the page background at the top edge (a vignette), and it
 * fades back in when scrolled to the top.
 *
 * Wrap the scrolling content and call [attachTo]:
 * ```kotlin
 * fadeLayout.attachTo(nestedScrollView)
 * ```
 */
class TopEdgeFadeLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /** Height of the fade zone in pixels. */
    var fadeHeight: Int = resources.getDimensionPixelSize(R.dimen.oui_des_top_fade_height)
        set(value) {
            field = value
            shader = null
            invalidate()
        }

    /** Color the content dissolves into. Defaults to the theme window background. */
    var fadeColor: Int = resolveBackground()
        set(value) {
            field = value
            shader = null
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: Shader? = null
    private var progress = 0f

    init {
        setWillNotDraw(false)
    }

    fun attachTo(scrollable: View) {
        when (scrollable) {
            is RecyclerView -> scrollable.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                    setScrollOffset(rv.computeVerticalScrollOffset())
                }
            })
            is NestedScrollView -> scrollable.setOnScrollChangeListener(
                NestedScrollView.OnScrollChangeListener { _, _, scrollY, _, _ -> setScrollOffset(scrollY) }
            )
            else -> scrollable.setOnScrollChangeListener { _, _, scrollY, _, _ -> setScrollOffset(scrollY) }
        }
    }

    fun setScrollOffset(offsetPx: Int) {
        val p = (offsetPx.toFloat() / fadeHeight).coerceIn(0f, 1f)
        if (p != progress) {
            progress = p
            invalidate()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (progress <= 0f) return
        val s = shader ?: LinearGradient(
            0f, 0f, 0f, fadeHeight.toFloat(),
            intArrayOf(fadeColor, withAlpha(fadeColor, 0.6f), Color.TRANSPARENT),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP
        ).also { shader = it }
        paint.shader = s
        paint.alpha = (progress * 255).toInt()
        canvas.drawRect(0f, 0f, width.toFloat(), fadeHeight.toFloat(), paint)
    }

    private fun withAlpha(color: Int, a: Float) =
        Color.argb((Color.alpha(color) * a).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private fun resolveBackground(): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)) {
            if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data
        } else Color.WHITE
    }
}
