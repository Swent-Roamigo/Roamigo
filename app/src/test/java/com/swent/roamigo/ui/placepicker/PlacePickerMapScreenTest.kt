// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
  fun singleMapActionAdaptsToSelectionThroughInitialEmptyAndEveryErrorState() {
    val state = mutableStateOf(PlacePickerUiState())
    var maps = 0
    compose.setContent {
      RoamigoTheme {
        PlacePickerScreen(state.value, {}, {}, {}, {}, {}, onChooseOnMap = { maps++ })
      }
    }
    val states =
        listOf(PlacePickerUiState(), PlacePickerUiState(searchStatus = PlaceSearchStatus.EMPTY)) +
            PlacePickerError.entries.map {
              PlacePickerUiState(searchStatus = PlaceSearchStatus.ERROR, searchError = it)
            }
    listOf(null, original).forEach { selection ->
      states.forEach { input ->
        compose.runOnIdle { state.value = input.copy(selectedLocation = selection) }
        val tag =
            if (selection == null) PlacePickerTestTags.CHOOSE_ON_MAP
            else PlacePickerTestTags.VIEW_ON_MAP
        val other =
            if (selection == null) PlacePickerTestTags.VIEW_ON_MAP
            else PlacePickerTestTags.CHOOSE_ON_MAP
        compose.onNodeWithTag(other).assertDoesNotExist()
        compose
            .onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertTextEquals(if (selection == null) "Choose on map" else "View on map")
            .performClick()
      }
    }
    compose.runOnIdle { assertEquals(states.size * 2, maps) }
  }

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
    compose
        .onNodeWithText("Choose on map")
        .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
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
  fun viewSelectionOnMapBackAndRotationPreserveSearchDraftAndSingleDelivery() {
    val repo = FakePlaceRepository()
    val results = mutableListOf<TripLocation>()
    var cancels = 0
    val content: @Composable () -> Unit = {
      CompositionLocalProvider(LocalInspectionMode provides true) {
        RoamigoTheme {
          PlacePickerRoute(repo, { results += it }, { cancels++ }, initialLocation = original)
        }
      }
    }
    compose.setContent(content)
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).performTextInput("query")
    compose.onNodeWithTag(PlacePickerTestTags.VIEW_ON_MAP).performClick()
    compose
        .onNodeWithTag(PlacePickerTestTags.MAP_NAME)
        .performScrollTo()
        .assertTextContains("Original")
    compose.onNodeWithTag(PlacePickerTestTags.MAP_NAME).performTextReplacement("Unconfirmed")
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.onNodeWithTag(PlacePickerTestTags.SELECTED).assertTextEquals("Selected place: Original")
    compose.onNodeWithTag(PlacePickerTestTags.QUERY).assertTextContains("query")
    compose.onNodeWithTag(PlacePickerTestTags.VIEW_ON_MAP).performClick()
    compose
        .onNodeWithTag(PlacePickerTestTags.MAP_NAME)
        .performScrollTo()
        .performTextReplacement("Edited")
    compose.activityRule.scenario.recreate()
    compose.activityRule.scenario.onActivity { it.setContent(content = content) }
    compose
        .onNodeWithTag(PlacePickerTestTags.MAP_NAME)
        .performScrollTo()
        .assertTextContains("Edited")
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).performClick()
    compose.waitUntil { results.size == 1 }
    compose.activityRule.scenario.recreate()
    compose.activityRule.scenario.onActivity { it.setContent(content = content) }
    compose.runOnIdle {
      assertEquals(listOf(original.copy(name = "Edited")), results)
      assertEquals(0, cancels)
    }
  }

  @Test
  fun nameFieldRequiresPointAndUnfinishedPicker() {
    val state = mutableStateOf(PlacePickerUiState(isMapMode = true))
    compose.setContent { PlacePickerMapScreen(state.value, {}, {}, {}, {}) { Box(it) } }
    compose.onNodeWithTag(PlacePickerTestTags.MAP_NAME).performScrollTo().assertIsNotEnabled()
    compose.runOnIdle {
      state.value = state.value.copy(manualDraft = PlacePickerManualDraft(MapCoordinate(0.0, 0.0)))
    }
    compose.onNodeWithTag(PlacePickerTestTags.MAP_NAME).assertIsEnabled()
    compose.runOnIdle { state.value = state.value.copy(finished = true) }
    compose.onNodeWithTag(PlacePickerTestTags.MAP_NAME).assertIsNotEnabled()
  }

  @Test
  fun nativeViewReceivesBothDragAxesWithoutScrollingTheSurroundingForm() {
    val events = mutableListOf<Int>()
    compose.setContent {
      RoamigoTheme {
        Box(Modifier.requiredSize(360.dp, 420.dp)) {
          PlacePickerMapScreen(PlacePickerUiState(isMapMode = true), {}, {}, {}, {}) { modifier ->
            AndroidView(
                modifier = modifier.testTag("native_map"),
                factory = { context ->
                  object : View(context) {
                        override fun onTouchEvent(event: MotionEvent): Boolean {
                          events += event.actionMasked
                          if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                          return true
                        }

                        override fun performClick(): Boolean = super.performClick()
                      }
                      .apply { setOnTouchListener(placePickerMapTouchListener) }
                },
            )
          }
        }
      }
    }
    val map = compose.onNodeWithTag("native_map").performScrollTo()
    val before = map.fetchSemanticsNode().boundsInWindow
    listOf(false, true).forEach { vertical ->
      compose.runOnIdle { events.clear() }
      map.performTouchInput {
        if (vertical)
            swipe(Offset(width * 0.5f, height * 0.8f), Offset(width * 0.5f, height * 0.2f), 500)
        else swipe(Offset(width * 0.6f, height * 0.5f), Offset(width * 0.4f, height * 0.5f), 500)
      }
      compose.runOnIdle {
        assertTrue(MotionEvent.ACTION_MOVE in events)
        assertFalse(MotionEvent.ACTION_CANCEL in events)
        assertEquals(MotionEvent.ACTION_UP, events.last())
        assertEquals(before, map.fetchSemanticsNode().boundsInWindow)
      }
    }
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
