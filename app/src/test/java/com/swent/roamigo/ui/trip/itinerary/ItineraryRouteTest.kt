// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.itinerary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ItineraryRouteTest {
  @get:Rule val composeRule = createComposeRule()

  // The trip header loads separately from the activities, so the plan can render before its dates.
  @Test
  fun thePlanOffersEveryTripDayOnceTheTripDatesArrive() {
    val start = LocalDate.of(2030, 7, 10)
    val end = start.plusDays(2)
    var dates by mutableStateOf<Pair<LocalDate, LocalDate>?>(null)
    composeRule.setContent {
      RoamigoTheme {
        ItineraryRoute(
            tripId = "trip-id",
            activityRepository = FakeActivityRepository(),
            voteRepository = InMemoryVoteRepository(),
            tripStartDate = dates?.first,
            tripEndDate = dates?.second,
            onAddActivity = {},
            onOpenVotes = {},
        )
      }
    }

    composeRule.onNodeWithText(EMPTY_TRIP).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.itineraryDay(start)).assertDoesNotExist()

    dates = start to end

    composeRule.onNodeWithTag(C.Tag.itineraryDay(start)).assertIsSelected()
    composeRule.onNodeWithTag(C.Tag.itineraryDay(start.plusDays(1))).assertExists()
    composeRule.onNodeWithTag(C.Tag.itineraryDay(end)).assertExists()
    composeRule.onNodeWithText(EMPTY_TRIP).assertIsDisplayed()
  }

  @Test
  fun retryingThePlanAloneLeavesTheTripLoaded() {
    var tripRetries = 0
    showRoute(
        FakeActivityRepository(failure = IllegalStateException("offline")),
        onRetryTrip = { tripRetries++ },
    )

    composeRule.onNodeWithTag(C.Tag.itinerary_retry).performClick()

    composeRule.runOnIdle { assertEquals(0, tripRetries) }
  }

  @Test
  fun retryingAFailedTripReloadsIt() {
    var tripRetries = 0
    showRoute(tripError = TripLoadError.FAILED, onRetryTrip = { tripRetries++ })

    composeRule.onNodeWithTag(C.Tag.itinerary_retry).performClick()

    composeRule.runOnIdle { assertEquals(1, tripRetries) }
  }

  @Test
  fun aTripThatCannotBeFoundOffersNoRetry() {
    showRoute(tripError = TripLoadError.NOT_FOUND)

    composeRule.onNodeWithText(TRIP_NOT_FOUND).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.itinerary_retry).assertDoesNotExist()
  }

  private fun showRoute(
      activityRepository: FakeActivityRepository = FakeActivityRepository(),
      tripError: TripLoadError? = null,
      onRetryTrip: () -> Unit = {},
  ) {
    val start = LocalDate.of(2030, 7, 10)
    composeRule.setContent {
      RoamigoTheme {
        ItineraryRoute(
            tripId = "trip-id",
            activityRepository = activityRepository,
            voteRepository = InMemoryVoteRepository(),
            tripStartDate = start,
            tripEndDate = start.plusDays(2),
            onAddActivity = {},
            onOpenVotes = {},
            tripError = tripError,
            onRetryTrip = onRetryTrip,
        )
      }
    }
  }

  private companion object {
    const val TRIP_NOT_FOUND =
        "We couldn't open this trip. It may be private or no longer available."
    const val EMPTY_TRIP = "Nothing planned yet. Add the first activity for the group."
  }
}
