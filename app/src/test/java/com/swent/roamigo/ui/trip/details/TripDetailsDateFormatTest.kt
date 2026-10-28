// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.trip.details

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class TripDetailsDateFormatTest {
  @Test
  fun sameMonthUsesTheCompactFigmaRange() {
    assertEquals(
        "12 – 19 Oct",
        formatTripDetailsDates(
            LocalDate.of(2026, 10, 12),
            LocalDate.of(2026, 10, 19),
            Locale.ENGLISH,
        ),
    )
  }

  @Test
  fun crossingMonthsRetainsBothMonthNames() {
    assertEquals(
        "30 Oct – 2 Nov",
        formatTripDetailsDates(
            LocalDate.of(2026, 10, 30),
            LocalDate.of(2026, 11, 2),
            Locale.ENGLISH,
        ),
    )
  }

  @Test
  fun crossingYearsRetainsBothYears() {
    assertEquals(
        "30 Dec 2026 – 2 Jan 2027",
        formatTripDetailsDates(
            LocalDate.of(2026, 12, 30),
            LocalDate.of(2027, 1, 2),
            Locale.ENGLISH,
        ),
    )
  }

  @Test
  fun monthNamesFollowTheDisplayLocale() {
    val start = LocalDate.of(2026, 10, 12)
    val end = LocalDate.of(2026, 10, 19)
    val month = end.format(java.time.format.DateTimeFormatter.ofPattern("MMM", Locale.FRENCH))
    assertEquals("12 – 19 $month", formatTripDetailsDates(start, end, Locale.FRENCH))
  }
}
