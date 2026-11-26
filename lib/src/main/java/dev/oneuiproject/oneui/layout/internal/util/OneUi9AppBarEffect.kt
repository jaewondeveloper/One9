package dev.oneuiproject.oneui.layout.internal.util

import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
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
import dev.oneuiproject.oneui.widget.internal.OuiFloatingBgDrawable
import dev.oneuiproject.oneui.widget.internal.OuiRecoilDrawable
import kotlin.math.abs

/**
 * One UI 9 app bar behaviour.
 *
 * Always:
 *  - the toolbar buttons show the One UI 9 press feedback (round recoil background).
 *
 * On screens whose app bar can collapse, once it has been seen expanded and is then collapsed
 * by scrolling:
 *  - the title/subtitle in the bar disappears,
 *  - the content moves up to the very top of the screen, under the (transparent) bar,
 *  - a vignette in the page background colour fades the content out at the top edge,
 *  - the navigation button and the menu keep a floating round background.
 */
internal class OneUi9AppBarEffect(
    private val appBar: AppBarLayout,
    private val toolbar: Toolbar,
    private val coordinator: CoordinatorLayout,
    private val content: View
) {
    private val ctx = appBar.context
    private val res = ctx.resources
    private val density = res.displayMetrics.density
    private val vignette = View(ctx)
    private var progress = -1f
    // Only after the bar was seen expanded: a screen that starts collapsed must look as before.
    private var sawExpanded = false
    private val floatingElevation = res.getDimension(R.dimen.oui_des_floating_navigate_up_elevation)
    private val toolbarHeight: Int

    private val floating = HashMap<View, Drawable>()
    private val prepared = HashSet<View>()
    private val originalTextColors = HashMap<TextView, Int>()
    private var originalContentHeight: Int? = null
    private var collapsingBackground: Drawable? = null
    private var collapsing: View? = null
    private var navButton: View? = null
    private var menuView: View? = null

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateContentHeight()
        prepareToolbarButtons()
    }

    init {
        val tv = TypedValue()
        toolbarHeight = if (ctx.theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, tv, true))
            TypedValue.complexToDimensionPixelSize(tv.data, res.displayMetrics)
        else (56 * density).toInt()
    }

    fun attach() {
        // The bar must not paint over the content that slides under it.
        collapsing = appBar.findViewById(R.id.toolbarlayout_collapsing_toolbar)
        collapsingBackground = collapsing?.background
        collapsing?.background = null
        appBar.background = null
        appBar.translationZ = density * 2f

        val bg = pageBackground()
        vignette.apply {
            alpha = 0f
            visibility = View.INVISIBLE
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            translationZ = density
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(bg, withAlpha(bg, 0.85f), withAlpha(bg, 0.5f), Color.TRANSPARENT)
            )
        }
        coordinator.addView(
            vignette,
            CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toolbarHeight + (16 * density).toInt()).apply {
                gravity = Gravity.TOP
            }
        )
        coordinator.addOnLayoutChangeListener(layoutListener)
        toolbar.addOnLayoutChangeListener(layoutListener)
        prepareToolbarButtons()
    }

    fun detach() {
        coordinator.removeOnLayoutChangeListener(layoutListener)
        toolbar.removeOnLayoutChangeListener(layoutListener)
        coordinator.removeView(vignette)
        collapsing?.background = collapsingBackground
        restoreContentHeight()
        content.translationY = 0f
        floating.clear()
        prepared.clear()
    }

    fun onOffset(layout: AppBarLayout, verticalOffset: Int) {
        val range = layout.totalScrollRange
        // Not an expandable app bar: it never collapses, the effect stays off.
        val collapsedFraction = if (range <= 0 || !layout.isEnabled) 0f else abs(verticalOffset).toFloat() / range
        if (collapsedFraction < 0.9f && range > 0) sawExpanded = true
        val p = if (!sawExpanded) 0f else ((collapsedFraction - 0.9f) / 0.1f).coerceIn(0f, 1f)
        if (p == progress) return
        progress = p

        vignette.visibility = if (p > 0f) View.VISIBLE else View.INVISIBLE
        vignette.alpha = p

        // Content slides up to the very top of the screen.
        content.translationY = -toolbarHeight * p

        // Title/subtitle: SESL keeps resetting their alpha, so fade the text colour instead.
        toolbar.children.filterIsInstance<TextView>().forEach { tv ->
            val original = originalTextColors.getOrPut(tv) { tv.currentTextColor }
            tv.setTextColor(withAlpha(original, 1f - p))
        }

        prepareToolbarButtons()
        // the menu capsule keeps the same distance from the right edge as the circle has from the left
        menuView?.translationX = -sideMargin() * p
        floating.forEach { (view, drawable) ->
            drawable.alpha = (255 * p).toInt()
            view.elevation = floatingElevation * p
        }
    }

    /** Makes the content as tall as the screen again once it is translated up by [toolbarHeight]. */
    private fun updateContentHeight() {
        val range = appBar.totalScrollRange
        if (range <= 0 || !appBar.isEnabled) {
            restoreContentHeight()
            return
        }
        val collapsedHeader = appBar.height - range
        val target = coordinator.height - collapsedHeader + toolbarHeight
        val lp = content.layoutParams ?: return
        if (target <= 0 || lp.height == target) return
        if (originalContentHeight == null) originalContentHeight = lp.height
        lp.height = target
        content.layoutParams = lp
    }

    private fun restoreContentHeight() {
        val original = originalContentHeight ?: return
        content.layoutParams?.let {
            if (it.height != original) {
                it.height = original
                content.layoutParams = it
            }
        }
        originalContentHeight = null
    }

    private fun recoilColor() = ContextCompat.getColor(ctx, R.color.oui_des_btn_ripple_color)

    /** Press feedback on the navigation button and every menu item, floating bg on the buttons. */
    private fun prepareToolbarButtons() {
        val size = (48 * density).toInt()
        val floatingColor = ContextCompat.getColor(ctx, R.color.oui_des_floating_navigate_up_bg_color)
        toolbar.children.forEach { child ->
            when (child) {
                is ImageButton -> if (prepared.add(child)) {
                    navButton = child
                    // centre of the icon itself (the button has uneven padding), not of the view
                    val iconArea = { RectF(child.paddingLeft.toFloat(), child.paddingTop.toFloat(),
                        (child.width - child.paddingRight).toFloat(), (child.height - child.paddingBottom).toFloat()) }
                    val f = OuiFloatingBgDrawable(floatingColor, circle = true, sizePx = size, anchor = iconArea)
                    f.alpha = (255 * progress.coerceAtLeast(0f)).toInt()
                    floating[child] = f
                    child.background = LayerDrawable(arrayOf(f, OuiRecoilDrawable(recoilColor(), size, iconArea)))
                }
                is ActionMenuView -> {
                    menuView = child
                    if (prepared.add(child)) {
                        // one capsule hugging all the menu buttons
                        val itemsArea = {
                            var l = Float.MAX_VALUE; var r = 0f
                            child.children.forEach { c -> l = minOf(l, c.left.toFloat()); r = maxOf(r, c.right.toFloat()) }
                            if (l > r) RectF(0f, 0f, child.width.toFloat(), child.height.toFloat())
                            else RectF(l, 0f, r, child.height.toFloat())
                        }
                        val f = OuiFloatingBgDrawable(floatingColor, circle = false, sizePx = size, anchor = itemsArea)
                        f.alpha = (255 * progress.coerceAtLeast(0f)).toInt()
                        floating[child] = f
                        child.background = f
                    }
                    child.children.forEach { item ->
                        if (prepared.add(item)) item.background = OuiRecoilDrawable(recoilColor(), size)
                    }
                }
            }
        }
    }

    /** Left margin of the circle (distance of its edge from the screen edge), mirrored on the right. */
    private fun sideMargin(): Float {
        val nav = navButton ?: return 0f
        val iconCenter = nav.left + nav.paddingLeft + (nav.width - nav.paddingLeft - nav.paddingRight) / 2f
        return (iconCenter - 24 * density).coerceAtLeast(0f)
    }

    /** The colour behind the page, so the vignette is invisible against it. */
    private fun pageBackground(): Int {
        val tv = TypedValue()
        if (ctx.theme.resolveAttribute(android.R.attr.windowBackground, tv, true)) {
            if (tv.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) return tv.data
            if (tv.resourceId != 0) {
                runCatching { return ContextCompat.getColor(ctx, tv.resourceId) }
                (ContextCompat.getDrawable(ctx, tv.resourceId) as? ColorDrawable)?.let { return it.color }
            }
        }
        if (ctx.theme.resolveAttribute(android.R.attr.colorBackground, tv, true)) {
            return if (tv.resourceId != 0) ContextCompat.getColor(ctx, tv.resourceId) else tv.data
        }
        return Color.WHITE
    }

    private fun withAlpha(color: Int, a: Float) =
        Color.argb((Color.alpha(color) * a).toInt(), Color.red(color), Color.green(color), Color.blue(color))
}
