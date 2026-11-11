// AI assistance was used for implementation support.
package com.swent.roamigo.ui.map

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TripScheduleTest {
  private val today = LocalDate.of(2026, 10, 9)

  private fun schedule(startOffset: Long, endOffset: Long) =
      tripScheduleOf(today.plusDays(startOffset), today.plusDays(endOffset), today)

  @Test
  fun aTripThatEndedYesterdayIsPast() {
    assertEquals(TripSchedule.Past, schedule(-5, -1))
  }

  @Test
  fun aTripEndingTodayIsStillInProgress() {
    assertEquals(TripSchedule.InProgress, schedule(-2, 0))
  }

  @Test
  fun aTripStartingTodayIsInProgress() {
    assertEquals(TripSchedule.InProgress, schedule(0, 3))
  }

  @Test
  fun aTripStartingSoonCountsTheDays() {
    assertEquals(TripSchedule.StartsIn(3), schedule(3, 10))
  }

  @Test
  fun aTripStartingInTwoWeeksIsStillSoon() {
    assertEquals(TripSchedule.StartsIn(14), schedule(14, 20))
  }

  @Test
  fun aTripFurtherAwayIsBeingPlanned() {
    assertEquals(TripSchedule.Planning, schedule(15, 20))
  }
}
