// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mapbox.android.gestures.MoveGestureDetector
import com.mapbox.geojson.Point
import com.mapbox.maps.ScreenCoordinate
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.OnMoveListener
import com.mapbox.maps.plugin.gestures.gestures
import com.swent.roamigo.model.place.FakePlaceRepository
import com.swent.roamigo.model.place.PlaceProximity
import com.swent.roamigo.ui.map.MAP_TEST_TIMEOUT_MILLIS
import com.swent.roamigo.ui.map.findMapView
import com.swent.roamigo.ui.map.loadLocalMapStyle
import com.swent.roamigo.ui.theme.RoamigoTheme
import kotlin.math.abs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises native hit testing and rendered marker replacement with no remote styles or tiles. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class PlacePickerMapRenderedTest {
  @get:Rule
  val compose =
      createAndroidComposeRule<ComponentActivity>(effectContext = UnconfinedTestDispatcher())

  @Test
  fun longPressesReplaceMarkerAndNormalTapsNeverChangeSelection() {
    val vm =
        PlacePickerViewModel(
            FakePlaceRepository(),
            SavedStateHandle(),
            proximity = PlaceProximity(46.52, 6.63),
        )
    vm.enterMap()
    compose.setContent {
      RoamigoTheme {
        val state by vm.uiState.collectAsState()
        // Ensure the outer form can actually scroll, so vertical gesture interception is tested.
        Box(Modifier.requiredSize(360.dp, 420.dp)) {
          PlacePickerMapScreen(
              state,
              vm::updateMapName,
              vm::backToSearch,
              vm::confirm,
              vm::retryMap,
          ) { modifier ->
            PlacePickerMap(
                state,
                vm::selectMapPoint,
                vm::mapLoadingFailed,
                vm::mapLoaded,
                modifier,
                style = """{"version":8,"sources":{},"layers":[]}""",
            )
          }
        }
      }
    }
    compose.loadLocalMapStyle()
    compose.runOnIdle {
      // Eliminate fling timing from camera comparisons while preserving actual drag gestures.
      compose.findMapView().gestures.updateSettings { scrollDecelerationEnabled = false }
    }
    assertNormalTapKeepsDraft(vm)
    compose.onAllNodesWithTag(PlacePickerTestTags.MAP_MARKER).assertCountEquals(0)
    compose.waitUntil(MAP_TEST_TIMEOUT_MILLIS) {
      compose.runOnIdle {
        val camera = compose.findMapView().mapboxMap.cameraState
        abs(camera.center.latitude() - 46.52) < 0.00001 &&
            abs(camera.center.longitude() - 6.63) < 0.00001 &&
            abs(camera.zoom - 12.0) < 0.00001
      }
    }
    // Both axes must reach Mapbox without moving the surrounding form.
    panMap(vertical = false)
    panMap(vertical = true)
    val cameraBefore = compose.runOnIdle { compose.findMapView().mapboxMap.cameraState }
    listOf(0.35f to 0.4f, 0.65f to 0.6f).forEach { (x, y) ->
      val expected = compose.runOnIdle {
        val map = compose.findMapView()
        map.mapboxMap.coordinateForPixel(
            ScreenCoordinate(map.width * x.toDouble(), map.height * y.toDouble())
        )
      }
      nativeGesture(Offset(x, y), duration = ViewConfiguration.getLongPressTimeout() + 100L)
      compose.waitUntil(MAP_TEST_TIMEOUT_MILLIS) {
        val point = vm.uiState.value.manualDraft.point
        point != null &&
            abs(point.latitude - expected.latitude()) < 0.00001 &&
            abs(point.longitude - expected.longitude()) < 0.00001
      }
      assertMarkerAt(expected)
      compose.runOnIdle {
        val after = compose.findMapView().mapboxMap.cameraState
        assertEquals(cameraBefore.center.latitude(), after.center.latitude(), 0.0000001)
        assertEquals(cameraBefore.center.longitude(), after.center.longitude(), 0.0000001)
        assertEquals(cameraBefore.zoom, after.zoom, 0.00001)
        assertEquals("", vm.uiState.value.manualDraft.name)
        vm.updateMapName("Named point")
      }
      assertNormalTapKeepsDraft(vm)
      assertMarkerAt(expected)
    }
    val selected = vm.uiState.value.manualDraft
    val point = selected.point!!
    listOf(false, true).forEach { vertical ->
      panMap(vertical)
      assertMarkerAt(Point.fromLngLat(point.longitude, point.latitude))
      compose.runOnIdle { assertEquals(selected, vm.uiState.value.manualDraft) }
    }
    compose
        .onNodeWithTag(PlacePickerTestTags.MAP_NAME)
        .performScrollTo()
        .performTextReplacement("Meeting point")
    compose.onNodeWithTag(PlacePickerTestTags.CONFIRM).assertIsEnabled().performClick()
    compose.runOnIdle { assertEquals("Meeting point", vm.consumeResult()!!.name) }
  }

  private fun panMap(vertical: Boolean) {
    val map = compose.onNodeWithTag(PlacePickerTestTags.MAP).performScrollTo()
    val bounds = map.fetchSemanticsNode().boundsInWindow
    val before = compose.runOnIdle { compose.findMapView().mapboxMap.cameraState.center }
    compose.runOnIdle {
      assertTrue("Map scrolling must be enabled", compose.findMapView().gestures.scrollEnabled)
    }
    val callbacks = IntArray(3)
    val listener =
        object : OnMoveListener {
          override fun onMoveBegin(detector: MoveGestureDetector) {
            callbacks[0]++
          }

          override fun onMove(detector: MoveGestureDetector): Boolean {
            callbacks[1]++
            return false
          }

          override fun onMoveEnd(detector: MoveGestureDetector) {
            callbacks[2]++
          }
        }
    compose.runOnIdle { compose.findMapView().gestures.addOnMoveListener(listener) }
    var after = before
    var movementDetected = false
    try {
      if (vertical) nativeGesture(Offset(0.25f, 0.65f), Offset(0.25f, 0.45f), 500L)
      else nativeGesture(Offset(0.6f, 0.5f), Offset(0.4f, 0.5f), 500L)
      // Native camera updates are asynchronous to Compose. Require real movement on the dragged
      // axis and a quiet interval before comparing the rendered marker and camera positions.
      var stableCenter = before
      var stableSince = SystemClock.uptimeMillis()
      compose.waitUntil(MAP_TEST_TIMEOUT_MILLIS) {
        after = compose.runOnIdle { compose.findMapView().mapboxMap.cameraState.center }
        val now = SystemClock.uptimeMillis()
        val moved =
            if (vertical) abs(after.latitude() - before.latitude()) > 0.00001
            else abs(after.longitude() - before.longitude()) > 0.00001
        movementDetected = movementDetected || moved
        if (
            abs(after.latitude() - stableCenter.latitude()) > 0.0000001 ||
                abs(after.longitude() - stableCenter.longitude()) > 0.0000001
        ) {
          stableCenter = after
          stableSince = now
        }
        moved && now - stableSince >= 100L
      }
      compose.runOnIdle { assertTrue("Missing native move callbacks", callbacks.all { it > 0 }) }
    } catch (failure: Throwable) {
      throw AssertionError(
          panDiagnostics(vertical, before, after, movementDetected, callbacks),
          failure,
      )
    } finally {
      Log.i(
          "PlacePickerMapTest",
          panDiagnostics(vertical, before, after, movementDetected, callbacks),
      )
      compose.runOnIdle { compose.findMapView().gestures.removeOnMoveListener(listener) }
    }
    assertEquals(
        "The form must not scroll during a map drag",
        bounds,
        map.fetchSemanticsNode().boundsInWindow,
    )
  }

  private fun assertNormalTapKeepsDraft(vm: PlacePickerViewModel) {
    val before = vm.uiState.value.manualDraft
    var clicked = false
    val listener = OnMapClickListener {
      clicked = true
      false
    }
    compose.runOnIdle { compose.findMapView().gestures.addOnMapClickListener(listener) }
    try {
      nativeGesture(Offset(0.8f, 0.3f))
      // Require the native single-tap callback, so a gesture that never reaches Mapbox cannot pass.
      compose.waitUntil(MAP_TEST_TIMEOUT_MILLIS) { compose.runOnIdle { clicked } }
      compose.runOnIdle { assertEquals(before, vm.uiState.value.manualDraft) }
    } finally {
      compose.runOnIdle { compose.findMapView().gestures.removeOnMapClickListener(listener) }
    }
  }

  private fun panDiagnostics(
      vertical: Boolean,
      before: Point,
      after: Point,
      moved: Boolean,
      callbacks: IntArray,
  ): String = compose.runOnIdle {
    val map = compose.findMapView()
    val visible = Rect()
    map.getLocalVisibleRect(visible)
    "pan vertical=$vertical before=$before after=$after moved=$moved " +
        "native begin/move/end=${callbacks.toList()} size=${map.width}x${map.height} " +
        "visible=$visible camera=${map.mapboxMap.cameraState} " +
        "scrollEnabled=${map.gestures.scrollEnabled} scrollMode=${map.gestures.scrollMode}"
  }

  /**
   * Inject through Android's input dispatcher in screen coordinates, paced by real uptime. Compose
   * batches its events on the UI thread and advances only its test clock; Mapbox's native drag
   * interactions and Android long-press detection need the platform event loop to run.
   */
  private fun nativeGesture(start: Offset, end: Offset = start, duration: Long = 50L) {
    compose.onNodeWithTag(PlacePickerTestTags.MAP).performScrollTo()
    val (from, to) =
        compose.runOnIdle {
          val map = compose.findMapView()
          val origin = IntArray(2).also(map::getLocationOnScreen)
          val visible = Rect()
          assertTrue("Map must be visible", map.getLocalVisibleRect(visible))
          assertTrue("Map must have positive dimensions", map.width > 0 && map.height > 0)
          fun screenPoint(fraction: Offset): Offset {
            val local = Offset(map.width * fraction.x, map.height * fraction.y)
            assertTrue(
                "Gesture point $local outside visible map $visible",
                visible.contains(local.x.toInt(), local.y.toInt()),
            )
            return local + Offset(origin[0].toFloat(), origin[1].toFloat())
          }
          screenPoint(start) to screenPoint(end)
        }
    Log.i("PlacePickerMapTest", "Native gesture screenFrom=$from screenTo=$to duration=$duration")
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    val downTime = SystemClock.uptimeMillis()
    fun inject(action: Int, point: Offset) {
      val properties =
          MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
          }
      val coordinates =
          MotionEvent.PointerCoords().apply {
            x = point.x
            y = point.y
            pressure = 1f
            size = 1f
          }
      val event =
          MotionEvent.obtain(
              downTime,
              SystemClock.uptimeMillis(),
              action,
              1,
              arrayOf(properties),
              arrayOf(coordinates),
              0,
              0,
              1f,
              1f,
              0,
              0,
              InputDevice.SOURCE_TOUCHSCREEN,
              0,
          )
      try {
        assertTrue(
            "Native input injection failed: $event",
            automation.injectInputEvent(event, true),
        )
      } finally {
        event.recycle()
      }
    }
    inject(MotionEvent.ACTION_DOWN, from)
    var released = false
    try {
      val steps = if (start == end) 1 else 32
      for (step in 1..steps) {
        val remaining = downTime + duration * step / steps - SystemClock.uptimeMillis()
        if (remaining > 0) SystemClock.sleep(remaining)
        if (start != end)
            inject(MotionEvent.ACTION_MOVE, from + (to - from) * (step.toFloat() / steps))
      }
      inject(MotionEvent.ACTION_UP, to)
      released = true
    } finally {
      if (!released) inject(MotionEvent.ACTION_CANCEL, to)
    }
  }

  private fun assertMarkerAt(expected: Point) {
    compose.waitUntil(MAP_TEST_TIMEOUT_MILLIS) {
      val markers = compose.onAllNodesWithTag(PlacePickerTestTags.MAP_MARKER).fetchSemanticsNodes()
      if (markers.size != 1) false
      else {
        val pixel = compose.runOnIdle {
          val map = compose.findMapView()
          val projected = map.mapboxMap.pixelForCoordinate(expected)
          val origin = IntArray(2).also(map::getLocationInWindow)
          Offset(origin[0] + projected.x.toFloat(), origin[1] + projected.y.toFloat())
        }
        val bounds = markers.single().boundsInWindow
        abs(bounds.center.x - pixel.x) < 4f && abs(bounds.bottom - pixel.y) < 4f
      }
    }
    compose.onAllNodesWithTag(PlacePickerTestTags.MAP_MARKER).assertCountEquals(1)
  }
}
