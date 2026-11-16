// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import android.os.Parcel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
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
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlacePickerScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val candidate = PlaceCandidate("cafe", "Café Romand", "Lausanne", "Provider attribution")

  @Test
  fun twoPickersInSameParentHaveIndependentDrafts() {
    val repo = FakePlaceRepository()
    compose.setContent {
      Column {
        Box(Modifier.weight(1f)) {
          PlacePickerRoute(repo, {}, {}, initialLocation = TripLocation(1.0, 2.0, "First"))
        }
        Box(Modifier.weight(1f)) {
          PlacePickerRoute(repo, {}, {}, initialLocation = TripLocation(3.0, 4.0, "Second"))
        }
      }
    }
    compose.onAllNodesWithTag(PlacePickerTestTags.QUERY)[0].performTextInput("first query")
    compose
        .onAllNodesWithTag(PlacePickerTestTags.QUERY)[1]
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    compose.onNodeWithText("Selected place: First").assertExists()
    compose.onNodeWithText("Selected place: Second").assertExists()
  }

  @Test
  fun removingPickerClearsItsViewModelAndReopeningCreatesFreshInstance() {
    val models = mutableListOf<PlacePickerViewModel>()
    var cleared = 0
    var showing by mutableStateOf(true)
    val repo = FakePlaceRepository()
    val factory =
        object : ViewModelProvider.Factory {
          @Suppress("UNCHECKED_CAST")
          override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            return PlacePickerViewModel(repo, extras.createSavedStateHandle()).also {
              models += it
              it.addCloseable { cleared++ }
            } as T
          }
        }
    compose.setContent { if (showing) PlacePickerRoute(repo, {}, {}, viewModelFactory = factory) }
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("old query")
    repeat(3) { opening ->
      compose.runOnIdle { showing = false }
      compose.runOnIdle { assertEquals(opening + 1, cleared) }
      compose.runOnIdle { showing = true }
      compose
          .onNodeWithTag(PlacePickerTestTags.QUERY)
          .assert(
              SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
          )
      compose.runOnIdle {
        assertEquals(opening + 2, models.size)
        assertNotSame(models[opening], models[opening + 1])
      }
    }
  }

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
    compose.onNodeWithText("Confirm Chosen café").assertIsEnabled()
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
    var parentTitle by mutableStateOf("")
    var parentDescription by mutableStateOf("")
    var showing by mutableStateOf(false)
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
        else
            Column {
              OutlinedTextField(parentTitle, { parentTitle = it }, Modifier.testTag("title"))
              OutlinedTextField(
                  parentDescription,
                  { parentDescription = it },
                  Modifier.testTag("description"),
              )
              Button(onClick = { showing = true }) { Text("Open picker") }
            }
      }
    }
    compose.onNodeWithTag("title").performTextInput("Edited title")
    compose.onNodeWithTag("description").performTextInput("Edited description")
    compose.onNodeWithText("Open picker").performClick()
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("café")
    awaitTag(PlacePickerTestTags.ERROR)
    compose.onNodeWithTag(PlacePickerTestTags.SELECTED).assertTextEquals("Selected place: Original")
    compose.runOnIdle {
      assertEquals("Edited title", parentTitle)
      assertEquals("Edited description", parentDescription)
      assertEquals(TripLocation(0.0, 0.0, "Original"), parentLocation)
    }
    compose.onNodeWithTag(PlacePickerTestTags.CANCEL).performClick()
    compose.runOnIdle {
      assertEquals("Edited title", parentTitle)
      assertEquals("Edited description", parentDescription)
      assertEquals(TripLocation(0.0, 0.0, "Original"), parentLocation)
      repo.search = { _, _ -> listOf(candidate) }
    }
    compose.onNodeWithText("Open picker").performClick()
    compose
        .onNodeWithTag(PlacePickerTestTags.QUERY)
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
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
      assertEquals(TripLocation(46.52, 6.63, "Café Romand"), parentLocation)
      assertEquals("Edited title", parentTitle)
      assertEquals("Edited description", parentDescription)
    }
    // Reopening uses the caller's current value, not a completed picker ViewModel/result.
    compose.onNodeWithText("Open picker").performClick()
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsEnabled()
  }

  @Test
  fun draftSurvivesActivityRecreationWithoutRetrievingOrDeliveringAgain() {
    val repo = FakePlaceRepository(search = { _, _ -> listOf(candidate) })
    val deliveries = mutableListOf<TripLocation>()
    val content: @Composable () -> Unit = {
      RoamigoTheme { PlacePickerRoute(repo, { deliveries += it }, {}) }
    }
    compose.setContent(content)
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("café")
    awaitTag(PlacePickerTestTags.candidate(candidate.id))
    compose.onNodeWithTag(PlacePickerTestTags.candidate(candidate.id)).performClick()
    awaitTag(PlacePickerTestTags.SELECTED)
    compose.activityRule.scenario.recreate()
    compose.activityRule.scenario.onActivity { it.setContent(content = content) }
    compose
        .onNodeWithTag(PlacePickerTestTags.SELECTED)
        .assertTextEquals("Selected place: Café Romand")
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).performClick()
    compose.waitUntil { deliveries.size == 1 }
    compose.activityRule.scenario.recreate()
    compose.activityRule.scenario.onActivity { it.setContent(content = content) }
    compose.runOnIdle {
      assertEquals(1, deliveries.size)
      assertEquals(1, repo.resolveRequests.size)
      assertEquals(1, repo.searchRequests.size)
    }
  }

  @Test
  fun displayedSearchResultsSurviveActualActivityRecreationWithoutAnotherRequest() {
    val repo = FakePlaceRepository(search = { _, _ -> listOf(candidate) })
    val content: @Composable () -> Unit = { RoamigoTheme { PlacePickerRoute(repo, {}, {}) } }
    compose.setContent(content)
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("café")
    awaitTag(PlacePickerTestTags.candidate(candidate.id))
    compose.activityRule.scenario.recreate()
    compose.activityRule.scenario.onActivity { it.setContent(content = content) }
    compose.onNodeWithTag(PlacePickerTestTags.candidate(candidate.id)).assertIsDisplayed()
    compose
        .onNodeWithTag(PlacePickerTestTags.QUERY)
        .assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("café"))
        )
    compose.onNodeWithTag(PlacePickerTestTags.candidate(candidate.id)).performClick()
    awaitTag(PlacePickerTestTags.SELECTED)
    compose.runOnIdle {
      assertEquals(1, repo.searchRequests.size)
      assertEquals(1, repo.resolveRequests.size)
    }
  }

  @Test
  fun pendingSearchSurvivesActualActivityRecreationAndPublishesOnce() {
    val answer = CompletableDeferred<List<PlaceCandidate>>()
    val repo = FakePlaceRepository(search = { _, _ -> answer.await() })
    val content: @Composable () -> Unit = {
      RoamigoTheme {
        PlacePickerRoute(repo, {}, {}, initialLocation = TripLocation(0.0, 0.0, "Previous"))
      }
    }
    compose.setContent(content)
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("café")
    compose.waitUntil(5_000) { compose.runOnIdle { repo.searchRequests.size == 1 } }
    compose.activityRule.scenario.recreate()
    compose.activityRule.scenario.onActivity { it.setContent(content = content) }
    compose.onNodeWithTag(PlacePickerTestTags.LOADING).assertExists()
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsNotEnabled()
    compose
        .onNodeWithTag(PlacePickerTestTags.QUERY)
        .assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("café"))
        )
    compose.onNodeWithTag(PlacePickerTestTags.SELECTED).assertTextEquals("Selected place: Previous")
    answer.complete(listOf(candidate))
    awaitTag(PlacePickerTestTags.candidate(candidate.id))
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsEnabled()
    compose.runOnIdle { assertEquals(1, repo.searchRequests.size) }
  }

  @Test
  fun searchingGenevaDisablesConfirmationAndStillNamesLausanneAfterEmptyResultsOrFailure() {
    val lausanne = PlaceCandidate("lausanne", "Lausanne", null)
    val geneva = PlaceCandidate("geneva", "Geneva", null)
    val search = CompletableDeferred<List<PlaceCandidate>>()
    val resolution = CompletableDeferred<Place>()
    val repo = FakePlaceRepository(search = { _, _ -> listOf(lausanne) })
    val deliveries = mutableListOf<TripLocation>()
    compose.setContent { RoamigoTheme { PlacePickerRoute(repo, { deliveries += it }, {}) } }
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertTextEquals("Confirm location")
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("Lausanne")
    awaitTag(PlacePickerTestTags.candidate(lausanne.id))
    compose.onNodeWithTag(PlacePickerTestTags.candidate(lausanne.id)).performClick()
    awaitTag(PlacePickerTestTags.SELECTED)
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertTextEquals("Confirm Lausanne")
    compose.runOnIdle { repo.search = { _, _ -> search.await() } }
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextReplacement("Geneva")
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsNotEnabled().performClick()
    search.complete(emptyList())
    awaitTag(PlacePickerTestTags.EMPTY)
    compose
        .onNodeWithTag(PlacePickerTestTags.CONFIRM)
        .assertIsEnabled()
        .assertTextEquals("Confirm Lausanne")
    compose.runOnIdle {
      repo.search = { _, _ -> throw PlaceSearchException.Unavailable("offline") }
    }
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performImeAction()
    awaitTag(PlacePickerTestTags.ERROR)
    compose
        .onNodeWithTag(PlacePickerTestTags.CONFIRM)
        .assertIsEnabled()
        .assertTextEquals("Confirm Lausanne")
    compose.runOnIdle {
      repo.search = { _, _ -> listOf(geneva) }
      repo.resolve = { resolution.await() }
    }
    compose.onNodeWithTag(PlacePickerTestTags.RETRY).performScrollTo().performClick()
    awaitTag(PlacePickerTestTags.candidate(geneva.id))
    compose.onNodeWithTag(PlacePickerTestTags.candidate(geneva.id)).performClick()
    awaitTag(PlacePickerTestTags.RESOLVING)
    compose
        .onNodeWithTag(PlacePickerTestTags.CONFIRM)
        .assertIsNotEnabled()
        .assertTextEquals("Confirm Lausanne")
    resolution.complete(Place("Geneva", null, 46.2, 6.14))
    compose.waitUntil(5_000) {
      compose.onAllNodesWithText("Confirm Geneva").fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsEnabled().performClick()
    compose.waitUntil { deliveries.size == 1 }
    compose.runOnIdle { assertEquals("Geneva", deliveries.single().name) }
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
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun compactPortraitKeepsConfirmationVisibleAndCandidatesScrollable() = compactLayout(320, 420)

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun landscapeKeepsConfirmationVisibleAndCandidatesScrollable() = compactLayout(640, 280)

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
                  selectedLocation =
                      TripLocation(
                          0.0,
                          0.0,
                          "Lausanne – Place Saint-François and the surrounding old town",
                      ),
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
    val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
    compose
        .onNodeWithText(
            "Confirm Lausanne – Place Saint-François and the surrounding old town",
            useUnmergedTree = true,
        )
        .performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
        ) {
          it(layouts)
        }
    compose.runOnIdle {
      assertFalse(
          "Confirmation label must fit: ${layouts.single()}",
          layouts.single().hasVisualOverflow,
      )
      if (width == 320) assertTrue(layouts.single().lineCount > 1)
    }
    compose.onNodeWithTag(PlacePickerTestTags.candidate("8")).performScrollTo().assertIsDisplayed()
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsDisplayed()
    compose.runOnIdle { assertEquals(1, confirms) }
  }

  private fun awaitTag(tag: String) {
    compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
  }
}
