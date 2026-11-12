package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import java.time.LocalDate
import java.time.ZoneId

internal fun Trip.isCompletedOn(
    today: LocalDate,
    zone: ZoneId,
): Boolean = endDate.isBeforeLocalDate(today, zone)

/** The final local calendar day is inclusive, independently of the stored trip status. */
internal fun Timestamp.isBeforeLocalDate(today: LocalDate, zone: ZoneId): Boolean =
    toDate().toInstant().atZone(zone).toLocalDate().isBefore(today)
