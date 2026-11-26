package dev.oneuiproject.oneuiexample.ui.main.fragments.oneui9

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import com.sec.sesl.tester.R
import dev.oneuiproject.oneui.R as iconsLibR
import dev.oneuiproject.oneui.widget.FloatingTabBar
import dev.oneuiproject.oneui.widget.TopEdgeFadeLayout
import dev.oneuiproject.oneuiexample.ui.main.core.base.AbsBaseFragment

/** Showcase of the One UI 9 style components. */
class OneUi9Fragment : AbsBaseFragment(R.layout.fragment_oneui9) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TopEdgeFadeLayout>(R.id.oneui9_fade)
            .attachTo(view.findViewById(R.id.oneui9_scroll))

        view.findViewById<View>(R.id.oneui9_navigate_up).setOnClickListener {
            findNavController().navigateUp()
        }

        val ctx = requireContext()
        fun icon(res: Int) = ContextCompat.getDrawable(ctx, res)!!
        view.findViewById<FloatingTabBar>(R.id.oneui9_tab_bar).setItems(
            listOf(
                FloatingTabBar.Item(1, "Progress", icon(R.drawable.drawer_page_icon_progressbar)),
                FloatingTabBar.Item(2, "Seek", icon(R.drawable.drawer_page_icon_seekbar)),
                FloatingTabBar.Item(3, "Pickers", icon(iconsLibR.drawable.ic_oui_calendar)),
            )
        )
    }
}
