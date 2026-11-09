// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.destination.Destination
import com.swent.roamigo.model.destination.DestinationKind
import com.swent.roamigo.model.destination.DestinationPhoto
import com.swent.roamigo.model.destination.DestinationRepository
import com.swent.roamigo.model.destination.FakeDestinationRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.ui.components.PHOTO_SHIMMER_TAG
import com.swent.roamigo.ui.components.PhotoCreditsDialogTestTags
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffoldTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationCardTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationScreenTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationSearchBarTestTags
import com.swent.roamigo.ui.trip.creation.components.SelectedDestinationsTestTags
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DestinationScreenTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun loadingSearchExposesProgressUntilRepositoryCompletes() {
    val paris = destination("paris-fr", "Paris", "France", "FR", 2988507)
    val response = CompletableDeferred<List<Destination>>()
    val repository = FakeDestinationRepository(search = { response.await() })
    showRoute(repository)

    search("Paris")
    waitForTag(DestinationScreenTestTags.LOADING)
    composeRule.onNodeWithTag(DestinationScreenTestTags.LOADING).assertIsDisplayed()

    response.complete(listOf(paris))
    waitForTag(DestinationCardTestTags.forDestination(paris.stableId))
    composeRule
        .onNodeWithTag(DestinationCardTestTags.forDestination(paris.stableId))
        .assertIsDisplayed()
    composeRule.onNodeWithTag(DestinationScreenTestTags.LOADING).assertDoesNotExist()
  }

  @Test
  fun cardsShimmerWhileTheirPhotoIsStillBeingFound() {
    val paris = destination("geonames:2988507", "Paris", "France", "FR", 2988507)
    showRoute(FakeDestinationRepository(search = { listOf(paris) }, photo = { null }))

    search("Paris")
    waitForTag(DestinationCardTestTags.forDestination(paris.stableId))

    // The card merges its children, so the shimmer is only visible in the unmerged tree.
    composeRule.onAllNodesWithTag(PHOTO_SHIMMER_TAG, useUnmergedTree = true)[0].assertIsDisplayed()
  }

  @Test
  fun recoverableErrorCanRetryAndSuccessfulSearchShowsStableIdCard() {
    var shouldFail = true
    val paris = destination("geonames:2988507", "Paris", "France", "FR", 2988507)
    val repository =
        FakeDestinationRepository(
            search = {
              if (shouldFail) throw IllegalStateException("temporarily unavailable")
              listOf(paris)
            },
        )
    showRoute(repository)

    search("Paris")
    waitForTag(DestinationScreenTestTags.ERROR)
    composeRule.onNodeWithTag(DestinationScreenTestTags.ERROR).assertIsDisplayed()

    shouldFail = false
    composeRule.onNodeWithTag(DestinationScreenTestTags.RETRY).performClick()
    waitForTag(DestinationCardTestTags.forDestination(paris.stableId))

    composeRule
        .onNodeWithTag(DestinationCardTestTags.forDestination(paris.stableId))
        .assertIsDisplayed()
    composeRule.onNodeWithTag(DestinationScreenTestTags.ERROR).assertDoesNotExist()
    assertEquals(listOf("Paris", "Paris"), repository.searchQueries)
  }

  @Test
  fun visibleCardsRequestPhotosLazilyAndPhotoCreditIsAccessible() {
    val destinations = (1..20).map { destination("city-$it", "City $it") }
    val photo =
        DestinationPhoto(
            imageUrl =
                "android.resource://com.swent.roamigo/${com.swent.roamigo.R.drawable.destination_placeholder}",
            creator = "A. Photographer",
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Example.jpg",
            license = "CC BY-SA 4.0",
            licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
            title = "City skyline",
            credit = "© A. Photographer",
        )
    val repository =
        FakeDestinationRepository(
            search = { destinations },
            photo = { if (it.stableId == destinations.first().stableId) photo else null },
        )
    showRoute(repository)

    search("City")
    waitForTag(DestinationCardTestTags.photoCreditsFor(destinations.first().stableId))

    composeRule
        .onNodeWithTag(DestinationCardTestTags.photoCreditsFor(destinations.first().stableId))
        .assertExists()
        .assertHasClickAction()
        .performClick()
    composeRule.onNodeWithText("City skyline").assertIsDisplayed()
    composeRule.onNodeWithText("Photo: A. Photographer").assertIsDisplayed()
    composeRule.onNodeWithText("© A. Photographer").assertIsDisplayed()
    composeRule.onNodeWithText("CC BY-SA 4.0 · Cropped for display").assertIsDisplayed()
    composeRule.onNodeWithText("View original").assertHasClickAction()
    composeRule.onNodeWithText("View license").assertHasClickAction()
    assertFalse(repository.photoRequests.any { it.stableId == destinations.last().stableId })
    composeRule.onNodeWithTag(PhotoCreditsDialogTestTags.CLOSE).performClick()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsNotEnabled()
  }

  @Test
  fun creditsHideLicenseLinkWhenUrlIsMissing() {
    val city = destination("paris-fr", "Paris", "France", "FR", 2988507)
    val photo =
        DestinationPhoto(
            imageUrl =
                "android.resource://com.swent.roamigo/${com.swent.roamigo.R.drawable.destination_placeholder}",
            creator = "Photographer",
            sourceUrl = "https://example.org/photo",
            license = "CC BY 4.0",
            licenseUrl = null,
        )
    showScreen(
        CreateTripUiState(
            destinations = listOf(city),
            destinationSearchStatus = DestinationSearchStatus.RESULTS,
            photosByDestinationId = mapOf(city.stableId to photo),
        )
    )
    waitForTag(DestinationCardTestTags.photoCreditsFor(city.stableId))
    composeRule.onNodeWithTag(DestinationCardTestTags.photoCreditsFor(city.stableId)).performClick()
    composeRule.onNodeWithText("View original").assertHasClickAction()
    composeRule.onNodeWithText("View license").assertDoesNotExist()
    composeRule.onNodeWithText("CC BY 4.0 · Cropped for display").assertIsDisplayed()
  }

  @Test
  fun failedPhotoDoesNotDisplayAttributionOverPlaceholder() {
    val city = destination("paris-fr", "Paris", "France", "FR", 2988507)
    val photo =
        DestinationPhoto(
            imageUrl = "file:///no-such-destination-image.jpg",
            creator = "A. Photographer",
            sourceUrl = "https://example.org/photo",
            license = "CC BY 4.0",
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
        )
    showScreen(
        CreateTripUiState(
            destinations = listOf(city),
            destinationSearchStatus = DestinationSearchStatus.RESULTS,
            photosByDestinationId = mapOf(city.stableId to photo),
        )
    )

    composeRule
        .onNodeWithTag(DestinationCardTestTags.forDestination(city.stableId))
        .assertIsDisplayed()
    composeRule
        .onNodeWithTag(DestinationCardTestTags.photoCreditsFor(city.stableId))
        .assertDoesNotExist()
  }

  @Test
  fun publicDomainPhotoCreditsUseFallbackCreatorAndHideDuplicateCredit() {
    val city = destination("lisbon-pt", "Lisbon", "Portugal", "PT", 2267057)
    val photo =
        DestinationPhoto(
            imageUrl =
                "android.resource://com.swent.roamigo/${com.swent.roamigo.R.drawable.destination_placeholder}",
            creator = null,
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Lisbon.jpg",
            license = "Public domain",
            licenseUrl = "https://creativecommons.org/publicdomain/mark/1.0/",
            credit = "Public domain",
        )
    showScreen(
        CreateTripUiState(
            destinations = listOf(city),
            destinationSearchStatus = DestinationSearchStatus.RESULTS,
            photosByDestinationId = mapOf(city.stableId to photo),
        )
    )

    waitForTag(DestinationCardTestTags.photoCreditsFor(city.stableId))
    composeRule.onNodeWithTag(DestinationCardTestTags.photoCreditsFor(city.stableId)).performClick()

    composeRule.onNodeWithText("Public domain").assertIsDisplayed()
    composeRule.onNodeWithText("Photo: Public domain").assertIsDisplayed()
    composeRule.onNodeWithText("Photo: ").assertDoesNotExist()
  }

  @Test
  fun noResultsAndRemovalKeepOtherSelectedDestination() {
    val paris = destination("paris-fr", "Paris", "France", "FR", 2988507)
    val lisbon = destination("lisbon-pt", "Lisbon", "Portugal", "PT", 2267057)
    val repository =
        FakeDestinationRepository(
            search = { if (it == "all") listOf(paris, lisbon) else emptyList() },
        )
    showRoute(repository)
    search("all")
    waitForTag(DestinationCardTestTags.forDestination(paris.stableId))
    composeRule.onNodeWithTag(DestinationCardTestTags.forDestination(paris.stableId)).performClick()
    composeRule
        .onNodeWithTag(DestinationCardTestTags.forDestination(lisbon.stableId))
        .performClick()

    composeRule.onNodeWithTag(DestinationSearchBarTestTags.SEARCH_FIELD).performTextClearance()
    search("No matching city")
    waitForTag(DestinationScreenTestTags.EMPTY)
    composeRule.onNodeWithTag(DestinationScreenTestTags.EMPTY).assertIsDisplayed()
    composeRule.onNodeWithTag(SelectedDestinationsTestTags.removeFor(paris.stableId)).performClick()

    composeRule
        .onNodeWithTag(SelectedDestinationsTestTags.removeFor(paris.stableId))
        .assertDoesNotExist()
    composeRule
        .onNodeWithTag(SelectedDestinationsTestTags.removeFor(lisbon.stableId))
        .assertIsDisplayed()
    composeRule.onNodeWithText("Continue · 1 stop").assertIsDisplayed()
  }

  private fun showRoute(repository: DestinationRepository) {
    val trips = FakeTripRepository()
    val savedStateHandle = SavedStateHandle()
    composeRule.setContent {
      RoamigoTheme {
        CreateTripRoute(
            tripRepository = trips,
            destinationRepository = repository,
            savedStateHandle = savedStateHandle,
            onClose = {},
            onSignIn = {},
            onTripCreated = { _, _ -> },
        )
      }
    }
  }

  private fun showScreen(state: CreateTripUiState) {
    composeRule.setContent {
      RoamigoTheme {
        DestinationScreen(
            uiState = state,
            onBack = {},
            onClose = {},
            onQueryChange = {},
            onToggleDestination = {},
            onRemoveDestination = {},
            onContinue = {},
        )
      }
    }
  }

  private fun search(query: String) {
    composeRule.onNodeWithTag(DestinationSearchBarTestTags.SEARCH_FIELD).performTextInput(query)
    composeRule.mainClock.advanceTimeBy(300)
    composeRule.waitForIdle()
  }

  private fun waitForTag(tag: String) {
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun destination(
      stableId: String,
      name: String = "Sample City",
      countryName: String = "Sample Country",
      countryCode: String = "SC",
      geonameId: Int = 1,
  ) =
      Destination(
          stableId = stableId,
          kind = DestinationKind.CITY,
          name = name,
          countryName = countryName,
          countryCode = countryCode,
          latitude = 10.0,
          longitude = 20.0,
          geonameId = geonameId,
      )
}
