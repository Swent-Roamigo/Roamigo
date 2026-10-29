// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.destination.Destination
import com.swent.roamigo.model.destination.DestinationKind
import com.swent.roamigo.model.destination.DestinationPhoto
import com.swent.roamigo.model.destination.FakeDestinationRepository
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.components.DestinationCardTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationScreenTestTags
import com.swent.roamigo.ui.trip.creation.components.SelectedDestinationsTestTags
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
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
    val response = CompletableDeferred<List<Destination>>()
    val repository = FakeDestinationRepository(search = { response.await() })
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = repository,
            requestDispatcher = Dispatchers.IO,
        )
    show(viewModel)

    viewModel.updateDestinationQuery("Paris")
    composeRule.mainClock.advanceTimeBy(300)
    composeRule.waitForIdle()
    composeRule.waitUntil(5_000) {
      viewModel.uiState.value.destinationSearchStatus == DestinationSearchStatus.LOADING
    }
    composeRule.onNodeWithTag(DestinationScreenTestTags.LOADING).assertIsDisplayed()

    response.complete(listOf(destination("paris-fr", "Paris", "France", "FR", 2988507)))
    composeRule.waitForIdle()
    composeRule.waitUntil(5_000) {
      viewModel.uiState.value.destinationSearchStatus == DestinationSearchStatus.RESULTS
    }
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
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = repository,
            requestDispatcher = Dispatchers.IO,
        )
    show(viewModel)

    viewModel.updateDestinationQuery("Paris")
    composeRule.mainClock.advanceTimeBy(300)
    composeRule.waitForIdle()
    composeRule.waitUntil(5_000) {
      viewModel.uiState.value.destinationSearchStatus == DestinationSearchStatus.ERROR
    }
    composeRule.onNodeWithTag(DestinationScreenTestTags.ERROR).assertIsDisplayed()

    shouldFail = false
    composeRule.onNodeWithTag(DestinationScreenTestTags.RETRY).performClick()
    composeRule.waitUntil(5_000) {
      viewModel.uiState.value.destinationSearchStatus == DestinationSearchStatus.RESULTS
    }

    composeRule
        .onNodeWithTag(DestinationCardTestTags.forDestination(paris.stableId))
        .assertIsDisplayed()
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
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = repository,
            requestDispatcher = Dispatchers.IO,
        )
    show(viewModel)

    viewModel.updateDestinationQuery("City")
    composeRule.mainClock.advanceTimeBy(300)
    composeRule.waitForIdle()
    composeRule.waitUntil(5_000) {
      repository.photoRequests.any { it.stableId == destinations.first().stableId }
    }
    composeRule.waitUntil(5_000) {
      composeRule
          .onAllNodesWithTag(DestinationCardTestTags.photoCreditsFor(destinations.first().stableId))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }

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
    assertEquals(emptyList<Destination>(), viewModel.uiState.value.selectedDestinations)
    assertFalse(repository.photoRequests.any { it.stableId == destinations.last().stableId })
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
    val repository = FakeDestinationRepository(search = { listOf(city) }, photo = { photo })
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = repository,
            requestDispatcher = Dispatchers.IO,
        )
    show(viewModel)
    viewModel.updateDestinationQuery("Paris")
    composeRule.waitUntil(5_000) {
      composeRule
          .onAllNodesWithTag(DestinationCardTestTags.photoCreditsFor(city.stableId))
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
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
    val repository = FakeDestinationRepository(search = { listOf(city) }, photo = { photo })
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = repository,
            requestDispatcher = Dispatchers.IO,
        )
    show(viewModel)

    viewModel.updateDestinationQuery("Paris")
    composeRule.waitUntil(5_000) {
      viewModel.uiState.value.photosByDestinationId.containsKey(city.stableId)
    }
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
    val repository = FakeDestinationRepository(search = { listOf(city) }, photo = { photo })
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = repository,
            requestDispatcher = Dispatchers.IO,
        )
    show(viewModel)

    viewModel.updateDestinationQuery("Lisbon")
    composeRule.waitUntil(5_000) {
      viewModel.uiState.value.photosByDestinationId.containsKey(city.stableId)
    }
    composeRule.onNodeWithTag(DestinationCardTestTags.photoCreditsFor(city.stableId)).performClick()

    composeRule.onNodeWithText("Public domain").assertIsDisplayed()
    composeRule.onNodeWithText("Photo: Public domain").assertIsDisplayed()
    composeRule.onNodeWithText("Photo: ").assertDoesNotExist()
  }

  @Test
  fun noResultsAndRemovalKeepOtherSelectedDestinationInOrder() {
    val paris = destination("paris-fr", "Paris", "France", "FR", 2988507)
    val lisbon = destination("lisbon-pt", "Lisbon", "Portugal", "PT", 2267057)
    val repository = FakeDestinationRepository(search = { emptyList() })
    val viewModel =
        createTestCreateTripViewModel(
            repository = com.swent.roamigo.model.trip.FakeTripRepository(),
            destinationRepository = repository,
            requestDispatcher = Dispatchers.IO,
        )
    viewModel.toggleDestination(paris)
    viewModel.toggleDestination(lisbon)
    show(viewModel)

    viewModel.updateDestinationQuery("No matching city")
    composeRule.mainClock.advanceTimeBy(300)
    composeRule.waitForIdle()
    composeRule.waitUntil(5_000) {
      viewModel.uiState.value.destinationSearchStatus == DestinationSearchStatus.EMPTY
    }
    composeRule.onNodeWithTag(DestinationScreenTestTags.EMPTY).assertIsDisplayed()
    composeRule.onNodeWithTag(SelectedDestinationsTestTags.removeFor(paris.stableId)).performClick()

    assertEquals(listOf(lisbon), viewModel.uiState.value.selectedDestinations)
  }

  private fun show(viewModel: CreateTripViewModel) {
    composeRule.setContent {
      RoamigoTheme {
        DestinationScreen(
            createTripViewModel = viewModel,
            onBack = {},
            onClose = {},
            onContinue = {},
        )
      }
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
