// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.model.users.FakeUserRepository
import com.swent.roamigo.model.users.User
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.navigation.Route
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Covers the explicit-argument side of [RoamigoApp]'s default parameters. */
@RunWith(AndroidJUnit4::class)
class RoamigoAppInjectionTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun usesTheProvidedNavControllerAndRepositories() {
    lateinit var navController: NavHostController
    composeRule.setContent {
      navController = rememberNavController()
      RoamigoTheme {
        RoamigoApp(
            tripRepository = FakeTripRepository(),
            userRepository = FakeUserRepository(User("sam", "Sam", null)),
            navController = navController,
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.permissions_continue).performClick()
    composeRule.waitForIdle()

    assertEquals(Route.TRIPS, navController.currentBackStackEntry?.destination?.route)
  }
}
