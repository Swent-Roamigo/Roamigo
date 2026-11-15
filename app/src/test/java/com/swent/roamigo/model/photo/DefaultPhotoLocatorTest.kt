// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import com.swent.roamigo.model.location.DeviceLocation
import com.swent.roamigo.model.location.FakeLocationRepository
import com.swent.roamigo.model.location.LocationRepository
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPhotoLocatorTest {
  private val location = FakeLocationRepository(hasPermission = true)
  private val locator = DefaultPhotoLocator(location)

  private fun locatorWith(read: suspend () -> DeviceLocation?) =
      DefaultPhotoLocator(
          object : LocationRepository by location {
            override suspend fun getCurrentLocation(): DeviceLocation? = read()
          }
      )

  @Test
  fun reportsPermissionOffWithoutReadingThePosition() = runTest {
    location.hasPermission = false
    location.currentLocation = DeviceLocation(46.5, 6.6)

    assertEquals(PhotoLocationResult.PermissionOff, locator.locate())
    assertEquals(0, location.getCurrentLocationCalls)
  }

  @Test
  fun findsThePosition() = runTest {
    location.currentLocation = DeviceLocation(46.5, 6.6)

    assertEquals(
        PhotoLocationResult.Found(PhotoCoordinates(46.5, 6.6)),
        locator.locate(),
    )
  }

  @Test
  fun reportsUnavailableWhenThereIsNoFix() = runTest {
    location.currentLocation = null

    assertEquals(PhotoLocationResult.Unavailable, locator.locate())
  }

  @Test
  fun timesOutOnlyAfterTheWholeTimeout() = runTest {
    val slow = locatorWith {
      delay(60.seconds)
      DeviceLocation(1.0, 2.0)
    }
    val result = async { slow.locate(2.seconds) }

    advanceTimeBy(2.seconds - 1.milliseconds)
    runCurrent()
    assertFalse(result.isCompleted)

    advanceTimeBy(1.milliseconds)
    runCurrent()
    assertEquals(PhotoLocationResult.TimedOut, result.await())
    assertEquals(2_000L, currentTime)
  }

  @Test
  fun usesTheDefaultTimeoutWhenNoneIsGiven() = runTest {
    val slow = locatorWith {
      delay(60.seconds)
      DeviceLocation(1.0, 2.0)
    }

    assertEquals(PhotoLocationResult.TimedOut, slow.locate())
    assertEquals(PHOTO_LOCATION_TIMEOUT.inWholeMilliseconds, currentTime)
  }

  @Test
  fun acceptsAFixThatArrivesJustBeforeTheTimeout() = runTest {
    val slow = locatorWith {
      delay(1.seconds)
      DeviceLocation(1.0, 2.0)
    }

    assertEquals(
        PhotoLocationResult.Found(PhotoCoordinates(1.0, 2.0)),
        slow.locate(2.seconds),
    )
  }

  @Test
  fun timesOutAtOnceWithoutReadingForAZeroOrNegativeTimeout() = runTest {
    location.currentLocation = DeviceLocation(46.5, 6.6)

    assertEquals(PhotoLocationResult.TimedOut, locator.locate(Duration.ZERO))
    assertEquals(PhotoLocationResult.TimedOut, locator.locate((-1).seconds))
    assertEquals(0, location.getCurrentLocationCalls)
    assertEquals(0L, currentTime)
  }

  @Test
  fun reportsPermissionOffWhenItIsRevokedDuringTheRead() = runTest {
    val revoked = locatorWith { throw SecurityException("revoked") }

    assertEquals(PhotoLocationResult.PermissionOff, revoked.locate())
  }

  @Test
  fun rejectsInvalidCoordinates() = runTest {
    val invalid =
        listOf(
            DeviceLocation(Double.NaN, 0.0),
            DeviceLocation(0.0, Double.NaN),
            DeviceLocation(Double.POSITIVE_INFINITY, 0.0),
            DeviceLocation(0.0, Double.NEGATIVE_INFINITY),
            DeviceLocation(90.1, 0.0),
            DeviceLocation(-90.1, 0.0),
            DeviceLocation(0.0, 180.1),
            DeviceLocation(0.0, -180.1),
        )

    invalid.forEach {
      location.currentLocation = it
      assertEquals("$it", PhotoLocationResult.Unavailable, locator.locate())
    }
  }

  @Test
  fun acceptsCoordinatesOnTheBounds() = runTest {
    listOf(DeviceLocation(90.0, 180.0), DeviceLocation(-90.0, -180.0)).forEach {
      location.currentLocation = it
      assertEquals(
          PhotoLocationResult.Found(PhotoCoordinates(it.latitude, it.longitude)),
          locator.locate(),
      )
    }
  }

  @Test
  fun cancellingTheCallerCancelsTheRead() = runTest {
    val started = CompletableDeferred<Unit>()
    var readCancelled = false
    val hanging = locatorWith {
      started.complete(Unit)
      try {
        delay(60.seconds)
      } finally {
        readCancelled = true
      }
      null
    }
    val result = async { hanging.locate() }
    started.await()

    result.cancel()
    runCurrent()

    assertTrue(result.isCancelled)
    assertTrue(readCancelled)
  }
}
