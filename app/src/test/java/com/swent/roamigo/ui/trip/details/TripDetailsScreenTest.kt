// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.details

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TripDetailsScreenTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  @Config(qualifiers = "en-rUS-w390dp-h844dp-xhdpi")
  fun rendersRealSummariesAndLinkedFeatures() {
    composeRule.setContent { RoamigoTheme { TripDetailsScreen(readyState(), {}, {}) } }
    composeRule.onNodeWithText("1 member").assertIsDisplayed()
    composeRule.onNodeWithText("4 suggested places").assertIsDisplayed()
    composeRule.onNodeWithText("4").assertIsDisplayed()
    composeRule.onNodeWithText("Not available yet").assertDoesNotExist()
    composeRule.onNodeWithText("Trip photos").performScrollTo().assertIsDisplayed()
  }

  @Test
  fun loadingCanTransitionThroughUnavailableTripAndRetryToReady() {
    val state = mutableStateOf(TripDetailsUiState())
    composeRule.setContent {
      RoamigoTheme {
        TripDetailsScreen(
            state.value,
            onRetry = { state.value = readyState() },
            onBack = {},
        )
      }
    }
    composeRule.onNodeWithText("Loading trip details…").assertIsDisplayed()
    composeRule.runOnIdle { state.value = TripDetailsUiState(false, TripDetailsError.NOT_FOUND) }
    composeRule
        .onNodeWithText("This trip is private. Only invited members can open it.")
        .assertIsDisplayed()
    composeRule.onNodeWithText("Retry").performClick()
  }

  @Test
  fun readyScreenForwardsEveryNavigationAction() {
    val actions = mutableListOf<String>()
    composeRule.setContent {
      RoamigoTheme {
        TripDetailsScreen(
            readyState(),
            {},
            {},
            onMembersClick = { actions += "invite" },
            onVotesClick = { actions += "votes" },
            onTripMapClick = { actions += "tripMap" },
            onPhotosClick = { actions += "photos" },
        )
      }
    }
    composeRule.onNodeWithText("Members").performScrollTo().performClick()
    composeRule.onNodeWithText("Live votes").performScrollTo().performClick()
    composeRule.onNodeWithText("Open trip map").performClick()
    composeRule.onNodeWithText("Trip photos").performScrollTo().performClick()
    org.junit.Assert.assertEquals(
        listOf("invite", "votes", "tripMap", "photos"),
        actions,
    )
  }

  @Test
  fun longNamesAndLargeTextKeepPhotoAndMapActionsReachable() {

    var mapped = false
    val name = "Lisbon, Porto and the Atlantic coast with friends"
    val state = readyState().let { it.copy(details = it.details!!.copy(name = name)) }
    composeRule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
        RoamigoTheme {
          TripDetailsScreen(
              state,
              {},
              {},
              {},
              onTripMapClick = { mapped = true },
          )
        }
      }
    }
    composeRule.onNodeWithText("Open trip map").assertIsDisplayed().performClick()
    composeRule.onNodeWithText("Trip photos").performScrollTo().assertIsDisplayed()
    assertTrue(mapped)
  }

  @Test
  fun errorOffersRetryAndBack() {
    var retried = false
    var backed = false
    composeRule.setContent {
      RoamigoTheme {
        TripDetailsScreen(
            TripDetailsUiState(false, TripDetailsError.LOAD_FAILED),
            { retried = true },
            { backed = true },
            {},
        )
      }
    }
    composeRule.onNodeWithText("Unable to load trip details.").assertIsDisplayed()
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithText("Back").performClick()
    assertTrue(retried)
    assertTrue(backed)
  }

  @Test
  fun mapActionRemainsReachableOnShortScreens() {
    var opened = false
    composeRule.setContent {
      RoamigoTheme {
        Box(Modifier.height(350.dp)) {
          TripDetailsScreen(readyState(), {}, {}, onTripMapClick = { opened = true })
        }
      }
    }
    composeRule.onNodeWithText("Open trip map").assertIsDisplayed().performClick()
    assertTrue(opened)
  }

  private fun readyState() =
      TripDetailsUiState(
          isLoading = false,
          details =
              TripDetails(
                  "Lisbon & Porto",
                  LocalDate.of(2026, 10, 12),
                  LocalDate.of(2026, 10, 19),
                  3,
                  1,
                  4,
              ),
      )
}
