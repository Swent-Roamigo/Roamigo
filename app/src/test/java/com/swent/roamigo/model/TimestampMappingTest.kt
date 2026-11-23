// AI assistance was used for implementation support.
package com.swent.roamigo.model

import com.google.firebase.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class TimestampMappingTest {
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

  @Test
  fun timestampConversionPreservesSecondsAndNanoseconds() {
    val timestamp = Timestamp(1_900_000_000, 123_456_789)

    assertEquals(Instant.ofEpochSecond(1_900_000_000, 123_456_789), timestamp.toDomainInstant())
  }

  @Test
  fun localDateFollowsTheZoneNotUtc() {
    // 23:30 UTC on 31 December is already 1 January in Zurich.
    val timestamp = Instant.parse("2026-12-31T23:30:00Z").toFirebaseTimestamp()

    assertEquals(LocalDate.of(2027, 1, 1), timestamp.toLocalDate(ZURICH))
    assertEquals(LocalDate.of(2026, 12, 31), timestamp.toLocalDate(ZoneId.of("UTC")))
  }

  @Test
  fun atZoneKeepsTheInstantWithSubMillisecondPrecision() {
    val timestamp = Timestamp(1_900_000_000, 123_456_789)

    assertEquals(
        ZonedDateTime.ofInstant(Instant.ofEpochSecond(1_900_000_000, 123_456_789), ZURICH),
        timestamp.atZone(ZURICH),
    )
  }

  private companion object {
    val ZURICH: ZoneId = ZoneId.of("Europe/Zurich")
  }
}
