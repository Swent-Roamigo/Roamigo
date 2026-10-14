// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffoldTestTags
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that the screens are connected as intended and that Back returns to the right one. */
@RunWith(AndroidJUnit4::class)
class RoamigoAppTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setUp() {
    val authRepository =
        FakeAuthRepository(AuthenticatedUser("alex", "Alex", null, null), initiallySignedIn = true)
    composeRule.setContent { RoamigoTheme { RoamigoApp(authRepository = authRepository) } }
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
  fun backFromTripsLeavesTheAppInsteadOfShowingPermissionsAgain() {
    waitForTrips()

    pressBack()

    assertTrue(composeRule.activity.isFinishing)
  }
}
