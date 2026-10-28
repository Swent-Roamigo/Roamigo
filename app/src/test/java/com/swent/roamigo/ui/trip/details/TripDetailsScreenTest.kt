// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.details

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.io.File
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
  fun rendersRealSummariesAndUnavailableFeatures() {
    composeRule.setContent { RoamigoTheme { TripDetailsScreen(readyState(), {}, {}, {}) } }
    composeRule.onNodeWithText("Lisbon & Porto").assertIsDisplayed()
    composeRule.onNodeWithText("1 member").assertIsDisplayed()
    composeRule.onNodeWithText("4 suggested places").assertIsDisplayed()
    composeRule.onNodeWithText("4").assertIsDisplayed()
    composeRule.onNodeWithText("12 – 19 Oct · 3 travelers").assertIsDisplayed()
    composeRule.onAllNodesWithText("—").assertCountEquals(2)
    val screenshot = File("build/reports/trip-details.png")
    screenshot.parentFile?.mkdirs()
    screenshot.outputStream().use {
      composeRule
          .onNodeWithTag("trip_details_screen")
          .captureToImage()
          .asAndroidBitmap()
          .compress(Bitmap.CompressFormat.PNG, 100, it)
    }
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
            onTripsClick = {},
        )
      }
    }
    composeRule.onNodeWithText("Loading trip details…").assertIsDisplayed()
    composeRule.runOnIdle { state.value = TripDetailsUiState(false, TripDetailsError.NOT_FOUND) }
    composeRule.onNodeWithText("This trip is no longer available to you.").assertIsDisplayed()
    composeRule.onNodeWithText("Retry").performClick()
    composeRule.onNodeWithText("Lisbon & Porto").assertIsDisplayed()
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
            onTripsClick = { actions += "trips" },
            onShareClick = { actions += "share" },
            onInviteClick = { actions += "invite" },
            onItineraryClick = { actions += "itinerary" },
            onVotesClick = { actions += "votes" },
            onMapClick = { actions += "map" },
            onPhotosClick = { actions += "photos" },
        )
      }
    }
    composeRule.onNodeWithText("Details").performClick()
    composeRule.onNodeWithContentDescription("Share trip").performClick()
    composeRule.onNodeWithText("Invite").performClick()
    composeRule.onNodeWithText("Itinerary").performClick()
    composeRule.onNodeWithText("Votes").performClick()
    composeRule.onNodeWithText("Open trip map").performClick()
    composeRule.onNodeWithText("Trips").performClick()
    composeRule.onNodeWithText("Map").performClick()
    composeRule.onNodeWithText("Photos").performClick()
    org.junit.Assert.assertEquals(
        listOf("share", "invite", "itinerary", "votes", "map", "trips", "map", "photos"),
        actions,
    )
  }

  @Test
  fun longNamesAndLargeTextKeepShareAndMapActionsReachable() {
    var shared = false
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
              onShareClick = { shared = true },
              onMapClick = { mapped = true },
          )
        }
      }
    }
    composeRule.onNodeWithText(name).assertIsDisplayed()
    composeRule.onNodeWithContentDescription("Share trip").assertIsDisplayed().performClick()
    composeRule.onNodeWithText("Open trip map").assertIsDisplayed().performClick()
    composeRule.onNodeWithText("Trip photos").performScrollTo().assertIsDisplayed()
    assertTrue(shared)
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
          TripDetailsScreen(readyState(), {}, {}, {}, onMapClick = { opened = true })
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
