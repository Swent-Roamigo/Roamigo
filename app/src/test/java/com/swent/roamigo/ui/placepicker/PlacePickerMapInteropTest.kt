// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.mapbox.maps.MapLoadingErrorType
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlacePickerMapInteropTest {
  @Test
  fun styleAndSourceFailuresReportLoadFailureButPartialResourceErrorsDoNot() {
    assertTrue(MapLoadingErrorType.STYLE.blocksPlacePickerMap())
    assertTrue(MapLoadingErrorType.SOURCE.blocksPlacePickerMap())
    listOf(
            MapLoadingErrorType.TILE,
            MapLoadingErrorType.GLYPHS,
            MapLoadingErrorType.SPRITE,
        )
        .forEach {
          assertFalse("$it can fail without preventing map interaction", it.blocksPlacePickerMap())
        }
  }

  @Test
  fun detachedMapAcceptsGestureStartAndReleaseWithoutConsumingNativeEvents() {
    // Android can deliver input while navigation is detaching the native view from its parent.
    val map = View(ApplicationProvider.getApplicationContext<Context>())
    assertNull(map.parent)
    listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP).forEach { action
      ->
      val event = MotionEvent.obtain(0, 0, action, 0f, 0f, 0)
      try {
        assertFalse(placePickerMapTouchListener.onTouch(map, event))
      } finally {
        event.recycle()
      }
    }
  }

  @Test
  fun nativeTouchOwnsGestureUntilReleaseOrCancellationWithoutConsumingMapboxEvents() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val requests = mutableListOf<Boolean>()
    val parent =
        object : FrameLayout(context) {
          override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
            requests += disallowIntercept
            super.requestDisallowInterceptTouchEvent(disallowIntercept)
          }
        }
    val map = View(context)
    parent.addView(map)
    listOf(MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL).forEach { ending ->
      requests.clear()
      listOf(
              MotionEvent.ACTION_DOWN,
              MotionEvent.ACTION_MOVE,
              MotionEvent.ACTION_POINTER_DOWN,
              MotionEvent.ACTION_POINTER_UP,
              ending,
          )
          .forEach { action ->
            val event = MotionEvent.obtain(0, 0, action, 0f, 0f, 0)
            try {
              assertFalse(placePickerMapTouchListener.onTouch(map, event))
            } finally {
              event.recycle()
            }
          }
      assertEquals(listOf(true, false), requests)
    }
    // Removing the map during navigation must also tolerate a detached view receiving cancellation.
    parent.removeView(map)
    val cancel = MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
    try {
      assertFalse(placePickerMapTouchListener.onTouch(map, cancel))
    } finally {
      cancel.recycle()
    }
  }
}
