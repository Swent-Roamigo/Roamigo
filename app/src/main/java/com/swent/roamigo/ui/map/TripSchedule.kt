// AI assistance was used for implementation support.
package com.swent.roamigo.ui.map

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Where a trip stands relative to today, as a short status next to its dates. */
internal sealed interface TripSchedule {
  /** The trip has ended. */
  data object Past : TripSchedule

  /** The trip has started and not ended. */
  data object InProgress : TripSchedule

  /** The trip starts soon, in [days] days. */
  data class StartsIn(val days: Int) : TripSchedule

  /** The trip is further away, still being planned. */
  data object Planning : TripSchedule
}

/** A trip starting within this many days is shown as "in N days" rather than "planning". */
internal const val SOON_DAYS = 14L

internal fun tripScheduleOf(start: LocalDate, end: LocalDate, today: LocalDate): TripSchedule =
    when {
      end.isBefore(today) -> TripSchedule.Past
      !start.isAfter(today) -> TripSchedule.InProgress
      else -> {
        val days = ChronoUnit.DAYS.between(today, start)
        if (days <= SOON_DAYS) TripSchedule.StartsIn(days.toInt()) else TripSchedule.Planning
      }
    }
