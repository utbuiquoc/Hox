/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.search.awesomebar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.graphics.toColorInt
import androidx.fragment.app.Fragment
import androidx.lifecycle.coroutineScope
import androidx.navigation.NavController
import mozilla.components.browser.state.action.AwesomeBarAction
import mozilla.components.browser.state.store.BrowserStore
import mozilla.components.compose.base.theme.layout.AcornWindowSize
import mozilla.components.compose.browser.awesomebar.AwesomeBar
import mozilla.components.compose.browser.awesomebar.AwesomeBarColors
import mozilla.components.compose.browser.awesomebar.AwesomeBarDefaults
import mozilla.components.compose.browser.awesomebar.AwesomeBarOrientation
import mozilla.components.compose.browser.awesomebar.internal.CurrentTabDetailsInteractions.CopyClicked
import mozilla.components.compose.browser.awesomebar.internal.CurrentTabDetailsInteractions.DetailsClicked
import mozilla.components.compose.browser.awesomebar.internal.CurrentTabDetailsInteractions.EditClicked
import mozilla.components.compose.browser.awesomebar.internal.CurrentTabDetailsInteractions.ShareClicked
import mozilla.components.compose.browser.toolbar.store.BrowserEditToolbarAction.SearchQueryUpdated
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarStore
import mozilla.components.compose.browser.toolbar.ui.BrowserToolbarQuery
import mozilla.components.concept.awesomebar.AwesomeBar as ConceptAwesomeBar
import mozilla.components.lib.state.ext.observeAsComposableState
import mozilla.components.lib.state.helpers.StoreProvider.Companion.fragmentStore
import mozilla.components.support.ktx.android.view.hideKeyboard
import org.mozilla.fenix.HomeActivity
import org.mozilla.fenix.R
import org.mozilla.fenix.components.AppStore
import org.mozilla.fenix.components.Components
import org.mozilla.fenix.components.appstate.AppAction.SearchAction.SearchEnded
import org.mozilla.fenix.components.menu.compose.DESKTOP_DROPDOWN_CORNER_DP
import org.mozilla.fenix.components.metrics.MetricsUtils
import org.mozilla.fenix.ext.components
import org.mozilla.fenix.ext.getRootView
import org.mozilla.fenix.ext.isTabletInterface
import org.mozilla.fenix.search.BrowserStoreToFenixSearchMapperMiddleware
import org.mozilla.fenix.search.BrowserToolbarToFenixSearchMapperMiddleware
import org.mozilla.fenix.search.FenixSearchMiddleware
import org.mozilla.fenix.search.SearchFragmentAction
import org.mozilla.fenix.search.SearchFragmentAction.CopyCurrentWebsiteDetailsClicked
import org.mozilla.fenix.search.SearchFragmentAction.EditCurrentWebsiteDetailsClicked
import org.mozilla.fenix.search.SearchFragmentAction.ReloadCurrentWebsiteClicked
import org.mozilla.fenix.search.SearchFragmentAction.ShareCurrentWebsiteDetailsClicked
import org.mozilla.fenix.search.SearchFragmentAction.SuggestionClicked
import org.mozilla.fenix.search.SearchFragmentAction.SuggestionSelected
import org.mozilla.fenix.search.SearchFragmentState
import org.mozilla.fenix.search.SearchFragmentStore
import org.mozilla.fenix.search.createInitialSearchFragmentState
import org.mozilla.fenix.settings.SupportUtils

private const val MATERIAL_DESIGN_SCRIM = "#52000000"
private const val AWESOMEBAR_MAX_WIDTH = 600
private const val DESKTOP_DROPDOWN_GAP_DP = 4
private const val DESKTOP_DROPDOWN_MARGIN_DP = 16
private const val DESKTOP_DROPDOWN_MIN_HEIGHT_DP = 160
private const val DESKTOP_DROPDOWN_MAX_HEIGHT_DP = 520

/**
 * Provides the window bounds of the desktop edit-mode URL input box so [AwesomeBarComposable] can align
 * its floating suggestion dropdown directly beneath it on tablet interfaces.
 */
internal val LocalDesktopUrlPillAnchorBounds = compositionLocalOf<IntRect?> { null }

/**
 * Cap the width of the awesomebar content to [AcornWindowSize.Small] and center it in the available space as a staged
 * rollout change.
 */
private fun Modifier.awesomeBarContentWidth(useAddressBarFocusMode: Boolean) =
    if (useAddressBarFocusMode) {
        fillMaxWidth().wrapContentWidth(align = Alignment.CenterHorizontally).widthIn(max = AWESOMEBAR_MAX_WIDTH.dp)
    } else {
        fillMaxWidth()
    }

