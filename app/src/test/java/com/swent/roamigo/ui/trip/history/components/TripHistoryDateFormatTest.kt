// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.history.components

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TripHistoryDateFormatTest {

  @Test
  fun formatsDatesWithinSameMonth() {
    val result =
        formatTripHistoryDateRange(
            LocalDate.of(2026, 6, 5),
            LocalDate.of(2026, 6, 12),
        )

    assertEquals("5 Jun 2026 – 12 Jun 2026", result)
  }

  @Test
  fun formatsDatesAcrossYears() {
    val result =
        formatTripHistoryDateRange(
            LocalDate.of(2025, 12, 30),
            LocalDate.of(2026, 1, 2),
        )

    assertEquals("30 Dec 2025 – 2 Jan 2026", result)
  }
}
