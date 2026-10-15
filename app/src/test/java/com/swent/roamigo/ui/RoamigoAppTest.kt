// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.DatesTravelersScreenTestTags
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffoldTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationCardTestTags
import com.swent.roamigo.ui.trip.creation.components.TripTypeSelectorTestTags
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that the screens are connected as intended and that Back returns to the right one. */
@RunWith(AndroidJUnit4::class)
class RoamigoAppTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
  private val repository = FakeTripRepository(currentUserId = "alex")

  @Before
  fun setUp() {
    val authRepository =
        FakeAuthRepository(AuthenticatedUser("alex", "Alex", null, null), initiallySignedIn = true)
    composeRule.setContent {
      RoamigoTheme { RoamigoApp(authRepository = authRepository, tripRepository = repository) }
    }
  }

  private fun waitForTag(tag: String) {
    composeRule.waitUntil { composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
  }

  private fun waitForTrips() {
    waitForTag(C.Tag.trips_screen)
  }

  private fun pressBack() {
    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
  }

  @Test
  fun existingSessionStartsOnTrips() {
    waitForTrips()

    composeRule.onAllNodesWithTag(C.Tag.permissions_title).assertCountEquals(0)
  }

  @Test
  fun avatarOpensProfileAndBackReturnsToTrips() {
    waitForTrips()
    waitForTag(C.Tag.trips_profile_avatar)

    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    composeRule.onNodeWithTag(C.Tag.profile_title).assertIsDisplayed()

    pressBack()
    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
  }

  @Test
  fun tripsTabOnTheProfileReturnsToTrips() {
    waitForTrips()
    waitForTag(C.Tag.trips_profile_avatar)
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()

    composeRule.onNodeWithTag(C.Tag.trips_navigation_trips).performClick()

    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
    composeRule.onAllNodesWithTag(C.Tag.profile_title).assertCountEquals(0)
  }

  @Test
  fun newTripOpensDestinationStepAndBackReturnsToTrips() {
    waitForTrips()

    composeRule.onNodeWithTag(C.Tag.trips_new_trip_button).performClick()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsDisplayed()

    pressBack()
    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
  }

  @Test
  fun backButtonOnTheDestinationStepReturnsToTrips() {
    waitForTrips()
    composeRule.onNodeWithTag(C.Tag.trips_new_trip_button).performClick()

    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.BACK).performClick()

    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
    composeRule.onAllNodesWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertCountEquals(0)
  }

  @Test
  fun closeButtonOnTheDestinationStepReturnsToTrips() {
    waitForTrips()
    composeRule.onNodeWithTag(C.Tag.trips_new_trip_button).performClick()

    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.CLOSE).performClick()

    composeRule.onNodeWithTag(C.Tag.trips_screen).assertIsDisplayed()
    composeRule.onAllNodesWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertCountEquals(0)
  }

  @Test
  fun datesBackPreservesDestinationsAndOptionsAndCloseDiscardsDraft() {
    waitForTrips()
    composeRule.onNodeWithTag(C.Tag.trips_new_trip_button).performClick()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsNotEnabled()
    composeRule.onNodeWithTag(DestinationCardTestTags.forDestination("Lisbon")).performClick()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()
    composeRule.onNodeWithTag(DatesTravelersScreenTestTags.SCREEN).assertIsDisplayed()
    composeRule.onNodeWithTag(TripTypeSelectorTestTags.SOLO).performScrollTo().performClick()

    pressBack()
    composeRule.onNodeWithTag(DestinationCardTestTags.forDestination("Lisbon")).assertIsSelected()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()
    composeRule.onNodeWithTag(TripTypeSelectorTestTags.SOLO).performScrollTo().assertIsSelected()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.CLOSE).performClick()
    waitForTrips()

    composeRule.onNodeWithTag(C.Tag.trips_new_trip_button).performClick()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsNotEnabled()
  }

  @Test
  fun creationReturnsToRefreshedTripsAndNextDraftStartsEmpty() {
    waitForTrips()
    composeRule.onNodeWithTag(C.Tag.trips_new_trip_button).performClick()
    composeRule.onNodeWithTag(DestinationCardTestTags.forDestination("Lisbon")).performClick()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()
    composeRule
        .onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION)
        .assertIsEnabled()
        .performClick()
    waitForTag(C.Tag.trips_main_trip_card)
    composeRule.onAllNodesWithTag(DatesTravelersScreenTestTags.SCREEN).assertCountEquals(0)

    // Returning through another screen must not replay the completed creation navigation.
    composeRule.onNodeWithTag(C.Tag.trips_profile_avatar).performClick()
    waitForTag(C.Tag.profile_title)
    pressBack()
    composeRule.waitUntil { composeRule.onNodeWithTag(C.Tag.trips_main_trip_card).isDisplayed() }
    composeRule.onNodeWithTag(C.Tag.trips_main_trip_card).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.trips_new_trip_button).performClick()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsNotEnabled()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.CLOSE).performClick()
    composeRule.waitUntil {
      composeRule.onNodeWithTag(C.Tag.trips_screen).isDisplayed() &&
          composeRule
              .onAllNodesWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION)
              .fetchSemanticsNodes()
              .isEmpty()
    }
    composeRule.waitForIdle()
    pressBack()
    composeRule.waitUntil { composeRule.activity.isFinishing }
    assertTrue(composeRule.activity.isFinishing)
  }

  @Test
  fun backFromTripsLeavesTheAppInsteadOfShowingPermissionsAgain() {
    waitForTrips()

    pressBack()

    assertTrue(composeRule.activity.isFinishing)
  }
}
