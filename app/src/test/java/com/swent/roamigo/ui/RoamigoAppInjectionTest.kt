// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.authentication.AuthenticatedUser
import com.swent.roamigo.model.authentication.FakeAuthRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.trip.Trip
import com.swent.roamigo.model.trip.TripStatus
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that [RoamigoApp] uses the repositories and nav controller it is given. */
@RunWith(AndroidJUnit4::class)
class RoamigoAppInjectionTest {
  @get:Rule val composeRule = createComposeRule()

  private fun timestamp(instant: Instant) = Timestamp(instant.epochSecond, instant.nano)

  @Test
  fun usesTheProvidedNavControllerAndRepositories() {
    val now = Instant.now()
    val trip =
        Trip(
            uid = "t1",
            name = "Injected Escape",
            description = null,
            destinations = listOf(TripLocation(38.7223, -9.1393, "Lisbon")),
            startDate = timestamp(now.minusSeconds(3600)),
            endDate = timestamp(now.plusSeconds(86_400)),
            ownerId = "sam",
            status = TripStatus.ACTIVE,
        )
    lateinit var navController: NavHostController
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            tripRepository = FakeTripRepository(trips = listOf(trip)),
            userRepository = FakeUserRepository(User("sam", "Sam", null)),
            navController = navController,
            authRepository =
                FakeAuthRepository(
                    AuthenticatedUser("sam", "Sam", null, null),
                    initiallySignedIn = true,
                ),
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.permissions_continue).performClick()
    composeRule.waitUntil {
      composeRule.onAllNodesWithTag(C.Tag.trips_main_trip_card).fetchSemanticsNodes().isNotEmpty()
    }

    composeRule
        .onNodeWithTag(C.Tag.trips_main_trip_card)
        .assert(hasAnyDescendant(hasText(trip.name, substring = true)))
    assertEquals(Route.TRIPS, navController.currentBackStackEntry?.destination?.route)
  }
}
