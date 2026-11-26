// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.itinerary

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en-rUS-w390dp-h844dp")
class ItineraryScreenTest {
  @get:Rule val composeRule = createComposeRule()

  private val sat = LocalDate.of(2026, 10, 10)
  private val sun = sat.plusDays(1)
  private val belem = ItineraryActivity("belem", "Belém Tower", "Belém", LocalTime.of(9, 30))
  private val lx = ItineraryActivity("lx", "LX Factory", "Alcântara", LocalTime.of(14, 0))

  private fun ready(
      activities: List<ItineraryActivity> = listOf(belem, lx),
      hasActivities: Boolean = true,
      refreshFailed: Boolean = false,
      waitingSuggestions: Int = 0,
  ) =
      ItineraryUiState.Ready(
          listOf(sat, sun),
          sun,
          activities,
          hasActivities,
          refreshFailed,
          waitingSuggestions,
      )

  private fun show(
      state: ItineraryUiState,
      onSelectDay: (LocalDate) -> Unit = {},
      onRetry: () -> Unit = {},
      onAddActivity: (() -> Unit)? = {},
      onOpenVotes: () -> Unit = {},
  ) {
    composeRule.setContent {
      RoamigoTheme { ItineraryScreen(state, onSelectDay, onRetry, onAddActivity, onOpenVotes) }
    }
  }

  @Test
  fun showsDayChipsAndTheSelectedDaysActivitiesInOrder() {
    val selected = mutableListOf<LocalDate>()
    show(ready(), onSelectDay = { selected += it })

    composeRule.onNodeWithTag(C.Tag.itineraryDay(sun)).assertIsSelected()
    composeRule.onNodeWithTag(C.Tag.itineraryDay(sat)).assertIsNotSelected().performClick()
    composeRule.onNodeWithText("Sat 10").assertIsDisplayed()
    composeRule.onNodeWithText("Belém Tower").assertIsDisplayed()
    composeRule.onNodeWithText("Alcântara").assertIsDisplayed()
    // Some JDKs put a narrow no-break space before AM.
    composeRule.onNodeWithText("9:30", substring = true).assertIsDisplayed()
    composeRule.runOnIdle { assertEquals(listOf(sat), selected) }
  }

  @Test
  fun waitingSuggestionsOpenTheVotes() {
    var opened = 0
    show(ready(waitingSuggestions = 3), onOpenVotes = { opened++ })

    composeRule.onNodeWithText("3 suggestions waiting for votes").assertIsDisplayed().performClick()
    composeRule.runOnIdle { assertEquals(1, opened) }
  }

  @Test
  fun oneWaitingSuggestionUsesTheSingular() {
    show(ready(emptyList(), hasActivities = false, waitingSuggestions = 1))

    composeRule.onNodeWithText("1 suggestion waiting for votes").assertIsDisplayed()
  }

  @Test
  fun noWaitingSuggestionShowsNoSuggestionRow() {
    show(ready())

    composeRule.onNodeWithTag(C.Tag.itinerary_waiting_suggestions).assertDoesNotExist()
  }

  @Test
  fun aLaterSelectedDayKeepsThePreviousDayFullyInView() {
    // Enough days after the selection that the row can scroll it to the start.
    val days = (0L until 20L).map { sat.plusDays(it) }
    val selected = days[10]
    show(ItineraryUiState.Ready(days, selected, emptyList(), hasActivities = true))

    composeRule.onNodeWithTag(C.Tag.itineraryDay(selected)).assertIsSelected().assertIsDisplayed()
    val previous = composeRule.onNodeWithTag(C.Tag.itineraryDay(days[9])).getUnclippedBoundsInRoot()
    assertTrue("previous day is cut off at ${previous.left}", previous.left >= 0.dp)
  }

