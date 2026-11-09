// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.content.res.Resources
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationFreshnessTest {

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

  private companion object {
    const val NOW = 1_700_000_000_000L
  }
}
