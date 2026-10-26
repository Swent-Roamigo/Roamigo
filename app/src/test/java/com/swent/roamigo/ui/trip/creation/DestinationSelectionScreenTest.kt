// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation

import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.destination.sampleDestination
import com.swent.roamigo.model.destination.sampleDestinationRepository
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffoldTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationCardTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationSearchBarTestTags
import com.swent.roamigo.ui.trip.creation.components.SelectedDestinations
import com.swent.roamigo.ui.trip.creation.components.SelectedDestinationsTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DestinationSelectionScreenTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun injectedSharedViewModelKeepsSelectedDestinationsAndContinueIsAnEvent() {
    val viewModel = CreateTripViewModel(destinationRepository = sampleDestinationRepository())
    var continued = false

    composeRule.setContent {
      RoamigoTheme {
        DestinationScreen(
            createTripViewModel = viewModel,
            onBack = {},
            onClose = {},
            onContinue = { continued = true },
        )
      }
    }

    composeRule.searchSampleDestinations()
    composeRule
        .onNodeWithTag(
            DestinationCardTestTags.forDestination(sampleDestination("Barcelona").stableId)
        )
        .performClick()
    composeRule
        .onNodeWithTag(
            DestinationCardTestTags.forDestination(sampleDestination("Barcelona").stableId)
        )
        .assertIsSelected()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()

    composeRule.runOnIdle {
      assertTrue(continued)
      assertEquals(
          listOf(sampleDestination("Barcelona")),
          viewModel.uiState.value.selectedDestinations,
      )
    }
  }

  @Test
  fun continueIsDisabledWhenRouteIsEmpty() {
    val viewModel = CreateTripViewModel(destinationRepository = sampleDestinationRepository())

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

    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsNotEnabled()
  }

  @Test
  fun continueLabelUsesSingularAndPluralStopForms() {
    val viewModel = CreateTripViewModel(destinationRepository = sampleDestinationRepository())

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

    viewModel.toggleDestination(sampleDestination("Lisbon"))
    composeRule
        .onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION)
        .assertTextEquals("Continue · 1 stop")

    viewModel.toggleDestination(sampleDestination("Porto"))
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
    val viewModel = CreateTripViewModel(destinationRepository = sampleDestinationRepository())
    viewModel.toggleDestination(sampleDestination("Lisbon"))
    viewModel.toggleDestination(sampleDestination("Porto"))

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

    composeRule.onNodeWithTag(DestinationSearchBarTestTags.SEARCH_FIELD).performTextInput("bar")
    composeRule.waitUntil(5_000) {
      composeRule
          .onAllNodesWithTag(
              DestinationCardTestTags.forDestination(sampleDestination("Barcelona").stableId)
          )
          .fetchSemanticsNodes()
          .isNotEmpty()
    }
    composeRule.runOnIdle {
      assertEquals(
          1,
          composeRule
              .onAllNodesWithTag(
                  DestinationCardTestTags.forDestination(sampleDestination("Barcelona").stableId)
              )
              .fetchSemanticsNodes()
              .size,
      )
      assertEquals(
          0,
          composeRule
              .onAllNodesWithTag(
                  DestinationCardTestTags.forDestination(sampleDestination("Prague").stableId)
              )
              .fetchSemanticsNodes()
              .size,
      )
    }

    composeRule.onNodeWithTag(DestinationSearchBarTestTags.SEARCH_FIELD).performTextClearance()
    composeRule
        .onNodeWithTag(SelectedDestinationsTestTags.removeFor(sampleDestination("Lisbon").stableId))
        .performClick()

    composeRule.runOnIdle {
      assertEquals(listOf(sampleDestination("Porto")), viewModel.uiState.value.selectedDestinations)
    }
  }
}
