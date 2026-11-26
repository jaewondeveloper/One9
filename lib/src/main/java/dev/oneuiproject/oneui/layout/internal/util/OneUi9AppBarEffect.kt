package dev.oneuiproject.oneui.layout.internal.util

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import dev.oneuiproject.oneui.design.R
import dev.oneuiproject.oneui.widget.internal.OuiFloatingBgDrawable
import dev.oneuiproject.oneui.widget.internal.OuiRecoilDrawable
import kotlin.math.abs

/**
 * One UI 9 app bar behaviour, following the state machine of SESL 9's `AppBarLayout`
 * (floating toolbar mode):
 *
 *  - EXPANDED -> COLLAPSED: the normal collapsing title (SESL 8 `AppBarLayout`, untouched).
 *  - COLLAPSED -> HIDING -> HIDED: once collapsed, scrolling the content hides the bar 1:1
 *    (scrolling back shows it again). While hiding:
 *      * the title/subtitle fades out,
 *      * the content moves up to the very top of the screen,
 *      * a vignette in the page background colour fades the content at the top edge,
 *      * the navigation button / menu get their floating round backgrounds
 *        (alpha 0 -> 1 in 150ms, linear: `ProjectionView` of SESL 9).
 *  - The toolbar buttons show the round recoil press feedback at all times.
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
    private val bottomVignette = View(ctx)
    private val floatingElevation = res.getDimension(R.dimen.oui_des_floating_navigate_up_elevation)
    private val toolbarHeight: Int

    /** How far the bar has been hidden, 0 (collapsed) .. [toolbarHeight] (hided). */
    private var hideOffset = 0f
    private var lastScroll = Int.MIN_VALUE
    private var scrollable: View? = null
    private var floatingAlpha = 0f
    private var floatingAnimator: ValueAnimator? = null

    private val floating = HashMap<View, Drawable>()
    private val prepared = HashSet<View>()
    private val originalTextColors = HashMap<TextView, Int>()
    private var originalContentHeight: Int? = null
    private var collapsingBackground: Drawable? = null
    private var collapsing: View? = null
    private var navButton: View? = null
    private var menuView: View? = null

    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        if (originalContentHeight != null) updateContentHeight()
        prepareToolbarButtons()
    }

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener { onContentScrolled() }

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
        bottomVignette.apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            translationZ = density
            background = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(bg, withAlpha(bg, 0.85f), withAlpha(bg, 0.45f), Color.TRANSPARENT)
            )
        }
        coordinator.addView(
            bottomVignette,
            CoordinatorLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (28 * density).toInt()).apply {
                gravity = Gravity.BOTTOM
            }
        )
        toolbar.addOnLayoutChangeListener(layoutListener)
        coordinator.viewTreeObserver.addOnScrollChangedListener(scrollListener)
        prepareToolbarButtons()
    }

    fun detach() {
        floatingAnimator?.cancel()
        if (coordinator.viewTreeObserver.isAlive) coordinator.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        coordinator.removeOnLayoutChangeListener(layoutListener)
        toolbar.removeOnLayoutChangeListener(layoutListener)
        coordinator.removeView(vignette)
        coordinator.removeView(bottomVignette)
        collapsing?.background = collapsingBackground
        restoreContentHeight()
        content.translationY = 0f
        floating.clear()
        prepared.clear()
    }

    /** SESL 9 `AppBarLayout.onOffsetChanged`: the bar is collapsed when the offset reaches the collapsed height. */
    fun onOffset(layout: AppBarLayout, verticalOffset: Int) {
        val range = layout.totalScrollRange
        val collapsed = range <= 0 || abs(verticalOffset) >= range
        if (!collapsed && hideOffset != 0f) {
            // expanding again: the bar is no longer (partly) hidden
            hideOffset = 0f
            lastScroll = Int.MIN_VALUE
            applyHide()
        }
    }

    private fun isCollapsed(): Boolean {
        val range = appBar.totalScrollRange
        return range <= 0 || abs(appBar.top) >= range
    }

    /** Scrolling the content hides (scroll up) or shows (scroll down) the collapsed bar 1:1. */
    private fun onContentScrolled() {
        val target = scrollable?.takeIf { it.isAttachedToWindow && it.isShown } ?: findScrollable(content)?.also {
            scrollable = it
            lastScroll = Int.MIN_VALUE
        } ?: return
        val current = scrollOffset(target)
        if (lastScroll == Int.MIN_VALUE) {
            lastScroll = current
            return
        }
        val delta = current - lastScroll
        lastScroll = current
        if (delta == 0 || !isCollapsed()) return
        val next = (hideOffset + delta).coerceIn(0f, toolbarHeight.toFloat())
        if (next != hideOffset) {
            hideOffset = next
            applyHide()
        }
    }

    private fun applyHide() {
        val p = hideOffset / toolbarHeight

        // Content slides up to the very top of the screen; it is only made taller while the bar is hiding,
        // so that at rest the end of the list keeps its normal bottom edge.
        if (hideOffset > 0f) updateContentHeight() else restoreContentHeight()
        content.translationY = -hideOffset

        // Vignette fades in with the hiding bar.
        vignette.visibility = if (p > 0f) View.VISIBLE else View.INVISIBLE
        vignette.alpha = (p * 3f).coerceAtMost(1f)

        // Title/subtitle: SESL keeps resetting their alpha, so fade the text colour instead.
        val titleAlpha = (1f - p * 2f).coerceIn(0f, 1f)
        toolbar.children.filterIsInstance<TextView>().forEach { tv ->
            val original = originalTextColors.getOrPut(tv) { tv.currentTextColor }
            tv.setTextColor(withAlpha(original, titleAlpha))
        }

        // Floating backgrounds: shown as soon as the bar starts hiding, 150ms linear (SESL 9 ProjectionView).
        animateFloating(if (hideOffset > 0f) 1f else 0f)
    }

    private fun animateFloating(target: Float) {
        if (floatingAlpha == target && floatingAnimator?.isRunning != true) return
        if (floatingAnimator?.isRunning == true && floatingAnimator?.animatedValue == target) return
        floatingAnimator?.cancel()
        floatingAnimator = ValueAnimator.ofFloat(floatingAlpha, target).apply {
            duration = 150
            interpolator = LinearInterpolator()
            addUpdateListener {
                floatingAlpha = it.animatedValue as Float
                setFloatingAlpha(floatingAlpha)
            }
            start()
        }
    }

    private fun setFloatingAlpha(a: Float) {
        prepareToolbarButtons()
        floating.forEach { (view, drawable) ->
            drawable.alpha = (255 * a).toInt()
            view.elevation = floatingElevation * a
        }
    }

    /** Makes the content as tall as the screen again once it is translated up by [toolbarHeight]. */
    private fun updateContentHeight() {
        if (appBar.height <= 0) return
        val collapsedHeader = appBar.height - appBar.totalScrollRange.coerceAtLeast(0)
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
                    val iconArea = {
                        RectF(child.paddingLeft.toFloat(), child.paddingTop.toFloat(),
                            (child.width - child.paddingRight).toFloat(), (child.height - child.paddingBottom).toFloat())
                    }
                    val f = OuiFloatingBgDrawable(floatingColor, circle = true, sizePx = size, anchor = iconArea)
                    f.alpha = (255 * floatingAlpha).toInt()
                    floating[child] = f
                    child.background = LayerDrawable(arrayOf(f, OuiRecoilDrawable(recoilColor(), size, iconArea)))
                }
                is ActionMenuView -> {
                    menuView = child
                    if (prepared.add(child)) {
                        // one capsule hugging all the menu buttons
                        // same geometry as the circle: icon centre +/- 24dp, so both sides have identical margins
                        val itemsArea = {
                            val visible = child.children.filter { it.visibility != View.GONE }.toList()
                            if (visible.isEmpty()) RectF(0f, 0f, child.width.toFloat(), child.height.toFloat())
                            else {
                                val first = visible.first()
                                val last = visible.last()
                                val firstCenter = first.left + first.translationX + first.width / 2f
                                val lastCenter = last.left + last.translationX + last.width / 2f
                                RectF(firstCenter - 24 * density, 0f, lastCenter + 24 * density, child.height.toFloat())
                            }
                        }
                        val f = OuiFloatingBgDrawable(floatingColor, circle = false, sizePx = size, anchor = itemsArea)
                        f.alpha = (255 * floatingAlpha).toInt()
                        floating[child] = f
                        child.background = f
                    }
                    var index = 0
                    child.children.filter { it.visibility != View.GONE }.forEach { item ->
                        if (prepared.add(item)) item.background = OuiRecoilDrawable(recoilColor(), size)
                        // several buttons: each one overlaps the previous one slightly, the capsule covers them all
                        item.translationX = -OVERLAP_DP * density * index
                        index++
                    }
                }
            }
        }
        // the menu keeps the same distance from the right edge as the navigation icon has from the left
        menuView?.translationX = -menuShift()
    }

    /**
     * How far the menu has to move left so that the centre of its last icon has the same distance from
     * the right edge as the navigation icon has from the left edge.
     */
    private fun menuShift(): Float {
        val nav = navButton ?: return 0f
        val menu = menuView as? ViewGroup ?: return 0f
        val last = menu.children.lastOrNull { it.visibility != View.GONE } ?: return 0f
        if (toolbar.width == 0 || last.width == 0) return 0f
        val leftDistance = nav.left + nav.paddingLeft + (nav.width - nav.paddingLeft - nav.paddingRight) / 2f
        val rightDistance = toolbar.width - (menu.left + last.left + last.translationX + last.width / 2f)
        return (leftDistance - rightDistance).coerceAtLeast(0f)
    }

    private fun findScrollable(root: View, depth: Int = 0): View? {
        if (depth > 10) return null
        if (root is RecyclerView || root is NestedScrollView || root is android.widget.ScrollView) {
            if (root.height > 0 && (root.canScrollVertically(1) || root.canScrollVertically(-1))) return root
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) findScrollable(root.getChildAt(i), depth + 1)?.let { return it }
        }
        return null
    }

    private fun scrollOffset(v: View): Int = if (v is RecyclerView) v.computeVerticalScrollOffset() else v.scrollY

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

    private companion object {
        /** Slight overlap of neighbouring menu buttons (dp). */
        const val OVERLAP_DP = 6f
    }
}
