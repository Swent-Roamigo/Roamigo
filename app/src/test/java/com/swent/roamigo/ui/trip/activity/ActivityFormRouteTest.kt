// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.swent.roamigo.model.place.FakePlaceRepository
import com.swent.roamigo.model.place.PlaceCandidate
import com.swent.roamigo.model.place.PlaceProximity
import com.swent.roamigo.model.place.PlaceSearchArea
import com.swent.roamigo.model.trip.Activity
import com.swent.roamigo.model.trip.activity.ActivityDraft
import com.swent.roamigo.model.trip.activity.ActivityRepository
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.ui.components.NotImplemented
import com.swent.roamigo.ui.placepicker.PlacePickerTestTags
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.TRIP_ID
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.day
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.place
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.storedActivity
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.trip
import kotlinx.coroutines.CompletableDeferred
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

  private fun show(
      mode: ActivityFormMode = ActivityFormMode.Create(TRIP_ID, day),
      modifier: Modifier = Modifier,
      viewModelFactory: ViewModelProvider.Factory? = null,
      placeSearchArea: PlaceSearchArea? = null,
  ) {
    compose.setContent {
      RoamigoTheme {
        ActivityFormRoute(
            activityRepository = activities,
            placeRepository = places,
            mode = mode,
            trip = trip,
            onClose = { closed++ },
            onActivitySaved = { saved += it },
            modifier = modifier,
            placeSearchArea = placeSearchArea,
            viewModelFactory = viewModelFactory,
        )
      }
    }
  }

  private fun pressBack() {
    compose.waitForIdle()
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
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
  fun thePlaceSearchIsLimitedToTheAreaOfTheTrip() {
    val london = PlaceSearchArea(PlaceProximity(51.5, -0.12), setOf("GB"))
    show(placeSearchArea = london)

    compose.pickPlace(candidate)

    compose.runOnIdle {
      assertEquals(london.proximity, places.searchRequests.last().second)
      assertEquals(setOf("GB"), places.searchCountries.last())
    }
  }

  @Test
  fun withoutAnAreaThePlaceSearchHasNoLocationAndNoCountry() {
    show()

    compose.pickPlace(candidate)

    compose.runOnIdle {
      assertEquals(null, places.searchRequests.last().second)
      assertEquals(emptySet<String>(), places.searchCountries.last())
    }
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
    compose.pickPlace(candidate)
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

  @Test
  fun leavingIsIgnoredWhileSavingAndAllowedWithoutAskingOnceSaved() {
    val gate = CompletableDeferred<Unit>()
    val gated =
        object : ActivityRepository by activities {
          override suspend fun createActivity(
              tripId: String,
              activityId: String,
              draft: ActivityDraft,
          ): Activity {
            gate.await()
            return activities.createActivity(tripId, activityId, draft)
          }
        }
    // A form filled through the injected factory, so the test starts from a valid draft.
    val form =
        ActivityFormViewModel(
            gated,
            ActivityFormMode.Create(TRIP_ID, day),
            trip,
            SavedStateHandle(),
        )
    form.updateName("Dinner")
    form.updatePlace(place)
    show(viewModelFactory = viewModelFactory { initializer { form } })

    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).performClick()
    compose.waitUntil(5_000) { form.uiState.value.isSaving }
    pressBack()
    compose.onNodeWithText("Discard your changes?").assertDoesNotExist()
    compose.runOnIdle { assertEquals(0, closed) }

    gate.complete(Unit)
    compose.waitUntil(5_000) { saved.isNotEmpty() }
    assertTrue(form.uiState.value.isDirty)
    pressBack()
    compose.onNodeWithText("Discard your changes?").assertDoesNotExist()
    compose.runOnIdle { assertEquals(1, closed) }
  }

  @Test
  fun callerModifierReachesTheFormAndThePlacePicker() {
    show(modifier = Modifier.semantics { contentDescription = HOST })
    compose.onNodeWithContentDescription(HOST).assertExists()
    compose.onNodeWithTag(ActivityFormTestTags.PLACE).performClick()
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).assertIsDisplayed()
    compose.onNodeWithContentDescription(HOST).assertExists()
  }

  @Test
  fun draftSurvivesWhenTheParentRecomposesWithTheSameArguments() {
    var parentTick by mutableIntStateOf(0)
    compose.setContent {
      RoamigoTheme {
        // Reading the tick makes the parent recompose and call the route again.
        Text("tick $parentTick")
        ActivityFormRoute(
            activityRepository = activities,
            placeRepository = places,
            // New but equal objects, as a parent rebuilding its arguments would pass.
            mode = ActivityFormMode.Create(TRIP_ID, day),
            trip = trip.copy(),
            onClose = { closed++ },
            onActivitySaved = { saved += it },
        )
      }
    }
    compose.onNodeWithTag(ActivityFormTestTags.NAME).performTextInput("Dinner")
    repeat(2) {
      compose.runOnIdle { parentTick++ }
      compose.onNodeWithText("tick ${it + 1}").assertExists()
    }
    compose.onNodeWithText("Dinner").assertExists()
  }

  private companion object {
    const val HOST = "activity form host"
  }
}
