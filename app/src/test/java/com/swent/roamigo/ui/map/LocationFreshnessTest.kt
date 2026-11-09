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
  fun justUpdatedLocationIsLive() {
    assertEquals("live", freshnessForAge(0))
  }

  @Test
  fun locationDatedInTheFutureIsLive() {
    assertEquals("live", freshnessForAge(-3_000))
  }

  @Test
  fun lastMillisecondBeforeLiveThresholdIsLive() {
    assertEquals("live", freshnessForAge(4_999))
  }

  @Test
  fun liveThresholdSwitchesToSeconds() {
    assertEquals("5 s ago", freshnessForAge(5_000))
  }

  @Test
  fun secondsAreRoundedDown() {
    assertEquals("10 s ago", freshnessForAge(10_999))
  }

  @Test
  fun lastSecondBeforeAMinuteIsShownInSeconds() {
    assertEquals("59 s ago", freshnessForAge(59_999))
  }

  @Test
  fun oneMinuteSwitchesToMinutes() {
    assertEquals("1 min ago", freshnessForAge(60_000))
  }

  @Test
  fun minutesAreRoundedDown() {
    assertEquals("1 min ago", freshnessForAge(119_999))
    assertEquals("2 min ago", freshnessForAge(120_000))
  }

  @Test
  fun longAgesAreStillShownInMinutes() {
    assertEquals("90 min ago", freshnessForAge(90 * 60_000L))
  }

  @Test
  fun locationDatedAtTheEpochIsShownInMinutes() {
    assertEquals("28333333 min ago", freshnessForAge(NOW))
  }

  @Test
  fun timeDoesNotChangeWhileNotTicking() {
    showCurrentTime(ticking = false)
    assertShownTime(NOW)

    advanceBy(5_000)

    assertShownTime(NOW)
  }

  @Test
  fun timeIsRefreshedEverySecondWhileTicking() {
    showCurrentTime(ticking = true)
    assertShownTime(NOW)

    advanceBy(900)
    assertShownTime(NOW)

    advanceBy(100)
    assertShownTime(NOW + 1_000)

    advanceBy(1_000)
    assertShownTime(NOW + 2_000)
  }

  @Test
  fun timeKeepsItsLastValueOnceTickingStops() {
    showCurrentTime(ticking = true)
    advanceBy(1_000)
    assertShownTime(NOW + 1_000)

    setTicking(false)
    advanceBy(5_000)

    assertShownTime(NOW + 1_000)
  }

  @Test
  fun timeIsRefreshedAgainOnceTickingStarts() {
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