  @Test
  fun activityNamesLineUpWhateverTheWidthOfTheirTime() {
    val morning = ItineraryActivity("m", "Belém Tower", "Belém", LocalTime.of(9, 30))
    val noon =
        ItineraryActivity("n", "Lunch at Time Out Market", "Cais do Sodré", LocalTime.of(12, 30))
    show(ready(listOf(morning, noon)))

    // Each row merges its texts for TalkBack, so the names are read from the unmerged tree.

    val morningName =
        composeRule.onNodeWithText("Belém Tower", useUnmergedTree = true).getUnclippedBoundsInRoot()
    val noonName =
        composeRule
            .onNodeWithText("Lunch at Time Out Market", useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
    assertEquals(noonName.left, morningName.left)
  }

  @Test
  fun dayChipsAnnounceTheFullDate() {
    show(ready())

    composeRule.onNodeWithContentDescription("Sunday, October 11, 2026").assertIsDisplayed()
  }

  @Test
  fun anEmptyTripInvitesToAddTheFirstActivity() {
    var adds = 0
    show(ready(emptyList(), hasActivities = false), onAddActivity = { adds++ })

    composeRule
        .onNodeWithText("Nothing planned yet. Add the first activity for the group.")
        .assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.itinerary_add_activity).performClick()
    composeRule.runOnIdle { assertEquals(1, adds) }
  }

  @Test
  fun anEmptyDayOfAPlannedTripSaysSo() {
    show(ready(emptyList(), hasActivities = true))

    composeRule.onNodeWithText("Nothing planned this day.").assertIsDisplayed()
  }

  @Test
  fun startTimesFollowTheSystem24HourSetting() {
    Settings.System.putString(
        ApplicationProvider.getApplicationContext<Context>().contentResolver,
        Settings.System.TIME_12_24,
        "24",
    )
    show(ready())

    composeRule.onNodeWithText("14:00").assertIsDisplayed()
    composeRule.onNodeWithText("PM", substring = true).assertDoesNotExist()
  }

  @Test
  fun aTripThatCannotBeFoundSaysSoWithoutARetry() {
    show(ItineraryUiState.TripNotFound)

    composeRule
        .onNodeWithText("We couldn't open this trip. It may be private or no longer available.")
        .assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.itinerary_retry).assertDoesNotExist()
  }

  @Test
  fun aReadOnlyTripOffersNoAddAction() {
    show(ready(emptyList(), hasActivities = false), onAddActivity = null)

    composeRule.onNodeWithTag(C.Tag.itinerary_add_activity).assertDoesNotExist()
  }

  @Test
  fun loadingThenErrorThenRetry() {
    val state = mutableStateOf<ItineraryUiState>(ItineraryUiState.Loading)
    var retries = 0
    composeRule.setContent {
      RoamigoTheme { ItineraryScreen(state.value, {}, { retries++ }, null, {}) }
    }
    composeRule.onNodeWithText("Loading the plan…").assertIsDisplayed()

    composeRule.runOnIdle { state.value = ItineraryUiState.Error }
    composeRule
        .onNodeWithText("We couldn't load the plan. Check your connection and try again.")
        .assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.itinerary_retry).performClick()
    composeRule.runOnIdle { assertEquals(1, retries) }
  }

  @Test
  fun aFailedRefreshKeepsThePlanAndOffersRetry() {
    var retries = 0
    show(ready(refreshFailed = true), onRetry = { retries++ })

    composeRule.onNodeWithText("The plan may be out of date.").assertIsDisplayed()
    composeRule.onNodeWithText("Belém Tower").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.itinerary_retry).performClick()
    composeRule.runOnIdle { assertEquals(1, retries) }
  }

  @Test
  @Config(qualifiers = "en-rUS-w640dp-h320dp")
  fun everyRowIsReachableInLandscape() {
    val many =
        (0 until 8).map { ItineraryActivity("a$it", "Stop $it", "Lisbon", LocalTime.of(8 + it, 0)) }
    show(ready(many))

    composeRule
        .onNodeWithTag(C.Tag.itinerary_list)
        .performScrollToNode(hasTestTag(C.Tag.itineraryActivity("a7")))
    composeRule.onNodeWithText("Stop 7").assertIsDisplayed()
  }
}
