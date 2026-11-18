// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.swent.roamigo.model.place.PlaceCandidate
import com.swent.roamigo.ui.placepicker.PlacePickerTestTags

private const val UI_TIMEOUT_MS = 5_000L

/** Waits until a node tagged [tag] is shown. */
internal fun ComposeTestRule.waitForTag(tag: String) =
    waitUntil(UI_TIMEOUT_MS) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

/**
 * Picks [candidate] through the place picker opened from the activity form, then waits for the form
 * to come back. The place repository under test must return [candidate] for any query.
 */
internal fun ComposeTestRule.pickPlace(candidate: PlaceCandidate) {
  onNodeWithTag(ActivityFormTestTags.PLACE).performClick()
  onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("time out")
  waitForTag(PlacePickerTestTags.candidate(candidate.id))
  onNodeWithTag(PlacePickerTestTags.candidate(candidate.id)).performClick()
  waitForTag(PlacePickerTestTags.SELECTED)
  onNodeWithTag(PlacePickerTestTags.CONFIRM).performClick()
  waitForTag(ActivityFormTestTags.SCREEN)
}
