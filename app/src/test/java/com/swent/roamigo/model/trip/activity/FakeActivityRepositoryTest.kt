// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.activity

import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.ActivityType
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeActivityRepositoryTest {

  private val draft =
      ActivityDraft(
          name = "Visit EPFL",
          description = null,
          location = TripLocation(46.5197, 6.5668, "EPFL"),
          startTime = Instant.ofEpochSecond(1_900_000_000),
          endTime = null,
      )

  @Test
  fun createdActivityIsAcceptedAndAuthoredByTheCurrentUser() = runBlocking {
    val repository = FakeActivityRepository(currentUserId = "user-1")

    val created = repository.createActivity("trip-1", "activity-1", draft)

    assertEquals("activity-1", created.uid)
    assertEquals("trip-1", created.tripId)
    assertEquals(ActivityType.ACCEPTED, created.activityType)
    assertEquals("user-1", created.createdByUserId)
    assertEquals(draft.name, created.name)
    assertEquals(draft.startTime.epochSecond, created.startTime.seconds)
  }

  @Test
  fun createdActivityIsObservedOnlyInItsTrip() = runBlocking {
    val repository = FakeActivityRepository()

    val created = repository.createActivity("trip-1", "activity-1", draft)

    assertEquals(listOf(created), repository.observeActivities("trip-1").first())
    assertTrue(repository.observeActivities("trip-2").first().isEmpty())
  }

  @Test
  fun retryingWithTheSameIdKeepsOneActivity() = runBlocking {
    val repository = FakeActivityRepository()

    val first = repository.createActivity("trip-1", "activity-1", draft)
    val second = repository.createActivity("trip-1", "activity-1", draft.copy(name = "Changed"))

    assertEquals(first, second)
    assertEquals(1, repository.observeActivities("trip-1").first().size)
  }

  @Test
  fun sameIdByAnotherAuthorIsDenied() {
    val existing = runBlocking {
      FakeActivityRepository(currentUserId = "user-1").createActivity("trip-1", "a", draft)
    }
    val repository = FakeActivityRepository(listOf(existing), currentUserId = "user-2")

    assertThrows(ActivityCreationException.PermissionDenied::class.java) {
      runBlocking { repository.createActivity("trip-1", "a", draft) }
    }
  }

  @Test
  fun sameIdInAnotherTripIsAnotherActivity() = runBlocking {
    val repository = FakeActivityRepository()

    repository.createActivity("trip-1", "a", draft)
    repository.createActivity("trip-2", "a", draft)

    assertEquals(1, repository.observeActivities("trip-1").first().size)
    assertEquals(1, repository.observeActivities("trip-2").first().size)
  }

  @Test
  fun invalidDraftIsRejectedAndNothingIsStored() = runBlocking {
    val repository = FakeActivityRepository()

    val error = runCatching {
      repository.createActivity("trip-1", "a", draft.copy(name = " "))
    }
        .exceptionOrNull()

    assertEquals(
        listOf(ActivityDraftError.BlankName),
        (error as ActivityCreationException.Validation).errors,
    )
    assertTrue(repository.observeActivities("trip-1").first().isEmpty())
  }

  @Test
  fun signedOutUserCannotCreate() {
    val repository = FakeActivityRepository(currentUserId = null)

    assertThrows(ActivityCreationException.NotAuthenticated::class.java) {
      runBlocking { repository.createActivity("trip-1", "a", draft) }
    }
  }

  @Test
  fun configuredFailureIsReportedAsCreationException() {
    val repository = FakeActivityRepository(failure = IOException("offline"))

    assertThrows(ActivityCreationException.Unknown::class.java) {
      runBlocking { repository.createActivity("trip-1", "a", draft) }
    }
    assertThrows(IOException::class.java) {
      runBlocking { repository.observeActivities("trip-1").first() }
    }
  }

  @Test
  fun configuredCreationExceptionIsReportedAsIs() {
    val failure = ActivityCreationException.Network()
    val repository = FakeActivityRepository(failure = failure)

    val error =
        assertThrows(ActivityCreationException.Network::class.java) {
          runBlocking { repository.createActivity("trip-1", "a", draft) }
        }
    assertSame(failure, error)
  }

  @Test
  fun observersOfAnotherTripDoNotSeeInitialActivitiesOfThisOne() = runBlocking {
    val existing = runBlocking { FakeActivityRepository().createActivity("trip-1", "a", draft) }
    val repository = FakeActivityRepository(listOf(existing))

    assertEquals(listOf(existing), repository.observeActivities("trip-1").first())
    assertTrue(repository.observeActivities("trip-2").first().isEmpty())
  }
}
