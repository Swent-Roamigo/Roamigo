package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import com.swent.roamigo.model.toLocalDate
import java.time.LocalDate
import java.time.ZoneId

internal fun Trip.isCompletedOn(
    today: LocalDate,
    zone: ZoneId,
): Boolean = endDate.isBeforeLocalDate(today, zone)

/** The final local calendar day is inclusive, independently of the stored trip status. */
internal fun Timestamp.isBeforeLocalDate(today: LocalDate, zone: ZoneId): Boolean =
    toLocalDate(zone).isBefore(today)
