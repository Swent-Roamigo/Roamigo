package com.swent.roamigo.ui.heatmap

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class TravelHeatmapScreenTest {

  @get:Rule val composeRule = createComposeRule()

  @Test
  fun previewDisplaysMapAndLegendWithoutStatistics() {
    composeRule.setContent {
      RoamigoTheme {
        TravelHeatmapPreview(
            uiState = TravelHeatmapUiState.Success(mapOf("PT" to 3)),
            onClick = {},
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).assertIsDisplayed()

    composeRule.onNodeWithText("Less").assertIsDisplayed()
    composeRule.onNodeWithText("More visits").assertIsDisplayed()
    composeRule.onNodeWithText("1 country").assertDoesNotExist()
    composeRule.onNodeWithText("Explored world").assertDoesNotExist()
  }

  @Test
  fun previewForwardsClickExactlyOnce() {
    var clicks = 0

    composeRule.setContent {
      RoamigoTheme {
        TravelHeatmapPreview(
            uiState = TravelHeatmapUiState.Success(mapOf("FR" to 2)),
            onClick = { clicks++ },
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun emptyPreviewStillShowsMap() {
    composeRule.setContent {
      RoamigoTheme {
        TravelHeatmapPreview(
            uiState = TravelHeatmapUiState.Success(emptyMap()),
            onClick = {},
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).assertIsDisplayed()
  }

  @Test
  fun loadingPreviewStillShowsMap() {
    composeRule.setContent {
      RoamigoTheme {
        TravelHeatmapPreview(
            uiState = TravelHeatmapUiState.Loading,
            onClick = {},
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.trip_history_world_map).assertIsDisplayed()
  }

  @Test
  fun previewErrorDisplaysFeedback() {
    composeRule.setContent {
      RoamigoTheme {
        TravelHeatmapPreview(
            uiState = TravelHeatmapUiState.Error,
            onClick = {},
        )
      }
    }

    composeRule
        .onNodeWithText(
            "Explored world unavailable",
            substring = true,
            useUnmergedTree = true,
        )
        .assertIsDisplayed()
  }

  @Test
  fun exploredWorldDisplaysCountsAndCountries() {
    composeRule.setContent {
      RoamigoTheme {
        ExploredWorldScreen(
            uiState = TravelHeatmapUiState.Success(mapOf("PT" to 3, "ES" to 2)),
            completedTripCount = 4,
            onBackClick = {},
        )
      }
    }

    composeRule.onNodeWithText("Explored world").assertIsDisplayed()
    composeRule.onNodeWithText("2 countries · 4 trips").assertIsDisplayed()

    composeRule.onNodeWithTag(C.Tag.heatmap_country_prefix + "PT").assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.heatmap_country_prefix + "ES").assertIsDisplayed()

    composeRule.onNodeWithText("3 trips").assertIsDisplayed()
    composeRule.onNodeWithText("2 trips").assertIsDisplayed()
  }

  @Test
  fun exploredWorldDisplaysEmptyState() {
    composeRule.setContent {
      RoamigoTheme {
        ExploredWorldScreen(
            uiState = TravelHeatmapUiState.Success(emptyMap()),
            completedTripCount = 0,
            onBackClick = {},
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.heatmap_empty).assertIsDisplayed()
    composeRule.onNodeWithText("0 countries · 0 trips").assertIsDisplayed()
  }

  @Test
  fun exploredWorldDisplaysLoadingState() {
    composeRule.setContent {
      RoamigoTheme {
        ExploredWorldScreen(
            uiState = TravelHeatmapUiState.Loading,
            completedTripCount = null,
            onBackClick = {},
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.heatmap_loading).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.heatmap_empty).assertDoesNotExist()
  }

  @Test
  fun exploredWorldDisplaysErrorState() {
    composeRule.setContent {
      RoamigoTheme {
        ExploredWorldScreen(
            uiState = TravelHeatmapUiState.Error,
            completedTripCount = null,
            onBackClick = {},
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.heatmap_error).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.heatmap_empty).assertDoesNotExist()
  }

  @Test
  fun backButtonForwardsClick() {
    var clicks = 0

    composeRule.setContent {
      RoamigoTheme {
        ExploredWorldScreen(
            uiState = TravelHeatmapUiState.Success(mapOf("IT" to 1)),
            completedTripCount = 1,
            onBackClick = { clicks++ },
        )
      }
    }

    composeRule.onNodeWithTag(C.Tag.heatmap_back).performClick()

    assertEquals(1, clicks)
  }

  @Test
  fun missingTripCountDoesNotInventStatistics() {
    composeRule.setContent {
      RoamigoTheme {
        ExploredWorldScreen(
            uiState = TravelHeatmapUiState.Success(mapOf("PT" to 1)),
            completedTripCount = null,
            onBackClick = {},
        )
      }
    }

    composeRule.onNodeWithText("1 country · 0 trips").assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.heatmap_country_prefix + "PT").assertIsDisplayed()
  }

  @Test
  fun countryNamesHaveSafeFallbacks() {
    assertEquals("Portugal", countryName("PT"))
    assertEquals("Spain", countryName("ES"))
    assertEquals("P", countryName("P"))
    assertEquals("pt", countryName("pt"))
  }
}
