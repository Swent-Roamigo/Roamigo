// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.trip.creation.components.CreateTripScaffoldTestTags
import com.swent.roamigo.ui.trip.creation.components.DestinationCardTestTags
import com.swent.roamigo.ui.trip.creation.components.SelectedDestinationsTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DestinationScreenTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun injectedSharedViewModelKeepsSelectedDestinationsAndContinueIsAnEvent() {
    val viewModel = CreateTripViewModel()
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

    composeRule.onNodeWithTag(DestinationCardTestTags.forDestination("Barcelona")).performClick()
    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).performClick()

    composeRule.runOnIdle {
      assertTrue(continued)
      assertEquals(
          listOf("Lisbon", "Porto", "Barcelona"),
          viewModel.uiState.value.selectedDestinations,
      )
    }
  }

  @Test
  fun continueIsDisabledWhenRouteIsEmpty() {
    composeRule.setContent {
      RoamigoTheme {
        DestinationScreenContent(
            uiState = CreateTripUiState(selectedDestinations = emptyList()),
            onBack = {},
            onClose = {},
            onQueryChange = {},
            onToggleDestination = {},
            onRemoveDestination = {},
            onContinue = {},
        )
      }
    }

    composeRule.onNodeWithTag(CreateTripScaffoldTestTags.PRIMARY_ACTION).assertIsNotEnabled()
  }

  @Test
  fun searchFiltersDestinationsAndSelectedStopCanBeRemoved() {
    val viewModel = CreateTripViewModel()

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

    composeRule.onNodeWithContentDescription("Search destinations").performTextInput("bar")
    composeRule.runOnIdle {
      assertEquals(
          1,
          composeRule
              .onAllNodesWithTag(DestinationCardTestTags.forDestination("Barcelona"))
              .fetchSemanticsNodes()
              .size,
      )
      assertEquals(
          0,
          composeRule
              .onAllNodesWithTag(DestinationCardTestTags.forDestination("Prague"))
              .fetchSemanticsNodes()
              .size,
      )
    }

    composeRule.onNodeWithContentDescription("Search destinations").performTextClearance()
    composeRule.onNodeWithTag(SelectedDestinationsTestTags.removeFor("Lisbon")).performClick()

    composeRule.runOnIdle {
      assertEquals(listOf("Porto"), viewModel.uiState.value.selectedDestinations)
    }
  }
}
