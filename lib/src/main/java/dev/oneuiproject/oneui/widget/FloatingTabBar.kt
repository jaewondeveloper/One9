package dev.oneuiproject.oneui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import dev.oneuiproject.oneui.design.R

/**
 * A One UI 9 style floating segment tab bar: a capsule container (56dp high, max 430dp wide)
 * with a capsule shaped highlight behind the selected item.
 *
 * ```kotlin
 * bar.setItems(listOf(FloatingTabBar.Item(1, "Home", icon1), FloatingTabBar.Item(2, "Settings", icon2)))
 * bar.onItemSelectedListener = FloatingTabBar.OnItemSelectedListener { item, index -> }
 * ```
 */
class FloatingTabBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    data class Item(val id: Int, val label: CharSequence, val icon: Drawable)

    fun interface OnItemSelectedListener {
        fun onItemSelected(item: Item, index: Int)
    }

    var onItemSelectedListener: OnItemSelectedListener? = null

    var selectedIndex: Int = -1
        private set

    private val res = context.resources
    private val items = ArrayList<Item>()
    private val itemViews = ArrayList<LinearLayout>()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tmp = RectF()
    private val barHeight = res.getDimensionPixelSize(R.dimen.oui_des_floating_tab_height)
    private val maxBarWidth = res.getDimensionPixelSize(R.dimen.oui_des_floating_tab_max_width)
    private val selectedTextColor = ContextCompat.getColor(context, R.color.oui_des_floating_tab_text_selected_color)
    private val defaultTextColor = ContextCompat.getColor(context, R.color.oui_des_floating_tab_text_default_color)
    private var indicatorLeft = 0f
    private var indicatorRight = 0f
    private var indicatorAnimator: ValueAnimator? = null

    init {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setWillNotDraw(false)
        val pad = res.getDimensionPixelSize(R.dimen.oui_des_floating_tab_inner_padding)
        setPadding(pad, pad, pad, pad)
        elevation = res.getDimension(R.dimen.oui_des_floating_tab_elevation)

        val alpha = TypedValue().also { res.getValue(R.dimen.oui_des_floating_tab_bg_alpha, it, true) }.float
        bgPaint.color = ContextCompat.getColor(context, R.color.oui_des_floating_tab_bg_color)
        bgPaint.alpha = (alpha * 255).toInt()
        selectedPaint.color = ContextCompat.getColor(context, R.color.oui_des_floating_tab_selected_bg_color)

        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.height / 2f)
            }
        }
    }

    fun setItems(newItems: List<Item>, selected: Int = 0) {
        items.clear()
        items.addAll(newItems)
        itemViews.forEach { removeView(it) }
        itemViews.clear()
        val iconSize = res.getDimensionPixelSize(R.dimen.oui_des_floating_tab_icon_size)
        newItems.forEachIndexed { index, item ->
            val cell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = item.label
                setOnClickListener { select(index, true, notify = true) }
            }
            val icon = ImageView(context).apply {
                setImageDrawable(DrawableCompat.wrap(item.icon.mutate()))
            }
            val label = TextView(context).apply {
                text = item.label
                maxLines = 1
                setTextSize(TypedValue.COMPLEX_UNIT_PX, res.getDimension(R.dimen.oui_des_floating_tab_text_size))
            }
            cell.addView(icon, LinearLayout.LayoutParams(iconSize, iconSize))
            cell.addView(label, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            itemViews.add(cell)
        }
        selectedIndex = -1
        if (newItems.isNotEmpty()) select(selected.coerceIn(0, newItems.lastIndex), animate = false, notify = false)
    }

    fun select(index: Int, animate: Boolean = true, notify: Boolean = false) {
        if (index !in itemViews.indices || index == selectedIndex) return
        selectedIndex = index
        itemViews.forEachIndexed { i, cell -> applyColors(cell, i == index) }
        val cell = itemViews[index]
        if (cell.width > 0 && animate) {
            animateIndicatorTo(cell.left.toFloat(), cell.right.toFloat())
        } else {
            indicatorAnimator?.cancel()
            indicatorLeft = cell.left.toFloat()
            indicatorRight = cell.right.toFloat()
            invalidate()
        }
        if (notify) onItemSelectedListener?.onItemSelected(items[index], index)
    }

    private fun applyColors(cell: LinearLayout, selected: Boolean) {
        val color = if (selected) selectedTextColor else defaultTextColor
        (cell.getChildAt(0) as ImageView).drawable?.let { DrawableCompat.setTint(it, color) }
        (cell.getChildAt(1) as TextView).setTextColor(color)
    }

    private fun animateIndicatorTo(left: Float, right: Float) {
        indicatorAnimator?.cancel()
        val startL = indicatorLeft
        val startR = indicatorRight
        indicatorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = PathInterpolator(0.22f, 0.25f, 0f, 1f)
            addUpdateListener {
                val f = it.animatedFraction
                indicatorLeft = startL + (left - startL) * f
                indicatorRight = startR + (right - startR) * f
                invalidate()
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val size = MeasureSpec.getSize(widthMeasureSpec)
        val w = if (mode == MeasureSpec.UNSPECIFIED) widthMeasureSpec
        else MeasureSpec.makeMeasureSpec(minOf(size, maxBarWidth), mode)
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(barHeight, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (indicatorAnimator?.isRunning != true) {
            itemViews.getOrNull(selectedIndex)?.let {
                indicatorLeft = it.left.toFloat()
                indicatorRight = it.right.toFloat()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        tmp.set(0f, 0f, width.toFloat(), h)
        canvas.drawRoundRect(tmp, h / 2f, h / 2f, bgPaint)
        if (selectedIndex >= 0) {
            tmp.set(indicatorLeft, paddingTop.toFloat(), indicatorRight, h - paddingBottom)
            val r = tmp.height() / 2f
            canvas.drawRoundRect(tmp, r, r, selectedPaint)
        }
    }

    override fun onDetachedFromWindow() {
        indicatorAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
