// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats a trip's first and last days as the compact Figma range, such as "12 – 19 Oct". Both
 * months, or both years, are kept when the trip crosses that boundary.
 *
 * @param start the first calendar day of the trip.
 * @param end the last calendar day of the trip.
 * @param locale the locale of the month names.
 */
internal fun formatTripDateRange(start: LocalDate, end: LocalDate, locale: Locale): String {
  val pattern = if (start.year == end.year) "d MMM" else "d MMM yyyy"
  val formatter = DateTimeFormatter.ofPattern(pattern, locale)
  val startText =
      if (start.year == end.year && start.month == end.month) start.dayOfMonth.toString()
      else start.format(formatter)
  return "$startText – ${end.format(formatter)}"
}
