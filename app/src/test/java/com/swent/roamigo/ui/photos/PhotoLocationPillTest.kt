// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoLocationPillTest {
  @get:Rule val composeRule = createComposeRule()

  private val status = mutableStateOf<PhotoLocationStatus>(PhotoLocationStatus.Locating)
  private var openMapCalls = 0

  private fun setPill() {
    composeRule.setContent {
      RoamigoTheme { PhotoLocationPill(status.value, onOpenMap = { openMapCalls++ }) }
    }
  }

  private fun pill() = composeRule.onNodeWithTag(C.Tag.photo_preview_location)

  @Test
  fun showsAProgressIndicatorWhileLocating() {
    status.value = PhotoLocationStatus.Locating
    setPill()

    pill().assertTextEquals("Getting location…")
    composeRule
        .onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate), useUnmergedTree = true)
        .assertExists()
  }

  @Test
  fun saysWhenTheLocationIsAttached() {
    status.value = PhotoLocationStatus.Attached(46.52, 6.63)
    setPill()

    pill().assertTextEquals("Location attached")
  }

  @Test
  fun saysWhenTheLocationIsUnavailable() {
    status.value = PhotoLocationStatus.Unavailable
    setPill()

    pill().assertTextEquals("Location unavailable")
  }

  @Test
  fun offPillIsAButtonThatOpensTheMap() {
    status.value = PhotoLocationStatus.Off
    setPill()

    pill().assertTextEquals("Location off · Turn on in Map")
    pill().assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    pill().assertHasClickAction()
    pill().assertHeightIsAtLeast(48.dp)
    pill().performClick()

    assertEquals(1, openMapCalls)
  }

  @Test
  fun onlyTheOffPillCanBeTapped() {
    setPill()

    listOf(
            PhotoLocationStatus.Locating,
            PhotoLocationStatus.Attached(46.52, 6.63),
            PhotoLocationStatus.Unavailable,
        )
        .forEach {
          status.value = it
          composeRule.waitForIdle()
          pill().assertHasNoClickAction()
          pill().performClick()
        }

    assertEquals(0, openMapCalls)
  }
}
