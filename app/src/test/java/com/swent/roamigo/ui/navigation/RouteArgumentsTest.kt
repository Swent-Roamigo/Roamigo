// AI assistance was used for implementation support.
package com.swent.roamigo.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.trip.activity.ActivityFormTrip
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Reads route arguments back through a real navigation graph. */
@RunWith(AndroidJUnit4::class)
class RouteArgumentsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var navController: NavHostController
  private var read: Result<ActivityFormTrip>? = null
  private var startRead: Result<ActivityFormTrip>? = null

  private fun openGraph() {
    compose.setContent {
      navController = rememberNavController()
      NavHost(navController, startDestination = START) {
        composable(START) { entry -> startRead = runCatching { entry.activityFormTrip() } }
        composable(Route.NEW_ACTIVITY) { entry -> read = runCatching { entry.activityFormTrip() } }
      }
    }
    compose.waitForIdle()
  }

  @Test
  fun newActivityGivesBackTheTripItWasOpenedFor() {
    val trip =
        ActivityFormTrip(
            "Lisbon & Porto / 2026?",
            LocalDate.of(2026, 10, 12),
            LocalDate.of(2026, 10, 18),
        )
    openGraph()

    compose.runOnIdle { navController.navigate(Route.newActivity("trip-1", trip)) }
    compose.waitForIdle()

    assertEquals(trip, read?.getOrThrow())
  }

  @Test
  fun aMissingTripNameIsRejectedWithItsName() {
    openGraph()

    compose.runOnIdle {
      navController.navigate("trip/trip-1/new_activity?startDate=2026-10-12&endDate=2026-10-18")
    }
    compose.waitForIdle()

    val failure = read?.exceptionOrNull()
    assertTrue(failure is IllegalArgumentException)
    assertEquals("Missing route argument tripName", failure?.message)
  }

  @Test
  fun aDestinationWithoutArgumentsIsNotReadAsAnActivityForm() {
    openGraph()

    val failure = startRead?.exceptionOrNull()
    assertTrue(failure is IllegalArgumentException)
    assertEquals("Missing route argument tripName", failure?.message)
  }

  private companion object {
    const val START = "start"
  }
}
