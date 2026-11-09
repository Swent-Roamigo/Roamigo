// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import java.time.Instant

/** Converts a domain date to the timestamp stored by the trip repositories. */
internal fun Instant.toFirebaseTimestamp(): Timestamp = Timestamp(epochSecond, nano)

/** Converts a stored trip timestamp into the Java time value used by presentation code. */
internal fun Timestamp.toDomainInstant(): Instant =
    Instant.ofEpochSecond(seconds, nanoseconds.toLong())
