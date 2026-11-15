/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package mozilla.components.compose.browser.toolbar

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.IntRect
import mozilla.components.compose.browser.toolbar.concept.Action.ActionButton
import mozilla.components.compose.browser.toolbar.concept.BrowserToolbarTestTags.ADDRESSBAR_EDIT_MODE_HORIZONTAL_DIVIDER
import mozilla.components.compose.browser.toolbar.concept.BrowserToolbarTestTags.ADDRESSBAR_SEARCH_BOX
import mozilla.components.compose.browser.toolbar.store.BrowserToolbarInteraction.BrowserToolbarEvent
import mozilla.components.compose.browser.toolbar.store.ToolbarGravity
import mozilla.components.compose.browser.toolbar.store.ToolbarGravity.Bottom
import mozilla.components.compose.browser.toolbar.store.ToolbarGravity.Top
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private const val TOOLBAR = "TOOLBAR"

@RunWith(RobolectricTestRunner::class)
class BrowserEditToolbarTest {

    @get:Rule val composeTestRule = createComposeRule()

    private var gravity: ToolbarGravity by mutableStateOf(Top)
    private var showDivider by mutableStateOf(true)

    @Test
    fun `WHEN the divider visibility is not specified THEN the divider is shown for both toolbar positions`() {
        composeTestRule.setContent {
            BrowserEditToolbar(
                query = "",
                hint = "Search",
                gravity = gravity,
                onInteraction = {},
            )
        }

        listOf(Top, Bottom).forEach {
            gravity = it
            composeTestRule.onNodeWithTag(ADDRESSBAR_EDIT_MODE_HORIZONTAL_DIVIDER).assertExists()
        }
    }

    @Test
    fun `WHEN the divider is shown THEN it is on the edge of the toolbar facing the webpage`() {
        setToolbar()

        gravity = Top
        assertEquals(toolbarBounds().bottom, dividerBounds().bottom)

        gravity = Bottom
        assertEquals(toolbarBounds().top, dividerBounds().top)
    }

    @Test
    fun `WHEN the divider is hidden THEN it is not shown for both toolbar positions`() {
        showDivider = false
        setToolbar()

        listOf(Top, Bottom).forEach {
            gravity = it
            composeTestRule.onNodeWithTag(ADDRESSBAR_EDIT_MODE_HORIZONTAL_DIVIDER).assertDoesNotExist()
        }
    }

    @Test
    fun `WHEN the divider is hidden THEN the toolbar size stays the same`() {
        setToolbar()

        listOf(Top, Bottom).forEach {
            gravity = it
            showDivider = true
            val toolbarBoundsWithDivider = toolbarBounds()

            showDivider = false

            assertEquals(toolbarBoundsWithDivider, toolbarBounds())
        }
    }

    @Test
    fun `WHEN useDesktopEditToolbar is true THEN start and end browser actions remain visible and URL pill bounds are reported`() {
        val startAction = ActionButton(
            drawable = ColorDrawable(0),
            contentDescription = "Back",
            onClick = object : BrowserToolbarEvent {},
            testTag = "DESKTOP_START_ACTION",
        )
        val endAction = ActionButton(
            drawable = ColorDrawable(0),
            contentDescription = "Menu",
            onClick = object : BrowserToolbarEvent {},
            testTag = "DESKTOP_END_ACTION",
        )
        var reportedBounds: IntRect? = null
        var useDesktop by mutableStateOf(true)

        composeTestRule.setContent {
            BrowserEditToolbar(
                query = "https://example.com",
                hint = "Search",
                browserActionsStart = listOf(startAction),
                browserActionsEnd = listOf(endAction),
                useDesktopEditToolbar = useDesktop,
                onDesktopUrlPillBoundsChanged = { reportedBounds = it },
                onInteraction = {},
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("DESKTOP_START_ACTION").assertExists()
        composeTestRule.onNodeWithTag("DESKTOP_END_ACTION").assertExists()
        assertNotNull(reportedBounds)
        assertTrue((reportedBounds?.width ?: 0) > 0)
        assertTrue((reportedBounds?.height ?: 0) > 0)

        useDesktop = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("DESKTOP_START_ACTION").assertDoesNotExist()
        composeTestRule.onNodeWithTag("DESKTOP_END_ACTION").assertDoesNotExist()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun `WHEN Escape is pressed in desktop edit toolbar THEN onDismissDesktopSearch is invoked`() {
        var dismissed = false

        composeTestRule.setContent {
            BrowserEditToolbar(
                query = "https://example.com",
                hint = "Search",
                useDesktopEditToolbar = true,
                onDismissDesktopSearch = { dismissed = true },
                onInteraction = {},
            )
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ADDRESSBAR_SEARCH_BOX).performClick()
        composeTestRule.onNodeWithTag(ADDRESSBAR_SEARCH_BOX).performKeyInput {
            pressKey(Key.Escape)
        }

        assertTrue(dismissed)
    }

    private fun setToolbar() {
        composeTestRule.setContent {
            Box(modifier = Modifier.testTag(TOOLBAR)) {
                BrowserEditToolbar(
                    query = "",
                    hint = "Search",
                    gravity = gravity,
                    showDivider = showDivider,
                    onInteraction = {},
                )
            }
        }
    }

    private fun toolbarBounds() = composeTestRule.onNodeWithTag(TOOLBAR).getUnclippedBoundsInRoot()

    private fun dividerBounds() =
        composeTestRule.onNodeWithTag(ADDRESSBAR_EDIT_MODE_HORIZONTAL_DIVIDER).getUnclippedBoundsInRoot()
}
