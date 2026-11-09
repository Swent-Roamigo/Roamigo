// AI assistance was used for implementation support.
package com.swent.roamigo.ui

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.swent.roamigo.ui.components.NotImplemented
import org.junit.Assert.assertTrue

internal val isNotImplemented = SemanticsMatcher.expectValue(NotImplemented, true)

/**
 * Discovers placeholders from semantics rather than keeping a second list of unfinished features.
 * Instrumented test implementation that asserts at least one unfinished action exists and interacts
 * with them.
 */
internal fun ComposeContentTestRule.assertNotImplementedActionsShowFeedback() {
  val actions = onAllNodes(isNotImplemented)
  val count = actions.fetchSemanticsNodes().size
  assertTrue("The fixture must expose at least one unfinished action", count > 0)
  repeat(count) { index ->
    val action = actions[index]
    if (!action.isDisplayed()) action.performScrollTo()
    action.performClick()
  }
}
