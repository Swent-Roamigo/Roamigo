// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.destination.sampleDestination
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.model.trip.FakeTripRepository
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffoldTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationCardTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationSearchBarTestTags
import com.swent.roamigo.ui.trip.creation.components.SelectedDestinations
import com.swent.roamigo.ui.trip.creation.components.SelectedDestinationsTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DestinationSelectionScreenTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun continueOpensDatesAndBackPreservesSelectedDestinations() {
    showCreation()
    composeRule.searchSampleDestinations()
    val barcelonaTag =
        DestinationCardTestTags.forDestination(sampleDestination("Barcelona").stableId)
    composeRule.onNodeWithTag(barcelonaTag).performClick().assertIsSelected()

    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()

    composeRule.onNodeWithTag(DatesTravelersScreenTestTags.SCREEN).assertIsDisplayed()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.BACK).performClick()
    composeRule.onNodeWithTag(barcelonaTag).assertIsSelected()
    composeRule
        .onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION)
        .assertTextEquals("Continue · 1 stop")
  }

  @Test
  fun continueIsDisabledWithoutSelectedDestinations() {
    showScreen { CreateTripUiState() }

    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsNotEnabled()
  }

  @Test
  fun continueLabelUsesSingularAndPluralStopForms() {
    var state by
        mutableStateOf(
            CreateTripUiState(selectedDestinations = listOf(sampleDestination("Lisbon")))
        )
    showScreen { state }

    composeRule
        .onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION)
        .assertTextEquals("Continue · 1 stop")

    composeRule.runOnIdle {
      state = state.copy(selectedDestinations = listOf("Lisbon", "Porto").map(::sampleDestination))
    }
    composeRule
        .onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION)
        .assertTextEquals("Continue · 2 stops")
  }

  @Test
  fun removeButtonsExposeDestinationSpecificContentDescriptions() {
    composeRule.setContent {
      RoamigoTheme {
        SelectedDestinations(
            destinations = listOf("Lisbon", "Porto").map(::sampleDestination),
            onRemove = {},
        )
      }
    }

    composeRule
        .onNodeWithTag(SelectedDestinationsTestTags.removeFor(sampleDestination("Lisbon").stableId))
        .assertContentDescriptionEquals("Remove Lisbon, Portugal")
    composeRule
        .onNodeWithTag(SelectedDestinationsTestTags.removeFor(sampleDestination("Porto").stableId))
        .assertContentDescriptionEquals("Remove Porto, Portugal")
  }

  @Test
  fun searchFiltersDestinationsAndSelectedStopCanBeRemoved() {
    showCreation()
    composeRule.searchSampleDestinations()
    listOf("Lisbon", "Porto").forEach { name ->
      composeRule
          .onNodeWithTag(DestinationCardTestTags.forDestination(sampleDestination(name).stableId))
          .performClick()
    }

    composeRule.onNodeWithTag(DestinationSearchBarTestTags.SEARCH_FIELD).performTextClearance()
    composeRule.onNodeWithTag(DestinationSearchBarTestTags.SEARCH_FIELD).performTextInput("bar")
    val barcelonaTag =
        DestinationCardTestTags.forDestination(sampleDestination("Barcelona").stableId)
    val pragueTag = DestinationCardTestTags.forDestination(sampleDestination("Prague").stableId)
    composeRule.waitUntil(5_000) {
      composeRule.onAllNodesWithTag(barcelonaTag).fetchSemanticsNodes().isNotEmpty() &&
          composeRule.onAllNodesWithTag(pragueTag).fetchSemanticsNodes().isEmpty()
    }
    composeRule.onAllNodesWithTag(barcelonaTag).assertCountEquals(1)
    composeRule.onAllNodesWithTag(pragueTag).assertCountEquals(0)

    composeRule.onNodeWithTag(DestinationSearchBarTestTags.SEARCH_FIELD).performTextClearance()
    composeRule
        .onNodeWithTag(SelectedDestinationsTestTags.removeFor(sampleDestination("Lisbon").stableId))
        .performClick()

    composeRule
        .onNodeWithTag(SelectedDestinationsTestTags.removeFor(sampleDestination("Lisbon").stableId))
        .assertDoesNotExist()
    composeRule
        .onNodeWithTag(SelectedDestinationsTestTags.removeFor(sampleDestination("Porto").stableId))
        .assertIsDisplayed()
    composeRule
        .onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION)
        .assertTextEquals("Continue · 1 stop")
  }

  private fun showCreation() {
    val tripRepository = FakeTripRepository()
    val destinationRepository = sampleDestinationRepository()
    val savedStateHandle = SavedStateHandle()
    composeRule.setContent {
      RoamigoTheme {
        CreateTripRoute(
            tripRepository = tripRepository,
            destinationRepository = destinationRepository,
            savedStateHandle = savedStateHandle,
            onClose = {},
            onSignIn = {},
            onTripCreated = {},
        )
      }
    }
  }

  private fun showScreen(state: () -> CreateTripUiState) {
    composeRule.setContent {
      RoamigoTheme {
        DestinationScreen(
            uiState = state(),
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
}
