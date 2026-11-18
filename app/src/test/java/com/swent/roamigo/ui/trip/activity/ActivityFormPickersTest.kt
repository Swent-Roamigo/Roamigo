// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextReplacement
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.day
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w390dp-h844dp")
class ActivityFormPickersTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val picked = mutableListOf<Any>()
  private var dismissed = 0
  private val host = Modifier.semantics { contentDescription = HOST }

  private fun showDateDialog() {
    compose.setContent {
      RoamigoTheme { ActivityDateDialog(day, { picked += it }, { dismissed++ }, modifier = host) }
    }
  }

  private fun showTimeDialog() {
    compose.setContent {
      RoamigoTheme {
        ActivityTimeDialog(LocalTime.of(19, 30), { picked += it }, { dismissed++ }, host)
      }
    }
  }

  @Test
  fun dateDialogAppliesTheCallerModifier() {
    showDateDialog()
    compose.onNodeWithContentDescription(HOST).assertExists()
  }

  @Test
  fun timeDialogAppliesTheCallerModifier() {
    showTimeDialog()
    compose.onNodeWithContentDescription(HOST).assertExists()
  }

  @Test
  fun dateDialogWithoutAValidDateOnlyCloses() {
    showDateDialog()
    compose.onNodeWithContentDescription("Switch to text input mode").performClick()
    compose.onNode(hasSetTextAction()).performTextClearance()
    compose.onNodeWithText("OK").performClick()
    compose.runOnIdle {
      assertEquals(emptyList<LocalDate>(), picked)
      assertEquals(1, dismissed)
    }
  }

  @Test
  @Config(qualifiers = "en-rUS-w640dp-h360dp-land")
  fun dateDialogCanBeConfirmedInLandscape() {
    showDateDialog()
    compose.onNodeWithText("OK").assertIsDisplayed().performClick()
    compose.runOnIdle { assertEquals(listOf<Any>(day), picked) }
  }

  @Test
  @Config(qualifiers = "en-rUS-w640dp-h360dp-land")
  fun timeDialogCanBeConfirmedInLandscape() {
    showTimeDialog()
    compose.onNodeWithText("OK").assertIsDisplayed().performClick()
    compose.runOnIdle { assertEquals(listOf<Any>(LocalTime.of(19, 30)), picked) }
  }

  @Test
  fun timeCanBeTypedAfterSwitchingToTheKeyboard() {
    showTimeDialog()
    compose.onNodeWithContentDescription("Type the time").performClick()
    compose.onNodeWithContentDescription("Use the clock").assertIsDisplayed()
    compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("8")
    compose.onNodeWithText("OK").performClick()
    // The dialog opened at 7:30 PM in 12-hour mode, so the typed hour stays in the evening.
    compose.runOnIdle { assertEquals(listOf<Any>(LocalTime.of(20, 30)), picked) }
  }

  private companion object {
    const val HOST = "picker host"
  }
}
