package com.swent.roamigo.ui.heatmap

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.history.TripHistoryRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class TravelHeatmapNavigationTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun historyToWorldAndBackReusesHeatmapData() {
    val trips = FakeTripRepository()
    var loadCalls = 0

    val source = VisitedCountryCounts {
      loadCalls++
      mapOf("PT" to 2)
    }

    composeRule.setContent {
      val navController = rememberNavController()

      RoamigoTheme {
        NavHost(
            navController = navController,
            startDestination = Route.TRIP_HISTORY,
        ) {
          composable(Route.TRIP_HISTORY) { entry ->
            TripHistoryRoute(
                tripRepository = trips,
                countryCounts = source,
                sharedOwner = entry,
                onBackClick = { navController.popBackStack() },
                onTripsClick = {},
                onMapClick = {},
                onOpenTrip = {},
                onOpenWorldMapClick = { navController.navigate(Route.EXPLORED_WORLD) },
                onFriendsClick = {},
            )
          }

          composable(Route.EXPLORED_WORLD) { entry ->
            val historyEntry =
                remember(entry) { navController.getBackStackEntry(Route.TRIP_HISTORY) }

            ExploredWorldRoute(
                tripRepository = trips,
                countryCounts = source,
                sharedOwner = historyEntry,
                onBackClick = { navController.popBackStack() },
            )
          }
        }
      }
    }

    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule
          .onAllNodesWithTag(C.Tag.trip_history_world_map)
          .fetchSemanticsNodes()
          .isNotEmpty() && loadCalls == 1
    }

    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).performClick()

    composeRule.onNodeWithTag(C.Tag.heatmap_screen).assertIsDisplayed()

    composeRule.onNodeWithTag(C.Tag.heatmap_back).performClick()

    composeRule.onNodeWithTag(C.Tag.trip_history_screen).assertIsDisplayed()

    composeRule.runOnIdle { assertEquals(1, loadCalls) }
  }
}
