// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import com.swent.roamigo.ui.components.NotImplemented
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.day
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.place
import com.swent.roamigo.ui.trip.activity.ActivityFormFixtures.trip
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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

  private fun show(state: ActivityFormUiState, modifier: Modifier = Modifier) {
    val first = shown == null
    shown = state
    if (!first) return
    compose.setContent {
      val current = shown ?: return@setContent
      RoamigoTheme {
        ActivityFormScreen(
            state = current,
            // Typed text is kept, as the ViewModel does, so a field never reverts on focus loss.
            onNameChange = {
              events += "name:$it"
              shown = current.copy(fields = current.fields.copy(name = it))
            },
            onDateChange = { events += it },
            onTimeChange = { events += (it ?: "no time") },
            onPlaceClick = { events += "place" },
            onDescriptionChange = {
              events += "description:$it"
              shown = current.copy(fields = current.fields.copy(description = it))
            },
            onSave = { events += "save" },
            onClose = { events += "close" },
            onDelete = { events += "delete" },
            modifier = modifier,
        )
      }
    }
  }

  // Modes

  @Test
  fun createModeShowsEveryFieldAndNoDeleteAction() {
    show(createState)
    compose.onNodeWithText("New activity").assertIsDisplayed()
    compose.onNodeWithText("Lisbon → Porto · Tue, Oct 13").assertIsDisplayed()
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
  fun timeFollowsTheSystem24HourSettingLikeThePicker() {
    Settings.System.putString(compose.activity.contentResolver, Settings.System.TIME_12_24, "24")
    show(editState)
    compose.onNodeWithText("19:30").assertExists()
  }

  @Test
  fun pickersAnnounceTheirLabelAndValue() {
    show(editState)
    mapOf(
            ActivityFormTestTags.DATE to ("Date" to "Tue, Oct 13"),
            ActivityFormTestTags.PLACE to ("Place" to "Time Out Market"),
        )
        .forEach { (tag, announced) -> compose.onNodeWithTag(tag).assertAnnounces(announced) }
    // Locale data may put a narrow no-break space before "PM".
    val time = compose.onNodeWithTag(ActivityFormTestTags.TIME).fetchSemanticsNode().config
    assertEquals(listOf("Time"), time[SemanticsProperties.ContentDescription])
    assertTrue(time[SemanticsProperties.StateDescription].startsWith("7:30"))
    show(createState)
    compose.onNodeWithTag(ActivityFormTestTags.PLACE).assertAnnounces("Place" to "Choose a place")
  }

  @Test
  fun textFieldsAnnounceTheirLabelThenWhatWasTyped() {
    show(editState)
    mapOf(
            ActivityFormTestTags.NAME to ("Activity" to "Dinner at Time Out Market"),
            ActivityFormTestTags.DESCRIPTION to ("Description" to "Bring cash"),
        )
        .forEach { (tag, announced) ->
          val (label, typed) = announced
          compose.onNodeWithText(label).assertExists()
          compose
              .onNodeWithTag(tag)
              .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ContentDescription))
              .assert(hasText(typed))
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
    compose
        .onNodeWithTag(ActivityFormTestTags.DESCRIPTION)
        .performScrollTo()
        .performTextReplacement("Bring snacks")
    compose.onNodeWithTag(ActivityFormTestTags.PLACE).performScrollTo().performClick()
    compose.onNodeWithTag(ActivityFormTestTags.TIME_CLEAR).performScrollTo().performClick()
    compose.onNodeWithTag(ActivityFormTestTags.CLOSE).performScrollTo().performClick()
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).performClick()
    assertEquals(
        listOf<Any>("name:Dinner", "description:Bring snacks", "place", "no time", "close", "save"),
        events,
    )
  }

  @Test
  fun dateDialogReturnsTheDayChosenInThePicker() {
    show(createState)
    compose.onNodeWithTag(ActivityFormTestTags.DATE).performClick()
    compose.onNodeWithTag(ActivityFormTestTags.DATE_DIALOG).assertExists()
    compose.onNode(hasText("Tuesday, October 20, 2026") and hasClickAction()).performClick()
    compose.onNodeWithText("OK").performClick()
    compose.onNodeWithTag(ActivityFormTestTags.DATE_DIALOG).assertDoesNotExist()
    assertEquals(listOf<Any>(day.withDayOfMonth(20)), events)
  }

  @Test
  fun cancellingTheDateDialogChangesNothing() {
    show(createState)
    compose.onNodeWithTag(ActivityFormTestTags.DATE).performClick()
    compose.onNode(hasText("Tuesday, October 20, 2026") and hasClickAction()).performClick()
    compose.onNodeWithText("Cancel").performClick()
    compose.onNodeWithTag(ActivityFormTestTags.DATE_DIALOG).assertDoesNotExist()
    assertEquals(emptyList<Any>(), events)
  }

  @Test
  fun timeDialogReturnsTheHourChosenOnTheClock() {
    show(editState)
    compose.onNodeWithTag(ActivityFormTestTags.TIME).performClick()
    compose.onNodeWithTag(ActivityFormTestTags.TIME_DIALOG).assertExists()
    // The picker opens on 7:30 PM; choosing 9 on the clock keeps PM and the minutes.
    compose.onNodeWithContentDescription("9 o'clock").performClick()
    compose.onNodeWithText("OK").performClick()
    compose.onNodeWithTag(ActivityFormTestTags.TIME_DIALOG).assertDoesNotExist()
    assertEquals(listOf<Any>(LocalTime.of(21, 30)), events)
  }

  @Test
  fun timeDialogWithoutATimeStartsAtNoon() {
    show(createState)
    compose.onNodeWithTag(ActivityFormTestTags.TIME).performClick()
    compose.onNodeWithText("OK").performClick()
    assertEquals(listOf<Any>(LocalTime.NOON), events)
  }

  @Test
  fun pickerFieldsAreNoLongerPlaceholders() {
    show(createState)
    listOf(ActivityFormTestTags.DATE, ActivityFormTestTags.TIME).forEach {
      compose.onNodeWithTag(it).assert(SemanticsMatcher.keyNotDefined(NotImplemented))
    }
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
    listOf(
            ActivityFormTestTags.FAILURE,
            ActivityFormTestTags.DELETE,
            ActivityFormTestTags.DESCRIPTION,
            ActivityFormTestTags.NAME,
            ActivityFormTestTags.CLOSE,
        )
        .forEach {
          compose.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
          compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).assertIsDisplayed()
        }
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).performClick()
    assertEquals(listOf<Any>("save"), events)
  }

  @Test
  @Config(qualifiers = "en-rUS-w640dp-h360dp-land")
  fun fieldsStayUsableAboveTheKeyboardInLandscape() {
    // Robolectric shows no keyboard, so the screen gets the height the keyboard leaves free.
    show(
        editState.copy(
            trip = trip.copy(name = "A very long trip name ".repeat(LONG_NAME_REPEATS)),
            failure = ActivityFormFailure.NETWORK,
        ),
        Modifier.height(HEIGHT_ABOVE_KEYBOARD),
    )
    compose.onNodeWithTag(ActivityFormTestTags.FAILURE).assertIsDisplayed()
    compose.onNodeWithTag(ActivityFormTestTags.NAME).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(ActivityFormTestTags.SUBMIT).assertIsDisplayed()
  }

  private fun SemanticsNodeInteraction.assertAnnounces(announced: Pair<String, String>) =
      assert(
              SemanticsMatcher.expectValue(
                  SemanticsProperties.ContentDescription,
                  listOf(announced.first),
              )
          )
          .assert(
              SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, announced.second)
          )

  private companion object {
    /** Height left on a 360dp-high phone in landscape once the keyboard is open. */
    val HEIGHT_ABOVE_KEYBOARD = 160.dp

    /** Repeats that make the trip name longer than the subtitle can show. */
    const val LONG_NAME_REPEATS = 10
  }
}
