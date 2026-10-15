// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip

import com.google.firebase.Timestamp
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class TripDateMappingTest {
  @Test
  fun conversionPreservesSecondsAndNanoseconds() {
    val instant = Instant.ofEpochSecond(1_900_000_000, 123_456_789)

    assertEquals(Timestamp(1_900_000_000, 123_456_789), instant.toFirebaseTimestamp())
  }

  @Test
  fun conversionPreservesDatesBeforeEpoch() {
    val instant = Instant.ofEpochSecond(-1, 500_000_000)

    assertEquals(Timestamp(-1, 500_000_000), instant.toFirebaseTimestamp())
  }
}
