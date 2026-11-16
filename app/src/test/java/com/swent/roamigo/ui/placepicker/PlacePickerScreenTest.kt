// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import android.os.Parcel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.place.*
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlacePickerScreenTest {
  @get:Rule val compose = createComposeRule()
  private val candidate = PlaceCandidate("cafe", "Café Romand", "Lausanne", "Provider attribution")

  @Test
  fun searchAndResolveAreSeparateAndConfirmationReturnsOnlyOnce() {
    val search = CompletableDeferred<List<PlaceCandidate>>()
    val resolve = CompletableDeferred<Place>()
    val repo =
        FakePlaceRepository(search = { _, _ -> search.await() }, resolve = { resolve.await() })
    val results = mutableListOf<TripLocation>()
    compose.setContent { RoamigoTheme { PlacePickerRoute(repo, { results += it }, {}) } }
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsNotEnabled()
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("ca")
    compose.onNodeWithText("Enter at least 3 characters to search.").assertIsDisplayed()
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("fé")
    awaitTag(PlacePickerTestTags.LOADING)
    search.complete(listOf(candidate))
    awaitTag(PlacePickerTestTags.candidate(candidate.id))
    compose.onNodeWithText("Lausanne").assertIsDisplayed()
    compose.onNodeWithText("Provider attribution").performScrollTo().assertIsDisplayed()
    compose
        .onNodeWithTag(PlacePickerTestTags.candidate(candidate.id))
        .performScrollTo()
        .performClick()
    awaitTag(PlacePickerTestTags.RESOLVING)
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsNotEnabled()
    compose.runOnIdle {
      assertEquals(listOf(candidate), repo.resolveRequests)
      assertTrue(results.isEmpty())
    }
    resolve.complete(Place("Chosen café", null, 46.52, 6.63))
    awaitTag(PlacePickerTestTags.SELECTED)
    compose
        .onNodeWithTag(PlacePickerTestTags.SELECTED)
        .assertIsDisplayed()
        .assertTextEquals("Selected place: Chosen café")
    compose.onNodeWithText("Search for a place and select a result.").assertDoesNotExist()
    compose.onNodeWithText("Confirm location").assertIsEnabled()
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).performClick()
    compose.waitUntil { results.size == 1 }
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsNotEnabled()
    compose.runOnIdle { assertEquals(listOf(TripLocation(46.52, 6.63, "Chosen café")), results) }
  }

  @Test
  fun emptyResultsAreClearAndAttributionOpensOnlyTrustedUrl() {
    val state = mutableStateOf(PlacePickerUiState())
    val links = mutableListOf<String>()
    compose.setContent {
      CompositionLocalProvider(
          LocalUriHandler provides
              object : UriHandler {
                override fun openUri(uri: String) {
                  links += uri
                }
              }
      ) {
        RoamigoTheme { PlacePickerScreen(state.value, {}, {}, {}, {}, {}) }
      }
    }
    compose.onNodeWithText("Search for a place and select a result.").assertIsDisplayed()
    compose.runOnIdle {
      state.value = PlacePickerUiState(query = "café", searchStatus = PlaceSearchStatus.EMPTY)
    }
    compose.onNodeWithTag(PlacePickerTestTags.EMPTY).assertIsDisplayed()
    compose.onNodeWithTag(PlacePickerTestTags.ATTRIBUTION).assertIsDisplayed().performClick()
    compose.runOnIdle { assertEquals(listOf("https://www.mapbox.com/about/maps/"), links) }
  }

  @Test
  fun everyErrorIsLocalizedAndOnlyRecoverableErrorsOfferSearchAgain() {
    val state = mutableStateOf(PlacePickerUiState())
    var retries = 0
    compose.setContent {
      RoamigoTheme { PlacePickerScreen(state.value, {}, {}, { retries++ }, {}, {}) }
    }
    val messages =
        mapOf(
            PlacePickerError.CONFIGURATION to "Place search is not configured in this build.",
            PlacePickerError.INVALID_TOKEN to
                "Place search is unavailable because its access token was refused.",
            PlacePickerError.QUOTA to "The place search limit has been reached. Try again later.",
            PlacePickerError.INVALID_PLACE to
                "This place has no usable location. Search again and choose another result.",
            PlacePickerError.UNAVAILABLE to
                "Could not reach place search. Check your connection and try again.",
        )
    messages.forEach { (error, message) ->
      compose.runOnIdle {
        state.value =
            PlacePickerUiState(
                query = "café",
                searchStatus = PlaceSearchStatus.ERROR,
                searchError = error,
            )
      }
      compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
      if (error == PlacePickerError.INVALID_PLACE || error == PlacePickerError.UNAVAILABLE) {
        compose.onNodeWithTag(PlacePickerTestTags.RETRY).performScrollTo().performClick()
      } else compose.onNodeWithTag(PlacePickerTestTags.RETRY).assertDoesNotExist()
    }
    compose.runOnIdle { assertEquals(2, retries) }
    compose.runOnIdle {
      state.value =
          PlacePickerUiState(query = "café", resolutionError = PlacePickerError.INVALID_PLACE)
    }
    compose.onNodeWithTag(PlacePickerTestTags.ERROR).performScrollTo().assertIsDisplayed()
  }

  @Test
  fun parentDraftSurvivesQuotaFailureCancelReopenAndSuccessfulReturn() {
    val repo = FakePlaceRepository(search = { _, _ -> throw PlaceSearchException.QuotaExceeded() })
    var parentLocation = TripLocation(0.0, 0.0, "Original")
    val parentTitle = "Existing title"
    var showing by mutableStateOf(true)
    compose.setContent {
      RoamigoTheme {
        if (showing)
            PlacePickerRoute(
                repo,
                {
                  parentLocation = it
                  showing = false
                },
                { showing = false },
                initialLocation = parentLocation,
            )
        else Button(onClick = { showing = true }) { Text(parentTitle) }
      }
    }
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("café")
    awaitTag(PlacePickerTestTags.ERROR)
    compose.onNodeWithTag(PlacePickerTestTags.SELECTED).assertTextEquals("Selected place: Original")
    compose.onNodeWithTag(PlacePickerTestTags.CANCEL).performClick()
    compose.runOnIdle {
      assertEquals("Existing title", parentTitle)
      assertEquals("Original", parentLocation.name)
      repo.search = { _, _ -> listOf(candidate) }
    }
    compose.onNodeWithText("Existing title").performClick()
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).assertTextContains("")
    compose.onNodeWithTag(PlacePickerTestTags.ERROR).assertDoesNotExist()
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("café")
    awaitTag(PlacePickerTestTags.candidate(candidate.id))
    compose.onNodeWithTag(PlacePickerTestTags.candidate(candidate.id)).performClick()
    awaitTag(PlacePickerTestTags.SELECTED)
    compose.waitUntil {
      compose.onAllNodesWithText("Selected place: Café Romand").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).performClick()
    compose.waitUntil { !showing }
    compose.runOnIdle {
      assertEquals("Café Romand", parentLocation.name)
      assertEquals("Existing title", parentTitle)
    }
    // Reopening uses the caller's current value, not a completed picker ViewModel/result.
    compose.onNodeWithText("Existing title").performClick()
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsEnabled()
  }

  @Test
  fun routeKeyAndDraftSurviveCompositionRestorationWithoutRetrievingAgain() {
    val restoration = StateRestorationTester(compose)
    val repo = FakePlaceRepository(search = { _, _ -> listOf(candidate) })
    val deliveries = mutableListOf<TripLocation>()
    restoration.setContent { RoamigoTheme { PlacePickerRoute(repo, { deliveries += it }, {}) } }
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("café")
    awaitTag(PlacePickerTestTags.candidate(candidate.id))
    compose.onNodeWithTag(PlacePickerTestTags.candidate(candidate.id)).performClick()
    awaitTag(PlacePickerTestTags.SELECTED)
    restoration.emulateSavedInstanceStateRestore()
    compose
        .onNodeWithTag(PlacePickerTestTags.SELECTED)
        .assertTextEquals("Selected place: Café Romand")
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).performClick()
    compose.waitUntil { deliveries.size == 1 }
    restoration.emulateSavedInstanceStateRestore()
    compose.runOnIdle {
      assertEquals(1, deliveries.size)
      assertEquals(1, repo.resolveRequests.size)
    }
  }

  @Test
  fun processRestorationRoundTripsPrimitivesAndDropsExpiredCandidates() {
    val handle =
        SavedStateHandle(
            mapOf(
                "initialized" to true,
                "name" to "  Chosen  ",
                "latitude" to 0.0,
                "longitude" to 180.0,
                "country" to "CH",
                "query" to "café",
            )
        )
    val parcel = Parcel.obtain()
    val restored =
        try {
          parcel.writeBundle(handle.savedStateProvider().saveState())
          parcel.setDataPosition(0)
          SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
        } finally {
          parcel.recycle()
        }
    val repo = FakePlaceRepository()
    val vm = PlacePickerViewModel(repo, restored)
    assertEquals(TripLocation(0.0, 180.0, "Chosen", "CH"), vm.uiState.value.selectedLocation)
    assertEquals("café", vm.uiState.value.query)
    assertTrue(vm.uiState.value.candidates.isEmpty())
    assertTrue(repo.resolveRequests.isEmpty())
  }

  @Test
  fun compactPortraitKeepsConfirmationVisibleAndCandidatesScrollable() = compactLayout(320, 420)

  @Test fun landscapeKeepsConfirmationVisibleAndCandidatesScrollable() = compactLayout(640, 280)

  private fun compactLayout(width: Int, height: Int) {
    var confirms = 0
    compose.setContent {
      RoamigoTheme {
        Box(Modifier.requiredSize(width.dp, height.dp)) {
          PlacePickerScreen(
              PlacePickerUiState(
                  query = "café",
                  searchStatus = PlaceSearchStatus.RESULTS,
                  candidates = (1..8).map { candidate.copy(id = it.toString()) },
                  selectedLocation = TripLocation(0.0, 0.0, "Chosen"),
              ),
              {},
              {},
              {},
              { confirms++ },
              {},
          )
        }
      }
    }
    compose
        .onNodeWithTag(PlacePickerTestTags.CONFIRM)
        .assertIsDisplayed()
        .assertIsEnabled()
        .performClick()
    compose.onNodeWithTag(PlacePickerTestTags.candidate("8")).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsDisplayed()
    compose.runOnIdle { assertEquals(1, confirms) }
  }

  private fun awaitTag(tag: String) {
    compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
  }
}
