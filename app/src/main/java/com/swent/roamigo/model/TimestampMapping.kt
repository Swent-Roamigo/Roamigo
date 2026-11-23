// AI assistance was used for implementation support.
package com.swent.roamigo.model

import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Converts a domain date to the timestamp stored in Firestore. */
internal fun Instant.toFirebaseTimestamp(): Timestamp = Timestamp(epochSecond, nano)

/**
 * Converts a stored timestamp into the Java time value used by presentation code. Going through
 * [Timestamp.toDate] would discard sub-millisecond precision.
 */
internal fun Timestamp.toDomainInstant(): Instant =
    Instant.ofEpochSecond(seconds, nanoseconds.toLong())

/** The stored timestamp as a date and time in [zone]. */
internal fun Timestamp.atZone(zone: ZoneId): ZonedDateTime = toDomainInstant().atZone(zone)

/** The calendar day of the stored timestamp in [zone]. */
internal fun Timestamp.toLocalDate(zone: ZoneId): LocalDate = atZone(zone).toLocalDate()
