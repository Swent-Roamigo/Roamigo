// AI assistance was used for implementation support.
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.friends.FakeFriendRepository
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.model.place.FakePlaceRepository
import com.swent.roamigo.model.place.PlaceCandidate
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.activity.FakeActivityRepository
import com.swent.roamigo.model.trip.voting.InMemoryVoteRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.authentication.AuthenticationScreenTestTags
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.placepicker.PlacePickerTestTags
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.activity.ActivityFormTestTags
import com.swent.roamigo.ui.trip.activity.ActivityFormTrip
import com.swent.roamigo.ui.trip.activity.pickNoon
import com.swent.roamigo.ui.trip.activity.pickPlace
import com.swent.roamigo.ui.trip.activity.waitForTag
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opening the activity form from a trip, through the app's real navigation graph. */
@RunWith(AndroidJUnit4::class)
class ActivityFormNavigationTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val tripId = "trip #1"
  // Characters that must survive being passed through the route.
  private val tripName = "Lisbon & Porto / 2026?"
  private val candidate = PlaceCandidate("market", "Time Out Market", "Lisbon")
  private val activities = FakeActivityRepository()
  private val auth =
      FakeAuthRepository(
          AuthenticatedUser("member", "Member", null, null),
          initiallySignedIn = true,
      )
  private lateinit var navController: NavHostController

  private fun openTrip(injectPlaces: Boolean = true) {
    val trip = navigationTestTrip(tripId, tripName)
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        TestRoamigoApp(
            voteRepository = InMemoryVoteRepository(),
            destinationRepository = sampleDestinationRepository(),
            tripRepository = FakeTripRepository(trips = listOf(trip)),
            activityRepository = activities,
            authRepository = auth,
            navController = navController,
            locationRepository = FakeLocationRepository(),
            friendRepository = FakeFriendRepository("member"),
            placeRepository =
                if (injectPlaces) FakePlaceRepository(search = { _, _ -> listOf(candidate) })
                else null,
        )
      }
    }
    composeRule.waitForTag(C.Tag.trips_main_trip_card)
    composeRule.onNodeWithTag(C.Tag.trips_main_trip_card).performClick()
    composeRule.waitForTag(C.Tag.trip_add_button)
  }

  private fun chooseAddAnActivity() {
    composeRule.onNodeWithTag(C.Tag.trip_add_button).performClick()
    composeRule.onNodeWithText("Add an activity").performClick()
  }

  private fun openActivityForm() {
    openTrip()
    chooseAddAnActivity()
    composeRule.waitForTag(ActivityFormTestTags.SCREEN)
  }

  private fun currentRoute() = navController.currentBackStackEntry?.destination?.route

  @Test
  fun addAnActivityOpensTheFormForThisTrip() {
    openActivityForm()

    composeRule.onNodeWithText(tripName, substring = true).assertIsDisplayed()
    composeRule.runOnIdle { assertEquals(Route.NEW_ACTIVITY, currentRoute()) }
  }

  @Test
  fun savedActivityIsStoredInTheTripAndTheTripIsShownAgain() {
    openActivityForm()
    composeRule.onNodeWithTag(ActivityFormTestTags.NAME).performTextInput("Dinner")
    composeRule.pickNoon()
    composeRule.pickPlace(candidate)
    composeRule.onNodeWithTag(ActivityFormTestTags.SUBMIT).performClick()

    composeRule.waitForTag(C.Tag.trip_add_button)
    composeRule.runOnIdle {
      assertEquals(Route.TRIP_DETAILS, currentRoute())
      val stored = runBlocking { activities.observeActivities(tripId).first() }
      assertEquals(listOf("Dinner"), stored.map { it.name })
      assertEquals(listOf("Time Out Market"), stored.map { it.location.name })
    }
  }

  @Test
  fun closingAnUntouchedFormReturnsToTheTripWithoutStoringAnything() {
    openActivityForm()
    composeRule.onNodeWithTag(ActivityFormTestTags.CLOSE).performClick()

    composeRule.waitForTag(C.Tag.trip_add_button)
    composeRule.runOnIdle {
      assertEquals(Route.TRIP_DETAILS, currentRoute())
      assertTrue(runBlocking { activities.observeActivities(tripId).first() }.isEmpty())
    }
  }

  @Test
  fun closingTwiceDuringTheExitOnlyLeavesTheForm() {
    openActivityForm()
    // Hold the exit animation so the second tap reaches the form that is already leaving.
    composeRule.mainClock.autoAdvance = false
    repeat(2) { composeRule.onNodeWithTag(ActivityFormTestTags.CLOSE).performClick() }
    composeRule.mainClock.autoAdvance = true

    composeRule.waitForTag(C.Tag.trip_add_button)
    composeRule.runOnIdle { assertEquals(Route.TRIP_DETAILS, currentRoute()) }
  }

  @Test
  fun withoutAnInjectedPlaceRepositoryThePlacePickerStillOpens() {
    openTrip(injectPlaces = false)
    chooseAddAnActivity()
    composeRule.waitForTag(ActivityFormTestTags.SCREEN)
    composeRule.onNodeWithTag(ActivityFormTestTags.PLACE).performClick()

    composeRule.onNodeWithTag(PlacePickerTestTags.QUERY).assertIsDisplayed()
  }

  @Test
  fun expiredSessionRedirectsOpeningTheFormToAuthentication() {
    openTrip()
    // The trip screen already redirects on its own actions, so this reaches the form's own check.
    composeRule.runOnIdle {
      auth.signOut()
      navController.navigate(
          Route.newActivity(tripId, ActivityFormTrip(tripName, LocalDate.now(), LocalDate.now()))
      )
    }

    composeRule.onNodeWithTag(AuthenticationScreenTestTags.heading).assertIsDisplayed()
    composeRule.runOnIdle { assertEquals(Route.AUTHENTICATION, currentRoute()) }
  }
}
