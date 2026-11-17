// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.swent.roamigo.model.place.FakePlaceRepository
import com.swent.roamigo.model.place.PlaceCandidate
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.ui.components.NotImplemented
import com.swent.roamigo.ui.placepicker.PlacePickerTestTags
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.TRIP_ID
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.day
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.storedActivity
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.trip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w390dp-h844dp")
class ActivityFormRouteTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val candidate = PlaceCandidate("market", "Time Out Market", "Lisbon", "Provider")
  private val places = FakePlaceRepository(search = { _, _ -> listOf(candidate) })
  private val activities = FakeActivityRepository()
  private var closed = 0
  private val saved = mutableListOf<String>()

  private fun show(mode: ActivityFormMode = ActivityFormMode.Create(TRIP_ID, day)) {
    compose.setContent {
      RoamigoTheme {
        ActivityFormRoute(
            activityRepository = activities,
            placeRepository = places,
            mode = mode,
            trip = trip,
            onClose = { closed++ },
            onActivitySaved = { saved += it },
        )
      }
    }
  }

  private fun pressBack() {
    compose.waitForIdle()
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
  }

  private fun pickPlace() {
    compose.onNodeWithTag(ActivityFormTestTags.PLACE).performClick()
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("time out")
    compose.waitUntil(5_000) {
      compose
          .onAllNodes(hasTestTag(PlacePickerTestTags.candidate(candidate.id)))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    compose.onNodeWithTag(PlacePickerTestTags.candidate(candidate.id)).performClick()
    compose.waitUntil(5_000) {
      compose
          .onAllNodes(hasTestTag(PlacePickerTestTags.SELECTED))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).performClick()
    compose.waitUntil(5_000) {
      compose.onAllNodes(hasTestTag(ActivityFormTestTags.SCREEN)).fetchSemanticsNodes().isNotEmpty()
    }
  }

  @Test
  fun closingAnUnchangedFormLeavesWithoutAsking() {
    show()
    compose.onNodeWithTag(ActivityFormTestTags.CLOSE).performClick()
    compose.runOnIdle { assertEquals(1, closed) }
    compose.onNodeWithText("Discard your changes?").assertDoesNotExist()
  }

  @Test
  fun closingAChangedFormAsksFirst() {
    show()
    compose.onNodeWithTag(ActivityFormTestTags.NAME).performTextInput("Dinner")
    compose.onNodeWithTag(ActivityFormTestTags.CLOSE).performClick()
    compose.onNodeWithText("Discard your changes?").assertIsDisplayed()
    compose.onNodeWithText("Keep editing").performClick()
    compose.onNodeWithText("Discard your changes?").assertDoesNotExist()
    compose.onNodeWithText("Dinner").assertExists()
    compose.runOnIdle { assertEquals(0, closed) }

    compose.onNodeWithTag(ActivityFormTestTags.CLOSE).performClick()
    compose.onNodeWithTag(ActivityFormTestTags.DISCARD_CONFIRM).performClick()
    compose.runOnIdle { assertEquals(1, closed) }
  }

  @Test
  fun backOnAChangedFormAsksFirst() {
    show()
    compose.onNodeWithTag(ActivityFormTestTags.NAME).performTextInput("Dinner")
    pressBack()
    compose.onNodeWithText("Discard your changes?").assertIsDisplayed()
    compose.runOnIdle { assertEquals(0, closed) }
  }

  @Test
  fun cancellingThePlacePickerKeepsTheForm() {
    show()
    compose.onNodeWithTag(ActivityFormTestTags.NAME).performTextInput("Dinner")
    compose.onNodeWithTag(ActivityFormTestTags.PLACE).performClick()
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).assertIsDisplayed()
    compose.onNodeWithTag(ActivityFormTestTags.SCREEN).assertDoesNotExist()
    compose.onNodeWithTag(PlacePickerTestTags.CANCEL).performClick()
    compose.onNodeWithTag(ActivityFormTestTags.SCREEN).assertIsDisplayed()
    compose.onNodeWithText("Dinner").assertExists()
    compose.onNodeWithText("Choose a place").assertExists()
    compose.runOnIdle { assertEquals(0, closed) }
  }

  @Test
  fun missingFieldsAreReportedAndNothingIsSaved() {
    show()
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).performClick()
    compose.onNodeWithText("Enter a name for the activity.").assertIsDisplayed()
    compose.onNodeWithText("Choose a place for the activity.").assertIsDisplayed()
    compose.runOnIdle {
      assertTrue(saved.isEmpty())
      assertTrue(runBlocking { activities.observeActivities(TRIP_ID).first() }.isEmpty())
    }
  }

  @Test
  fun filledFormIsSavedThroughTheRepositoryAndReportedOnce() {
    show()
    compose.onNodeWithTag(ActivityFormTestTags.NAME).performTextInput("Dinner")
    pickPlace()
    compose.onNodeWithText("Time Out Market").assertExists()
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).performClick()
    compose.waitUntil(5_000) { saved.isNotEmpty() }
    compose.runOnIdle {
      val stored = runBlocking { activities.observeActivities(TRIP_ID).first() }.single()
      assertEquals(listOf(stored.uid), saved)
      assertEquals("Dinner", stored.name)
      assertEquals("Time Out Market", stored.location.name)
    }
  }

  @Test
  fun editModeNeverPretendsToSaveOrDelete() {
    show(ActivityFormMode.Edit(TRIP_ID, storedActivity()))
    compose
        .onNodeWithTag(ActivityFormTestTags.DELETE)
        .assert(SemanticsMatcher.expectValue(NotImplemented, true))
        .performClick()
    compose.runOnIdle { assertEquals("Not implemented yet", ShadowToast.getTextOfLatestToast()) }
    ShadowToast.reset()

    compose.onNodeWithTag(ActivityFormTestTags.NAME).performTextInput("Late ")
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).performClick()
    compose.runOnIdle {
      assertEquals("Not implemented yet", ShadowToast.getTextOfLatestToast())
      assertTrue(saved.isEmpty())
      assertTrue(runBlocking { activities.observeActivities(TRIP_ID).first() }.isEmpty())
    }
  }
}
