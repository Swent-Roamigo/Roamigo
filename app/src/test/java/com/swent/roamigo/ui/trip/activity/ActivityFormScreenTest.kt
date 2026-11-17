// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.swent.roamigo.ui.components.NotImplemented
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.day
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.place
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.trip
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w390dp-h844dp")
class ActivityFormScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val createState =
      ActivityFormUiState(isEdit = false, trip = trip, fields = ActivityFormFields(date = day))
  private val editState =
      ActivityFormUiState(
          isEdit = true,
          trip = trip,
          fields =
              ActivityFormFields(
                  name = "Dinner at Time Out Market",
                  date = day,
                  time = LocalTime.of(19, 30),
                  place = place,
                  description = "Bring cash",
              ),
      )

  private val events = mutableListOf<Any>()

  // setContent can only run once per test, so later calls only swap the state.
  private var shown by mutableStateOf<ActivityFormUiState?>(null)

  private fun show(state: ActivityFormUiState) {
    val first = shown == null
    shown = state
    if (!first) return
    compose.setContent {
      val current = shown ?: return@setContent
      RoamigoTheme {
        ActivityFormScreen(
            state = current,
            onNameChange = { events += "name:$it" },
            onTimeChange = { events += (it ?: "no time") },
            onPlaceClick = { events += "place" },
            onDescriptionChange = { events += "description:$it" },
            onSave = { events += "save" },
            onClose = { events += "close" },
            onDelete = { events += "delete" },
        )
      }
    }
  }

  // Modes

  @Test
  fun createModeShowsEveryFieldAndNoDeleteAction() {
    show(createState)
    compose.onNodeWithText("New activity").assertIsDisplayed()
    compose.onNode(hasText("Lisbon → Porto · ", substring = true)).assertIsDisplayed()
    listOf(
            ActivityFormTestTags.NAME,
            ActivityFormTestTags.DATE,
            ActivityFormTestTags.TIME,
            ActivityFormTestTags.PLACE,
            ActivityFormTestTags.DESCRIPTION,
        )
        .forEach { compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed() }
    compose.onNodeWithText("Add a time (optional)").assertExists()
    compose.onNodeWithText("Choose a place").assertExists()
    compose.onNodeWithTag(ActivityFormTestTags.TIME_CLEAR).assertDoesNotExist()
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).assertTextContains("Add activity")
    compose.onNodeWithTag(ActivityFormTestTags.DELETE).assertDoesNotExist()
    compose.onNodeWithText("Delete activity").assertDoesNotExist()
  }

  @Test
  fun editModeShowsPrefilledValuesSaveChangesAndDelete() {
    show(editState)
    compose.onNodeWithText("Edit activity").assertIsDisplayed()
    compose.onNodeWithText("Dinner at Time Out Market").assertExists()
    // Locale data may put a narrow no-break space before "PM".
    compose.onNodeWithText("7:30", substring = true).assertExists()
    compose.onNodeWithText("Time Out Market").assertExists()
    compose.onNodeWithText("Bring cash").assertExists()
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).assertTextContains("Save changes")
    compose.onNodeWithTag(ActivityFormTestTags.DELETE).assertIsDisplayed().performClick()
    assertEquals(listOf<Any>("delete"), events)
  }

  @Test
  fun fieldsAreLabelledForAccessibility() {
    show(createState)
    listOf("Activity", "Date", "Time", "Place", "Description").forEach {
      compose.onNodeWithContentDescription(it).assertExists()
    }
    compose.onNodeWithContentDescription("Close").assertExists()
  }

  // Saving and messages

  @Test
  fun buttonIsDisabledWhileSaving() {
    show(createState.copy(fields = editState.fields, saveStatus = ActivityFormSaveStatus.SAVING))
    compose
        .onNodeWithTag(ActivityFormTestTags.SUBMIT)
        .assertIsNotEnabled()
        .assertTextContains("Adding activity…")
    compose.onNodeWithTag(ActivityFormTestTags.CLOSE).assertIsNotEnabled()
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).performClick()
    assertEquals(emptyList<Any>(), events)
  }

  @Test
  fun buttonIsDisabledOnceSaved() {
    show(createState.copy(saveStatus = ActivityFormSaveStatus.SAVED))
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).assertIsNotEnabled()
  }

  @Test
  fun fieldErrorsAreShown() {
    show(
        createState.copy(
            errors =
                listOf(
                    ActivityFormError.BLANK_NAME,
                    ActivityFormError.END_BEFORE_START,
                    ActivityFormError.MISSING_PLACE,
                    ActivityFormError.DESCRIPTION_TOO_LONG,
                )
        )
    )
    listOf(
            "Enter a name for the activity.",
            "This activity would end before it starts. Choose an earlier time.",
            "Choose a place for the activity.",
            "This description is too long. Shorten it.",
        )
        .forEach { compose.onNodeWithText(it).performScrollTo().assertIsDisplayed() }
  }

  @Test
  fun saveFailureIsShownAndTheButtonStaysAvailableForARetry() {
    show(createState.copy(failure = ActivityFormFailure.NETWORK))
    compose
        .onNodeWithTag(ActivityFormTestTags.FAILURE)
        .assertIsDisplayed()
        .assertTextContains("Could not save the activity", substring = true)
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).assertIsEnabled().performClick()
    assertEquals(listOf<Any>("save"), events)
  }

  @Test
  fun dateOutsideTheTripShowsAWarningOnlyThen() {
    show(createState)
    compose.onNodeWithTag(ActivityFormTestTags.OUTSIDE_TRIP).assertDoesNotExist()
    show(createState.copy(fields = ActivityFormFields(date = trip.endDate.plusDays(2))))
    compose
        .onNodeWithTag(ActivityFormTestTags.OUTSIDE_TRIP)
        .assertIsDisplayed()
        .assertTextContains("You can still save it.", substring = true)
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).assertIsEnabled()
  }

  // Callbacks

  @Test
  fun changesAndActionsAreForwarded() {
    show(editState.copy(isEdit = false, fields = editState.fields.copy(name = "")))
    compose.onNodeWithTag(ActivityFormTestTags.NAME).performTextInput("Dinner")
    compose.onNodeWithTag(ActivityFormTestTags.PLACE).performClick()
    compose.onNodeWithTag(ActivityFormTestTags.TIME_CLEAR).performClick()
    compose.onNodeWithTag(ActivityFormTestTags.CLOSE).performClick()
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).performClick()
    assertEquals(listOf<Any>("name:Dinner", "place", "no time", "close", "save"), events)
  }

  @Test
  fun dateAndTimeFieldsGiveTheUnfinishedActionFeedbackUntilTheirPickersExist() {
    show(createState)
    listOf(ActivityFormTestTags.DATE, ActivityFormTestTags.TIME).forEach { tag ->
      ShadowToast.reset()
      compose
          .onNodeWithTag(tag)
          .assert(SemanticsMatcher.expectValue(NotImplemented, true))
          .performClick()
      compose.runOnIdle { assertEquals("Not implemented yet", ShadowToast.getTextOfLatestToast()) }
    }
    compose
        .onNodeWithTag(ActivityFormTestTags.PLACE)
        .assert(SemanticsMatcher.keyNotDefined(NotImplemented))
    assertEquals(emptyList<Any>(), events)
  }

  @Test
  fun disabledFormIgnoresTaps() {
    show(editState.copy(saveStatus = ActivityFormSaveStatus.SAVING))
    compose.onNodeWithTag(ActivityFormTestTags.PLACE).performClick()
    compose.onNodeWithTag(ActivityFormTestTags.DELETE).performClick()
    compose.onNodeWithTag(ActivityFormTestTags.TIME_CLEAR).performClick()
    assertEquals(emptyList<Any>(), events)
    show(editState)
    compose.onNodeWithTag(ActivityFormTestTags.PLACE).performClick()
    assertEquals(listOf<Any>("place"), events)
  }

  // Small screens and landscape

  @Test
  @Config(qualifiers = "en-rUS-w320dp-h480dp")
  fun primaryActionIsReachableOnASmallScreen() {
    assertActionsReachable()
  }

  @Test
  @Config(qualifiers = "en-rUS-w320dp-h480dp", fontScale = 1.5f)
  fun primaryActionIsReachableOnASmallScreenWithLargeText() {
    assertActionsReachable()
  }

  @Test
  @Config(qualifiers = "en-rUS-w640dp-h360dp-land")
  fun primaryActionIsReachableInLandscape() {
    assertActionsReachable()
  }

  private fun assertActionsReachable() {
    show(
        editState.copy(
            errors = listOf(ActivityFormError.BLANK_NAME),
            failure = ActivityFormFailure.NETWORK,
        )
    )
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).assertIsDisplayed()
    compose.onNodeWithTag(ActivityFormTestTags.DELETE).assertIsDisplayed()
    compose.onNodeWithTag(ActivityFormTestTags.CLOSE).assertIsDisplayed()
    compose.onNodeWithTag(ActivityFormTestTags.DESCRIPTION).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(ActivityFormTestTags.NAME).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).assertIsDisplayed().performClick()
    assertEquals(listOf<Any>("save"), events)
  }
}