/**
 * Wrapper over a [Composable] to show search suggestions, responsible for its setup.
 *
 * @param activity [HomeActivity] providing the ability to open URLs and querying the current browsing mode.
 * @param fragment [Fragment] to the lifecycle of which long running operations and objects will be tied to.
 * @param modifier [Modifier] to be applied to the [Composable].
 * @param components [Components] for accessing other functionalities of the application.
 * @param appStore [AppStore] for accessing the current application state.
 * @param browserStore [BrowserStore] for accessing the current browser state.
 * @param toolbarStore [BrowserToolbarStore] for accessing the current toolbar state.
 * @param navController [NavController] for navigating to other destinations in the application.
 * @param tabId [String] Id of the current tab for which a new search was started.
 * @param showScrimWhenNoSuggestions Whether to show a scrim when no suggestions are available.
 * @param searchAccessPoint Where search was started from.
 */
class AwesomeBarComposable(
    private val activity: HomeActivity,
    private val fragment: Fragment,
    private val modifier: Modifier,
    private val components: Components,
    private val appStore: AppStore,
    private val browserStore: BrowserStore,
    private val toolbarStore: BrowserToolbarStore,
    private val navController: NavController,
    private val tabId: String? = null,
    private val showScrimWhenNoSuggestions: Boolean = false,
    private val searchAccessPoint: MetricsUtils.Source = MetricsUtils.Source.NONE,
) {
    private val searchStore by initializeSearchStore()

    /**
     * [Composable] fully integrated with [BrowserStore] and [BrowserToolbarStore] that will show search suggestions
     * whenever the users edits the current query in the toolbar.
     */
    @OptIn(ExperimentalLayoutApi::class) // for WindowInsets.isImeVisible
    @Suppress("LongMethod", "CyclomaticComplexMethod", "CognitiveComplexMethod")
    @Composable
    fun SearchSuggestions() {
        val deleteHistoryDelegate =
            remember(activity.getRootView(), searchStore) {
                activity.getRootView()?.let {
                    DeleteHistoryEntryDelegate(it, it.context.components, searchStore)
                }
            }
        val isSearchActive = appStore.observeAsComposableState { it.searchState.isSearchActive }.value
        val state = searchStore.observeAsComposableState { it }.value
        val orientation by
            remember(state.searchSuggestionsOrientedAtBottom) {
                derivedStateOf {
                    when (searchStore.state.searchSuggestionsOrientedAtBottom) {
                        true -> AwesomeBarOrientation.BOTTOM
                        false -> AwesomeBarOrientation.TOP
                    }
                }
            }
        val shouldShowClipboardBar by
            remember(
                state.showClipboardSuggestions,
                state.query,
                state.clipboardHasUrl,
            ) {
                derivedStateOf {
                    state.showClipboardSuggestions && state.query.isEmpty() && state.clipboardHasUrl
                }
            }
        val clipboardBarBackground = MaterialTheme.colorScheme.surface
        val context = LocalContext.current
        LocalConfiguration.current
        val isDesktopDropdown = context.isTabletInterface(components.settings)
        val desktopAnchorBounds = LocalDesktopUrlPillAnchorBounds.current
        val showCurrentTabData =
            remember(isDesktopDropdown) {
                !isDesktopDropdown && components.settings.showAddressBarInFocusMode
            }
        val view = LocalView.current
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        LaunchedEffect(isSearchActive) {
            if (!isSearchActive) {
                focusManager.clearFocus()
                keyboardController?.hide()
            } else {
                val hasUrl = components.clipboardHandler.containsURL()
                searchStore.dispatch(SearchFragmentAction.UpdateClipboardHasUrl(hasUrl))
            }
        }

        if (isDesktopDropdown) {
            if (isSearchActive && desktopAnchorBounds != null && desktopAnchorBounds.width > 0) {
                DesktopSearchSuggestionsPopup(
                    desktopAnchorBounds = desktopAnchorBounds,
                    state = state,
                    searchStore = searchStore,
                    orientation = orientation,
                    shouldShowClipboardBar = shouldShowClipboardBar,
                    deleteHistoryDelegate = deleteHistoryDelegate,
                )
            }
            return
        }

        if (isSearchActive && shouldShowClipboardBar && orientation == AwesomeBarOrientation.TOP) {
            val url = components.clipboardHandler.extractURL()

            ClipboardSuggestionBar(
                modifier = Modifier.awesomeBarContentWidth(showCurrentTabData),
                shouldUseBottomToolbar = components.settings.shouldUseBottomToolbar,
                backgroundColor = clipboardBarBackground,
                onClick = {
                    url?.let {
                        toolbarStore.dispatch(
                            SearchQueryUpdated(query = BrowserToolbarQuery(url), isQueryPrefilled = true)
                        )
                    }
                },
            )
        }

        if (isSearchActive) {
            if (state.showSearchSuggestionsHint) {
                PrivateSuggestionsOnboardingCard(
                    modifier = Modifier.awesomeBarContentWidth(showCurrentTabData),
                    searchStore = searchStore,
                )
            }
            if (state.shouldShowSearchSuggestions) {
                Box(
                    modifier =
                        modifier.background(MaterialTheme.colorScheme.surface).fillMaxSize().pointerInput(
                            WindowInsets.isImeVisible
                        ) {
                            detectTapGestures(
                                onPress = {
                                    view.hideKeyboard()
                                },
                                onTap = {
                                    focusManager.clearFocus()
                                    appStore.dispatch(SearchEnded)
                                },
                            )
                        }
                ) {
                    val currentTabDetailsToShow =
                        when (state.query.isBlank() && showCurrentTabData) {
                            true -> state.currentTabData
                            else -> null
                        }

                    Box(modifier = Modifier.awesomeBarContentWidth(showCurrentTabData)) {
                        SearchSuggestionsAwesomeBar(
                            state = state,
                            searchStore = searchStore,
                            currentTabDetailsToShow = currentTabDetailsToShow,
                            orientation = orientation,
                            colors = AwesomeBarDefaults.colors(),
                            deleteHistoryDelegate = deleteHistoryDelegate,
                            onVisibilityStateUpdated = {
                                browserStore.dispatch(AwesomeBarAction.VisibilityStateUpdated(it))
                            },
                            onScroll = { view.hideKeyboard() },
                        )
                    }
                }
            } else if (showScrimWhenNoSuggestions) {
                Spacer(
                    modifier =
                        modifier.background(Color(MATERIAL_DESIGN_SCRIM.toColorInt())).fillMaxSize().pointerInput(
                            WindowInsets.isImeVisible
                        ) {
                            detectTapGestures(
                                onPress = {
                                    keyboardController?.hide()
                                },
                                onTap = {
                                    focusManager.clearFocus()
                                    appStore.dispatch(SearchEnded)
                                },
                            )
                        }
                )
            }
        }

        if (isSearchActive && shouldShowClipboardBar && orientation == AwesomeBarOrientation.BOTTOM) {
            val url = components.clipboardHandler.extractURL()

            ClipboardSuggestionBar(
                modifier = Modifier.awesomeBarContentWidth(showCurrentTabData),
                shouldUseBottomToolbar = components.settings.shouldUseBottomToolbar,
                backgroundColor = clipboardBarBackground,
                onClick = {
                    url?.let {
                        toolbarStore.dispatch(
                            SearchQueryUpdated(query = BrowserToolbarQuery(url), isQueryPrefilled = true)
                        )
                    }
                },
            )
        }
    }

    @Composable
    private fun DesktopSearchSuggestionsPopup(
        desktopAnchorBounds: IntRect,
        state: SearchFragmentState,
        searchStore: SearchFragmentStore,
        orientation: AwesomeBarOrientation,
        shouldShowClipboardBar: Boolean,
        deleteHistoryDelegate: DeleteHistoryEntryDelegate?,
    ) {
        val shouldOpenPopup =
            shouldShowClipboardBar || state.showSearchSuggestionsHint || state.shouldShowSearchSuggestions
        if (!shouldOpenPopup) return

        val view = LocalView.current
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val verticalGapPx = with(density) { DESKTOP_DROPDOWN_GAP_DP.dp.roundToPx() }
        val marginPx = with(density) { DESKTOP_DROPDOWN_MARGIN_DP.dp.roundToPx() }
        val minHeightPx = with(density) { DESKTOP_DROPDOWN_MIN_HEIGHT_DP.dp.roundToPx() }
        val imeBottomPx = WindowInsets.ime.getBottom(density)
        val navBottomPx = WindowInsets.navigationBars.getBottom(density)
        val statusTopPx = WindowInsets.statusBars.getTop(density)
        val rootHeightPx =
            view.rootView?.height?.takeIf { it > 0 }
                ?: with(density) { configuration.screenHeightDp.dp.roundToPx() }
        val availableHeightPx =
            if (orientation == AwesomeBarOrientation.BOTTOM) {
                (desktopAnchorBounds.top - verticalGapPx) - statusTopPx - marginPx
            } else {
                val effectiveBottom = rootHeightPx - maxOf(imeBottomPx, navBottomPx) - marginPx
                effectiveBottom - (desktopAnchorBounds.bottom + verticalGapPx)
            }.coerceAtLeast(minHeightPx)
        val maxPopupHeightDp =
            with(density) { availableHeightPx.toDp() }.coerceAtMost(DESKTOP_DROPDOWN_MAX_HEIGHT_DP.dp)

        var hasVisibleSuggestions by
            remember(state.shouldShowSearchSuggestions, state.searchSuggestionsProviders) {
                mutableStateOf(false)
            }
        val hasVisibleCard =
            shouldShowClipboardBar ||
                state.showSearchSuggestionsHint ||
                (state.shouldShowSearchSuggestions && hasVisibleSuggestions)

        Popup(
            popupPositionProvider =
                remember(desktopAnchorBounds, orientation, verticalGapPx, marginPx) {
                    object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize,
                        ): IntOffset {
                            val x =
                                desktopAnchorBounds.left.coerceIn(
                                    0,
                                    (windowSize.width - popupContentSize.width).coerceAtLeast(0),
                                )
                            val y =
                                if (orientation == AwesomeBarOrientation.BOTTOM) {
                                    (desktopAnchorBounds.top - popupContentSize.height - verticalGapPx)
                                        .coerceAtLeast(marginPx)
                                } else {
                                    (desktopAnchorBounds.bottom + verticalGapPx)
                                        .coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0))
                                }
                            return IntOffset(x, y)
                        }
                    }
                },
            properties =
                PopupProperties(
                    focusable = false,
                    dismissOnClickOutside = false,
                    dismissOnBackPress = false,
                ),
        ) {
            val cardShape = RoundedCornerShape(DESKTOP_DROPDOWN_CORNER_DP.dp)
            Surface(
                modifier =
                    Modifier.width(with(density) { desktopAnchorBounds.width.toDp() })
                        .heightIn(max = maxPopupHeightDp),
                shape = cardShape,
                color =
                    if (hasVisibleCard) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        Color.Transparent
                    },
                border =
                    if (hasVisibleCard) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    } else {
                        null
                    },
                shadowElevation = if (hasVisibleCard) 8.dp else 0.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (shouldShowClipboardBar) {
                        val url = components.clipboardHandler.extractURL()
                        ClipboardSuggestionBar(
                            modifier = Modifier.fillMaxWidth(),
                            shouldUseBottomToolbar = components.settings.shouldUseBottomToolbar,
                            backgroundColor = Color.Transparent,
                            onClick = {
                                url?.let {
                                    toolbarStore.dispatch(
                                        SearchQueryUpdated(query = BrowserToolbarQuery(url), isQueryPrefilled = true)
                                    )
                                }
                            },
                        )
                    }
                    if (state.showSearchSuggestionsHint) {
                        PrivateSuggestionsOnboardingCard(
                            modifier = Modifier.fillMaxWidth(),
                            searchStore = searchStore,
                        )
                    }
                    if (state.shouldShowSearchSuggestions) {
                        SearchSuggestionsAwesomeBar(
                            state = state,
                            searchStore = searchStore,
                            currentTabDetailsToShow = null,
                            orientation = orientation,
                            colors = AwesomeBarDefaults.colors(background = Color.Transparent),
                            deleteHistoryDelegate = deleteHistoryDelegate,
                            onVisibilityStateUpdated = { visibilityState ->
                                hasVisibleSuggestions =
                                    visibilityState.visibleProviderGroups.values.any { it.isNotEmpty() }
                                browserStore.dispatch(AwesomeBarAction.VisibilityStateUpdated(visibilityState))
                            },
                            onScroll = { view.hideKeyboard() },
                            modifier = Modifier.padding(vertical = if (hasVisibleSuggestions) 4.dp else 0.dp),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PrivateSuggestionsOnboardingCard(
        modifier: Modifier,
        searchStore: SearchFragmentStore,
    ) {
        PrivateSuggestionsCard(
            modifier = modifier,
            onSearchSuggestionsInPrivateModeAllowed = {
                components.settings.shouldShowSearchSuggestionsInPrivate = true
                components.settings.showSearchSuggestionsInPrivateOnboardingFinished = true
                searchStore.dispatch(SearchFragmentAction.SetShowSearchSuggestions(true))
                searchStore.dispatch(SearchFragmentAction.AllowSearchSuggestionsInPrivateModePrompt(false))
                searchStore.dispatch(SearchFragmentAction.PrivateSuggestionsCardAccepted)
            },
            onSearchSuggestionsInPrivateModeBlocked = {
                components.settings.shouldShowSearchSuggestionsInPrivate = false
                components.settings.showSearchSuggestionsInPrivateOnboardingFinished = true
                searchStore.dispatch(SearchFragmentAction.AllowSearchSuggestionsInPrivateModePrompt(false))
            },
            onLearnMoreClick = {
                components.useCases.fenixBrowserUseCases.loadUrlOrSearch(
                    searchTermOrURL =
                        SupportUtils.getGenericSumoURLForTopic(SupportUtils.SumoTopic.SEARCH_SUGGESTION),
                    newTab = appStore.state.searchState.sourceTabId == null,
                    private = true,
                )
                appStore.dispatch(SearchEnded)
                if (navController.currentDestination?.id != R.id.browserFragment) {
                    navController.navigate(R.id.browserFragment)
                }
            },
        )
    }

    @Composable
    private fun SearchSuggestionsAwesomeBar(
        state: SearchFragmentState,
        searchStore: SearchFragmentStore,
        currentTabDetailsToShow: mozilla.components.compose.browser.awesomebar.internal.CurrentTabData?,
        orientation: AwesomeBarOrientation,
        colors: AwesomeBarColors,
        deleteHistoryDelegate: DeleteHistoryEntryDelegate?,
        onVisibilityStateUpdated: (ConceptAwesomeBar.VisibilityState) -> Unit,
        onScroll: () -> Unit,
        modifier: Modifier = Modifier,
    ) {
        Box(modifier = modifier) {
            AwesomeBar(
                text = state.query,
                currentTabData = currentTabDetailsToShow,
                colors = colors,
                providers = state.searchSuggestionsProviders,
                hiddenSuggestions = state.hiddenSuggestions,
                orientation = orientation,
                onSuggestionClicked = { suggestion ->
                    searchStore.dispatch(SuggestionClicked(suggestion))
                },
                onAutoComplete = { suggestion ->
                    searchStore.dispatch(SuggestionSelected(suggestion))
                },
                onRemoveClicked = { suggestion ->
                    deleteHistoryDelegate?.handleDeletingHistoryEntry(suggestion)
                },
                onCurrentSiteDetailsInteraction = {
                    when (it) {
                        ShareClicked -> searchStore.dispatch(ShareCurrentWebsiteDetailsClicked)
                        CopyClicked -> searchStore.dispatch(CopyCurrentWebsiteDetailsClicked)
                        EditClicked -> searchStore.dispatch(EditCurrentWebsiteDetailsClicked)
                        DetailsClicked -> searchStore.dispatch(ReloadCurrentWebsiteClicked)
                    }
                },
                onVisibilityStateUpdated = onVisibilityStateUpdated,
                onScroll = onScroll,
                profiler = components.core.engine.profiler,
            )
        }
    }

    private fun initializeSearchStore() =
        fragment.fragmentStore(
            createInitialSearchFragmentState(
                context = activity,
                components = components,
                tabId = tabId,
                pastedText = null,
                searchAccessPoint = searchAccessPoint,
            )
        ) {
            val lifecycleScope = fragment.viewLifecycleOwner.lifecycle.coroutineScope

            SearchFragmentStore(
                initialState = it,
                middleware =
                    listOf(
                        BrowserToolbarToFenixSearchMapperMiddleware(
                            toolbarStore = toolbarStore,
                            browsingModeManager = activity.browsingModeManager,
                            scope = lifecycleScope,
                            browserStore = browserStore,
                        ),
                        BrowserStoreToFenixSearchMapperMiddleware(
                            browserStore = browserStore,
                            scope = lifecycleScope,
                            appStore = components.appStore,
                        ),
                        FenixSearchMiddleware(
                            fragment = fragment,
                            engine = components.core.engine,
                            useCases = components.useCases,
                            nimbusComponents = components.nimbus,
                            settings = components.settings,
                            appStore = appStore,
                            browserStore = browserStore,
                            toolbarStore = toolbarStore,
                            navController = navController,
                            browsingModeManager = activity.browsingModeManager,
                            shareUseCases = components.useCases.shareUseCases,
                            clipboardHandler = components.clipboardHandler,
                        ),
                    ),
            )
        }
}
