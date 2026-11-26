package dev.oneuiproject.oneui.layout.internal.util

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import com.google.android.material.appbar.AppBarLayout
import dev.oneuiproject.oneui.design.R
import kotlin.math.abs

/**
 * One UI 9 collapsed app bar behaviour.
 *
 * While the app bar collapses:
 *  - the collapsed title/subtitle fades out,
 *  - a vignette in the page background colour appears at the top (status bar side),
 *  - the navigation button and the menu get floating round backgrounds,
 *  - the content moves up under the (now transparent) bar.
 */
internal class OneUi9AppBarEffect(
    private val appBar: AppBarLayout,
    private val toolbar: Toolbar,
    private val coordinator: CoordinatorLayout,
    private val content: View
) {
    private val ctx = appBar.context
    private val res = ctx.resources
    private val vignette = View(ctx)
    private val toolbarHeight: Int
    private var progress = -1f
    private var navBg = ContextCompat.getDrawable(ctx, R.drawable.oui_des_floating_navigate_up_bg)?.mutate()
    private var menuBg = ContextCompat.getDrawable(ctx, R.drawable.oui_des_floating_toolbar_bg)?.mutate()
    private val floatingElevation = res.getDimension(R.dimen.oui_des_floating_navigate_up_elevation)

    init {
        val tv = TypedValue()
        toolbarHeight = if (ctx.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true))
            TypedValue.complexToDimensionPixelSize(tv.data, res.displayMetrics)
        else res.getDimensionPixelSize(R.dimen.oui_des_floating_navigate_up_size)
    }

    fun attach() {
        // Transparent bar drawn above the content (no shadow without a background).
        appBar.background = null
        appBar.translationZ = res.displayMetrics.density * 2f

        val fadeHeight = toolbarHeight + res.getDimensionPixelSize(R.dimen.oui_des_top_fade_height) / 3
        vignette.apply {
            alpha = 0f
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(pageBackground(), withAlpha(pageBackground(), 0.6f), Color.TRANSPARENT)
            )
            translationZ = res.displayMetrics.density * 1f // above content, below the app bar
        }
        coordinator.addView(
            vignette,
            CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, fadeHeight).apply {
                gravity = Gravity.TOP
            }
        )
    }

    fun detach() {
        coordinator.removeView(vignette)
    }

    fun onOffset(layout: AppBarLayout, verticalOffset: Int) {
        val range = layout.totalScrollRange
        val collapsed = if (range == 0) 1f else abs(verticalOffset).toFloat() / range
        val p = ((collapsed - 0.6f) / 0.4f).coerceIn(0f, 1f)
        if (p == progress) return
        progress = p

        vignette.alpha = p

        // Collapsed title/subtitle disappears.
        val titleAlpha = 1f - p
        toolbar.children.forEach { child ->
            if (child is TextView) child.alpha = titleAlpha
        }

        // Floating round backgrounds for the navigation button and the menu.
        toolbar.children.forEach { child ->
            when {
                child is ImageButton -> applyFloating(child, navBg, p)
                child is ActionMenuView -> applyFloating(child, menuBg, p)
            }
        }

        // Content slides up under the bar.
        content.translationY = -toolbarHeight * p
    }

    private fun applyFloating(view: View, bg: android.graphics.drawable.Drawable?, p: Float) {
        if (bg == null) return
        if (view.background !== bg) view.background = bg
        bg.alpha = (255 * p).toInt()
        view.elevation = floatingElevation * p
    }

    private fun pageBackground(): Int {
        val tv = TypedValue()
        return if (ctx.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)) {
            if (tv.resourceId != 0) ContextCompat.getColor(ctx, tv.resourceId) else tv.data
        } else Color.WHITE
    }

    private fun withAlpha(color: Int, a: Float) =
        Color.argb((Color.alpha(color) * a).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
