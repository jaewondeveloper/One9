package dev.oneuiproject.oneui.layout.internal.util

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
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
 * One UI 9 collapsed app bar behaviour. Only active on screens whose app bar can actually collapse
 * (expandable app bar); on every other screen nothing is changed.
 *
 * Once the app bar is fully collapsed (the user has scrolled):
 *  - the title/subtitle in the bar disappears,
 *  - a vignette in the page background colour fades the content out at the top edge,
 *  - the navigation button and the menu get floating round backgrounds.
 */
internal class OneUi9AppBarEffect(
    private val appBar: AppBarLayout,
    private val toolbar: Toolbar,
    private val coordinator: CoordinatorLayout,
    @Suppress("unused") private val content: View
) {
    private val ctx = appBar.context
    private val res = ctx.resources
    private val density = res.displayMetrics.density
    private val vignette = View(ctx)
    private var progress = -1f
    // The effect only applies after the bar has been seen expanded: a screen that starts collapsed
    // (short content) must look like it always did.
    private var sawExpanded = false
    private val floatingElevation = res.getDimension(R.dimen.oui_des_floating_navigate_up_elevation)

    private val navBg = ContextCompat.getDrawable(ctx, R.drawable.oui_des_floating_navigate_up_bg)
    private val menuBg = ContextCompat.getDrawable(ctx, R.drawable.oui_des_floating_toolbar_bg)
    private val wrappers = HashMap<View, Drawable>()
    private val originalTextColors = HashMap<TextView, Int>()

    fun attach() {
        val fadeHeight = (28 * density).toInt()
        vignette.apply {
            alpha = 0f
            visibility = View.INVISIBLE
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(pageBackground(), withAlpha(pageBackground(), 0.6f), Color.TRANSPARENT)
            )
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
        wrappers.clear()
    }

    fun onOffset(layout: AppBarLayout, verticalOffset: Int) {
        val range = layout.totalScrollRange
        // Not an expandable app bar: never collapses, so the effect must stay off.
        val collapsedFraction = if (range <= 0 || !layout.isEnabled) 0f else abs(verticalOffset).toFloat() / range
        if (collapsedFraction < 0.9f && range > 0) sawExpanded = true
        val p = if (!sawExpanded) 0f else ((collapsedFraction - 0.9f) / 0.1f).coerceIn(0f, 1f)
        if (p == progress) return
        progress = p

        // Vignette sits right below the bar and fades the content out under it.
        vignette.visibility = if (p > 0f) View.VISIBLE else View.INVISIBLE
        vignette.alpha = p
        vignette.translationY = appBar.bottom.toFloat()

        // Title/subtitle: SESL keeps resetting their alpha, so fade the text colour instead.
        toolbar.children.filterIsInstance<TextView>().forEach { tv ->
            val original = originalTextColors.getOrPut(tv) { tv.currentTextColor }
            tv.setTextColor(withAlpha(original, 1f - p))
        }

        // Floating round backgrounds for the navigation button and the menu.
        toolbar.children.forEach { child ->
            when (child) {
                is ImageButton -> applyFloating(child, navBg, inset(child, 4f, 4f), p)
                is ActionMenuView -> applyFloating(child, menuBg, inset(child, 0f, 4f), p)
            }
        }
    }

    private fun inset(v: View, horizontalDp: Float, verticalDp: Float) =
        intArrayOf((horizontalDp * density).toInt(), (verticalDp * density).toInt())

    private fun applyFloating(view: View, bg: Drawable?, inset: IntArray, p: Float) {
        if (bg == null) return
        val wrapper = wrappers.getOrPut(view) {
            InsetDrawable(bg.constantState?.newDrawable()?.mutate() ?: bg, inset[0], inset[1], inset[0], inset[1])
                .also { view.background = it }
        }
        wrapper.alpha = (255 * p).toInt()
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
