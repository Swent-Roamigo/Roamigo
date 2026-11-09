// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.content.res.Resources
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationFreshnessTest {
  @get:Rule val composeRule = createComposeRule()

  // Robolectric does not control System.currentTimeMillis(), so the tests inject the time.
  private var clockMillis = NOW
  private val isTicking = mutableStateOf(false)

  private val resources: Resources
    get() = RuntimeEnvironment.getApplication().resources

  private fun freshnessForAge(ageMillis: Long): String =
      formatLocationFreshness(
          updatedAtMillis = NOW - ageMillis,
          nowMillis = NOW,
          resources = resources,
      )

  @Test
  fun freshnessIsLiveThenInWholeSecondsThenInWholeMinutes() {
    // Age of the location in milliseconds, with the text expected for it.
    listOf(
            // Dated in the future, e.g. because the member's clock is ahead.
            -3_000L to "live",
            0L to "live",
            4_999L to "live",
            5_000L to "5 s ago",
            10_999L to "10 s ago",
            59_999L to "59 s ago",
            60_000L to "1 min ago",
            119_999L to "1 min ago",
            120_000L to "2 min ago",
            90 * 60_000L to "90 min ago",
            // Dated at the epoch.
            NOW to "28333333 min ago",
        )
        .forEach { (ageMillis, expected) ->
          assertEquals("age of $ageMillis ms", expected, freshnessForAge(ageMillis))
        }
  }

  @Test
  fun timeIsRefreshedEverySecondWhileTickingAndKeptOnceItStops() {
    showCurrentTime(ticking = true)
    assertShownTime(NOW)

    advanceBy(900)
    assertShownTime(NOW)

    advanceBy(100)
    assertShownTime(NOW + 1_000)

    advanceBy(1_000)
    assertShownTime(NOW + 2_000)

    setTicking(false)
    advanceBy(5_000)

    assertShownTime(NOW + 2_000)
  }

  @Test
  fun timeIsRefreshedOnlyOnceTickingStarts() {
    showCurrentTime(ticking = false)
    advanceBy(3_000)
    assertShownTime(NOW)

    setTicking(true)
    assertShownTime(NOW + 3_000)

    advanceBy(1_000)
    assertShownTime(NOW + 4_000)
  }

  /** Shows the time given by [rememberCurrentTimeMillis], driven by the test's clocks only. */
  private fun showCurrentTime(ticking: Boolean) {
    composeRule.mainClock.autoAdvance = false
    isTicking.value = ticking
    composeRule.setContent {
      Text(
          text = rememberCurrentTimeMillis(isTicking.value) { clockMillis }.toString(),
          modifier = Modifier.testTag(TIME_TAG),
      )
    }
    composeRule.mainClock.advanceTimeByFrame()
  }

  /**
   * Changes [isTicking] and lets the screen recompose and restart its refresh before time moves.
   */
  private fun setTicking(ticking: Boolean) {
    isTicking.value = ticking
    // Without auto-advance, nothing else hands the change to the recomposer before the frames.
    Snapshot.sendApplyNotifications()
    repeat(2) { composeRule.mainClock.advanceTimeByFrame() }
  }

  /** Moves the injected time and the Compose clock forward together. */
  private fun advanceBy(millis: Long) {
    clockMillis += millis
    composeRule.mainClock.advanceTimeBy(millis, ignoreFrameDuration = true)
    composeRule.mainClock.advanceTimeByFrame()
  }

  private fun assertShownTime(millis: Long) {
    composeRule.onNodeWithTag(TIME_TAG).assertTextEquals(millis.toString())
  }

  private companion object {
    const val NOW = 1_700_000_000_000L
    const val TIME_TAG = "current_time"
  }
}
