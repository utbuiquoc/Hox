/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.tabhistory

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.R as materialR
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import mozilla.components.browser.state.action.EngineAction
import mozilla.components.browser.state.selector.findCustomTabOrSelectedTab
import mozilla.components.lib.state.ext.flowScoped
import mozilla.components.support.ktx.android.content.getColorFromAttr
import mozilla.components.support.ktx.android.util.dpToPx
import org.mozilla.fenix.R
import org.mozilla.fenix.components.menu.compose.DESKTOP_DROPDOWN_CORNER_DP
import org.mozilla.fenix.components.menu.compose.DESKTOP_DROPDOWN_WIDTH_DP
import org.mozilla.fenix.components.menu.compose.calculateDesktopDropdownMaxHeight
import org.mozilla.fenix.components.menu.compose.configureAsDesktopDropdown
import org.mozilla.fenix.components.menu.compose.updateDesktopDropdownLayout
import org.mozilla.fenix.databinding.FragmentTabHistoryDialogBinding
import org.mozilla.fenix.ext.requireComponents

private const val DROPDOWN_BORDER_WIDTH_DP = 1

class TabHistoryDialogFragment : BottomSheetDialogFragment() {

    var customTabSessionId: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            configureAsDesktopDropdown(
                fragment = this@TabHistoryDialogFragment,
                gravity = Gravity.TOP or Gravity.START,
                widthDp = DESKTOP_DROPDOWN_WIDTH_DP,
            )
        }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dialog?.updateDesktopDropdownLayout(
            fragment = this,
            gravity = Gravity.TOP or Gravity.START,
            widthDp = DESKTOP_DROPDOWN_WIDTH_DP,
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? = inflater.inflate(R.layout.fragment_tab_history_dialog, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val binding = FragmentTabHistoryDialogBinding.bind(view)

        val metrics = resources.displayMetrics
        view.background =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = DESKTOP_DROPDOWN_CORNER_DP.dpToPx(metrics).toFloat()
                setColor(view.context.getColorFromAttr(materialR.attr.colorSurface))
                setStroke(
                    DROPDOWN_BORDER_WIDTH_DP.dpToPx(metrics),
                    view.context.getColorFromAttr(materialR.attr.colorOutlineVariant),
                )
            }
        view.elevation = 0f
        view.clipToOutline = true

        customTabSessionId = requireArguments().getString(EXTRA_SESSION_ID)

        val controller =
            DefaultTabHistoryController(
                navController = findNavController(),
                goToHistoryIndexUseCase = requireComponents.useCases.sessionUseCases.goToHistoryIndex,
                customTabId = customTabSessionId,
            )
        val tabHistoryView =
            TabHistoryView(
                container = binding.tabHistoryLayout,
                expandDialog = ::expand,
                interactor = TabHistoryInteractor(controller),
            )

        val maxHeightPx = (calculateDesktopDropdownMaxHeight().value * metrics.density).toInt()
        view.findViewById<RecyclerView>(R.id.tabHistoryRecyclerView)
            ?.updateLayoutParams<ConstraintLayout.LayoutParams> {
                constrainedHeight = true
                matchConstraintMaxHeight = maxHeightPx
            }

        // flush the session state for showing the most recent the engine session state of the selected tab.
        requireComponents.core.store.state.selectedTabId?.let {
            requireComponents.core.store.dispatch(EngineAction.FlushEngineSessionStateAction(it))
        }

        // flush the session state for showing the most recent the engine session state of the custom tab.
        customTabSessionId?.let {
            requireComponents.core.store.dispatch(EngineAction.FlushEngineSessionStateAction(it))
        }

        requireComponents.core.store.flowScoped(viewLifecycleOwner, Dispatchers.Main) { flow ->
            flow
                .mapNotNull { state -> state.findCustomTabOrSelectedTab(customTabSessionId)?.content?.history }
                .distinctUntilChanged()
                .collect { historyState ->
                    tabHistoryView.updateState(historyState)
                }
        }
    }

    private fun expand() = Unit

    companion object {
        const val EXTRA_SESSION_ID = "activeSessionId"
        val NAME: String =
            TabHistoryDialogFragment::class.java.canonicalName?.substringAfterLast('.')
                ?: TabHistoryDialogFragment::class.java.simpleName
    }
}
