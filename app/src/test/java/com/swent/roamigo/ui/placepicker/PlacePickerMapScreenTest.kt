// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import androidx.activity.ComponentActivity
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.place.FakePlaceRepository
import com.swent.roamigo.ui.map.MapCoordinate
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlacePickerMapScreenTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val original = TripLocation(46.0, 6.0, "Original", "CH")

  @Test
  fun mapControlsRequirePointAndNameAndConfirmationReturnsTrimmedLocation() {
    val vm = PlacePickerViewModel(FakePlaceRepository(), SavedStateHandle())
    vm.enterMap()
    compose.setContent {
      RoamigoTheme {
        val state by vm.uiState.collectAsState()
        PlacePickerMapScreen(
            state,
            vm::updateMapName,
            vm::backToSearch,
            vm::confirm,
            vm::retryMap,
        ) { modifier ->
          Box(
              modifier
                  .testTag("map_surface")
                  .combinedClickable(
                      onClick = {},
                      onLongClick = { vm.selectMapPoint(0.0, 0.0) },
                  )
          ) {
            if (state.manualDraft.point != null) PlacePickerMapMarker()
          }
        }
      }
    }
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsNotEnabled()
    compose.onNodeWithTag(PlacePickerTestTags.MAP_NAME).performScrollTo().assertIsNotEnabled()
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsNotEnabled()
    compose
        .onNodeWithText("Press and hold anywhere on the map to select a location.")
        .performScrollTo()
        .assertIsDisplayed()
    compose.onNodeWithTag("map_surface").performScrollTo().performTouchInput { click() }
    compose.runOnIdle {
      assertNull(vm.uiState.value.manualDraft.point)
      assertEquals("", vm.uiState.value.manualDraft.name)
    }
    compose.onNodeWithContentDescription("Selected point").assertDoesNotExist()
    compose.onNodeWithTag("map_surface").performTouchInput { longClick() }
    compose.onNodeWithContentDescription("Selected point").assertIsDisplayed()
    compose.onNodeWithTag(PlacePickerTestTags.MAP_NAME).assertIsEnabled()
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsNotEnabled()
    compose
        .onNodeWithTag(PlacePickerTestTags.MAP_NAME)
        .performScrollTo()
        .performTextReplacement("   ")
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsNotEnabled()
    compose.onNodeWithTag(PlacePickerTestTags.MAP_NAME).performTextReplacement("  Meeting point  ")
    compose
        .onNodeWithTag(PlacePickerTestTags.CONFIRM)
        .assertTextEquals("Confirm Meeting point")
        .assertIsEnabled()
        .performClick()
    compose.runOnIdle {
      assertEquals(TripLocation(0.0, 0.0, "Meeting point"), vm.consumeResult())
      assertNull(vm.consumeResult())
    }
  }

  @Test
  fun mapErrorKeepsDraftAndOffersRetryAndBackWithoutSearchRetry() {
    val vm = PlacePickerViewModel(FakePlaceRepository(), SavedStateHandle(), original)
    vm.enterMap()
    vm.mapLoadingFailed()
    var backs = 0
    compose.setContent {
      val state by vm.uiState.collectAsState()
      PlacePickerMapScreen(
          state,
          vm::updateMapName,
          {
            backs++
            vm.backToSearch()
          },
          vm::confirm,
          vm::retryMap,
      ) {
        Box(it)
      }
    }
    compose.onNodeWithTag(PlacePickerTestTags.MAP_ERROR).performScrollTo().assertIsDisplayed()
    compose
        .onNodeWithTag(PlacePickerTestTags.MAP_NAME)
        .performScrollTo()
        .assertTextContains("Original")
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsEnabled()
    compose.onNodeWithTag(PlacePickerTestTags.MAP_RETRY).performScrollTo().performClick()
    compose.onNodeWithTag(PlacePickerTestTags.MAP_ERROR).assertDoesNotExist()
    compose.onNodeWithTag(PlacePickerTestTags.MAP_BACK).performScrollTo().performClick()
    compose.runOnIdle {
      assertEquals(1, backs)
      assertEquals(1, vm.uiState.value.mapLoadAttempt)
      assertEquals(original, vm.uiState.value.selectedLocation)
      assertEquals(original, vm.uiState.value.manualDraft.location)
    }
  }

  @Test
  fun completedMapCannotRetryLoadingOrDeliverAgain() {
    val vm = PlacePickerViewModel(FakePlaceRepository(), SavedStateHandle(), original)
    vm.enterMap()
    vm.mapLoadingFailed()
    compose.setContent {
      val state by vm.uiState.collectAsState()
      PlacePickerMapScreen(state, vm::updateMapName, vm::backToSearch, vm::confirm, vm::retryMap) {
        Box(it)
      }
    }
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).performClick()
    compose.onNodeWithTag(PlacePickerTestTags.MAP_RETRY).performScrollTo().assertIsNotEnabled()
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsNotEnabled()
    compose.runOnIdle {
      assertEquals(original, vm.consumeResult())
      assertNull(vm.consumeResult())
    }
  }

  @Test
  fun nameFieldRequiresValidPointAndUnfinishedPicker() {
    val state = mutableStateOf(PlacePickerUiState(isMapMode = true))
    compose.setContent { PlacePickerMapScreen(state.value, {}, {}, {}, {}) { Box(it) } }
    listOf(
            null,
            MapCoordinate(Double.NaN, 0.0),
            MapCoordinate(0.0, Double.POSITIVE_INFINITY),
            MapCoordinate(91.0, 0.0),
            MapCoordinate(0.0, 181.0),
        )
        .forEach { point ->
          compose.runOnIdle {
            state.value = state.value.copy(manualDraft = PlacePickerManualDraft(point))
          }
          compose.onNodeWithTag(PlacePickerTestTags.MAP_NAME).performScrollTo().assertIsNotEnabled()
        }
    compose.runOnIdle {
      state.value = state.value.copy(manualDraft = PlacePickerManualDraft(MapCoordinate(0.0, 0.0)))
    }
    compose.onNodeWithTag(PlacePickerTestTags.MAP_NAME).assertIsEnabled()
    compose.runOnIdle { state.value = state.value.copy(finished = true) }
    compose.onNodeWithTag(PlacePickerTestTags.MAP_NAME).assertIsNotEnabled()
  }

  @Test fun compactPortraitKeepsNameAndConfirmationReachable() = compactLayout(320, 420, 1f)

  @Test fun landscapeKeepsNameAndConfirmationReachable() = compactLayout(640, 280, 1f)

  @Test fun largeTextAndKeyboardSizedViewportKeepControlsReachable() = compactLayout(320, 240, 1.8f)

  private fun compactLayout(width: Int, height: Int, fontScale: Float) {
    val vm = PlacePickerViewModel(FakePlaceRepository(), SavedStateHandle(), original)
    vm.enterMap()
    compose.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        RoamigoTheme {
          Box(Modifier.requiredSize(width.dp, height.dp)) {
            val state by vm.uiState.collectAsState()
            PlacePickerMapScreen(
                state,
                vm::updateMapName,
                vm::backToSearch,
                vm::confirm,
                vm::retryMap,
            ) {
              Box(it)
            }
          }
        }
      }
    }
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsDisplayed().assertIsEnabled()
    compose
        .onNodeWithTag(PlacePickerTestTags.MAP_NAME)
        .performScrollTo()
        .performTextReplacement("Edited")
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsDisplayed().performClick()
    compose.runOnIdle { assertEquals(original.copy(name = "Edited"), vm.consumeResult()) }
  }
}
