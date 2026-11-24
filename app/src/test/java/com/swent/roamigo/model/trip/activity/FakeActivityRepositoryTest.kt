// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.activity

import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.ActivityType
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
  fun suggestionsWithOptionalSchedulesAreStoredAndObservedAlongsideAcceptedActivities() =
      runBlocking {
        val repository = FakeActivityRepository(currentUserId = "user-1")
        val itinerary = repository.createActivity("trip-1", "itinerary", draft)
        val unscheduled =
            repository.createSuggestionWithVote(
                "trip-1",
                "unscheduled",
                draft.copy(startTime = null),
            )
        val startOnly = repository.createSuggestionWithVote("trip-1", "start-only", draft)
        val end = draft.startTime!!.plusSeconds(3600)
        val scheduled =
            repository.createSuggestionWithVote("trip-1", "scheduled", draft.copy(endTime = end))

        assertEquals(ActivityType.SUGGESTION, unscheduled.activity.activityType)
        assertEquals("user-1", unscheduled.activity.createdByUserId)
        assertNull(unscheduled.activity.startTime)
        assertNull(unscheduled.activity.endTime)
        assertNull(unscheduled.activity.description)
        assertEquals(draft.startTime.epochSecond, startOnly.activity.startTime?.seconds)
        assertNull(startOnly.activity.endTime)
        assertEquals(draft.startTime.epochSecond, scheduled.activity.startTime?.seconds)
        assertEquals(end.epochSecond, scheduled.activity.endTime?.seconds)
        assertEquals(
            listOf(itinerary, unscheduled.activity, startOnly.activity, scheduled.activity),
            repository.observeActivities("trip-1").first(),
        )
      }

  @Test
  fun concurrentSuggestionRetriesKeepTheOriginalDespiteDifferentDrafts() = runBlocking {
    val repository = FakeActivityRepository()

    val attempts =
        List(12) { index ->
              async(Dispatchers.Default) {
                repository.createSuggestionWithVote(
                    "trip-1",
                    "repeat",
                    draft.copy(name = "Place $index"),
                )
              }
            }
            .awaitAll()

    assertEquals(listOf(attempts.first()), attempts.distinct())
    assertEquals(listOf(attempts.first().activity), repository.observeActivities("trip-1").first())
  }

  @Test
  fun invalidSuggestionDraftIsIgnoredOnlyWhenRetryingAnExistingSuggestion() = runBlocking {
    val repository = FakeActivityRepository()
    val original = repository.createSuggestionWithVote("trip-1", "id", draft)
    val invalid = draft.copy(name = " ")

    assertEquals(original, repository.createSuggestionWithVote("trip-1", "id", invalid))

    val error = runCatching { repository.createSuggestionWithVote("trip-1", "new", invalid) }
    val validation = error.exceptionOrNull() as ActivityCreationException.Validation
    assertEquals(listOf(ActivityDraftError.BlankName), validation.errors)
    assertEquals(listOf(original.activity), repository.observeActivities("trip-1").first())
  }

  @Test
  fun anotherAuthorCannotReuseASuggestionId() = runBlocking {
    val original =
        FakeActivityRepository(currentUserId = "alice")
            .createSuggestionWithVote("trip-1", "id", draft)
    val repository =
        FakeActivityRepository(
            listOf(original.activity),
            currentUserId = "bob",
            votes = listOf(original.vote),
        )

    val error = runCatching {
      repository.createSuggestionWithVote("trip-1", "id", draft.copy(name = " "))
    }

    assertTrue(error.exceptionOrNull() is ActivityCreationException.PermissionDenied)
    assertEquals(listOf(original.activity), repository.observeActivities("trip-1").first())
  }

  @Test
  fun createActivityCannotReuseAnExistingSuggestionId() = runBlocking {
    val suggestion =
        FakeActivityRepository()
            .createSuggestionWithVote("trip-1", "id", draft.copy(startTime = null))
    val repository = FakeActivityRepository(listOf(suggestion.activity))

    val error = runCatching { repository.createActivity("trip-1", "id", draft.copy(name = " ")) }

    assertTrue(error.exceptionOrNull() is ActivityCreationException.TypeConflict)
    assertEquals(listOf(suggestion.activity), repository.observeActivities("trip-1").first())
  }

  @Test
  fun createSuggestionCannotReuseAnExistingAcceptedActivityId() = runBlocking {
    val repository = FakeActivityRepository()
    val itinerary = repository.createActivity("trip-1", "id", draft)

    val error = runCatching {
      repository.createSuggestionWithVote("trip-1", "id", draft.copy(name = " "))
    }

    assertTrue(error.exceptionOrNull() is ActivityCreationException.TypeConflict)
    assertEquals(listOf(itinerary), repository.observeActivities("trip-1").first())
  }

  @Test
  fun suggestionCreationRejectsIdsThatCannotProduceAValidVoteId() = runBlocking {
    val repository = FakeActivityRepository()
    for (id in listOf(" ", ".", "..", "a/b", "__reserved__", "é".repeat(746))) {
      val error = runCatching { repository.createSuggestionWithVote("trip-1", id, draft) }
      assertTrue(error.exceptionOrNull() is IllegalArgumentException)
    }
    assertEquals(1500, initialSuggestionVoteId("a".repeat(1489)).toByteArray().size)
    assertTrue(repository.observeActivities("trip-1").first().isEmpty())
  }

  @Test
  fun suggestionCreationPropagatesCancellation() = runBlocking {
    val cancellation = CancellationException("cancelled")
    val repository = FakeActivityRepository(failure = cancellation)

    val error = runCatching { repository.createSuggestionWithVote("trip-1", "id", draft) }

    assertSame(cancellation, error.exceptionOrNull())
  }

  @Test
  fun createdActivityIsAcceptedAndAuthoredByTheCurrentUser() = runBlocking {
    val repository = FakeActivityRepository(currentUserId = "user-1")

    val created = repository.createActivity("trip-1", "activity-1", draft)

    assertEquals("activity-1", created.uid)
    assertEquals("trip-1", created.tripId)
    assertEquals(ActivityType.ACCEPTED, created.activityType)
    assertEquals("user-1", created.createdByUserId)
    assertEquals(draft.name, created.name)
    assertEquals(draft.startTime?.epochSecond, created.startTime?.seconds)
  }

  @Test
  fun activityWithoutStartTimeIsRejectedAndNotStored() = runBlocking {
    val repository = FakeActivityRepository()

    val error = runCatching {
      repository.createActivity("trip-1", "activity-1", draft.copy(startTime = null))
    }

    val validation = error.exceptionOrNull() as ActivityCreationException.Validation
    assertEquals(listOf(ActivityDraftError.MissingStartTime), validation.errors)
    assertTrue(repository.observeActivities("trip-1").first().isEmpty())
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
    assertEquals(
        first,
        repository.createActivity("trip-1", "activity-1", draft.copy(startTime = null)),
    )
    assertEquals(listOf(first), repository.observeActivities("trip-1").first())
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
  fun malformedIdsAreACallerError() {
    val repository = FakeActivityRepository()

    assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.createActivity("trip-1", "a/b", draft) }
    }
    assertThrows(IllegalArgumentException::class.java) {
      runBlocking { repository.createActivity(" ", "activity-1", draft) }
    }
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
