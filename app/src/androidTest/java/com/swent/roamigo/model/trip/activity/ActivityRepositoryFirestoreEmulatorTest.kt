// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip.activity

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.swent.roamigo.FirebaseEmulators
import com.swent.roamigo.FirebaseEmulators.FIRESTORE_PORT
import com.swent.roamigo.FirebaseEmulators.HOST
import com.swent.roamigo.FirebaseEmulators.PROJECT_ID
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.ActivityType
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.TripDraft
import com.swent.roamigo.model.trip.TripRepositoryFirestore
import com.swent.roamigo.model.trip.toFirestoreData
import com.swent.roamigo.model.trip.voting.VoteBallot
import com.swent.roamigo.model.trip.voting.VoteRepositoryFirestore
import com.swent.roamigo.model.trip.voting.VoteStatus
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs [ActivityRepositoryFirestore] against the Auth and Firestore emulators, so that the rules of
 * `firestore.rules` are enforced on every read and write.
 *
 * Requires `firebase emulators:exec --only auth,firestore --project demo-roamigo`.
 */
@RunWith(AndroidJUnit4::class)
class ActivityRepositoryFirestoreEmulatorTest {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private val apps = mutableListOf<FirebaseApp>()
  private val tripId = "trip-${UUID.randomUUID()}"

  private val draft =
      ActivityDraft(
          name = "Visit EPFL",
          description = "Rolex Learning Center",
          location = TripLocation(46.5197, 6.5668, "EPFL", "CH"),
          startTime = Instant.ofEpochSecond(1_900_000_000),
          endTime = Instant.ofEpochSecond(1_900_003_600),
      )

  private lateinit var alice: User
  private lateinit var bob: User

  @Before
  fun setUp() =
      runBlocking<Unit> {
        alice = signInUser()
        bob = signInUser()
        // Alice owns the trip. Bob is not a member until a test adds him.
        alice.tripRepository.createTrip(tripId, tripDraft())
      }

  @After
  fun tearDown() {
    apps.forEach { it.delete() }
    clearFirestore()
  }

  @Test
  fun memberCreatesActivityAndReadsItBack() = runBlocking {
    val created = alice.repository.createActivity(tripId, "activity-1", draft)

    assertEquals(ActivityType.ACCEPTED, created.activityType)
    assertEquals(alice.userId, created.createdByUserId)
    assertEquals(listOf(created), alice.tripRepository.getActivitiesForTrip(tripId))
    assertEquals(
        listOf(created),
        withTimeout(10_000) {
          alice.repository.observeActivities(tripId).first { it.isNotEmpty() }
        },
    )
  }

  @Test
  fun membersCanCreateSuggestionsAndObserveThemAlongsideItineraryActivities() = runBlocking {
    addBobToTrip()
    val itinerary = alice.repository.createActivity(tripId, "itinerary", draft)
    val observed = async {
      withTimeout(10_000) { bob.repository.observeActivities(tripId).first { it.size == 3 } }
    }

    val (unscheduled, unscheduledVote) =
        alice.repository.createSuggestionWithVote(
            tripId,
            "unscheduled",
            draft.copy(description = null, startTime = null, endTime = null),
        )
    val (scheduled, scheduledVote) =
        bob.repository.createSuggestionWithVote(tripId, "scheduled", draft)

    assertEquals(ActivityType.SUGGESTION, unscheduled.activityType)
    assertEquals(alice.userId, unscheduled.createdByUserId)
    assertNull(unscheduled.startTime)
    assertNull(unscheduled.endTime)
    assertEquals(ActivityType.SUGGESTION, scheduled.activityType)
    assertEquals(bob.userId, scheduled.createdByUserId)
    assertEquals(draft.startTime?.epochSecond, scheduled.startTime?.seconds)
    assertEquals(draft.endTime?.epochSecond, scheduled.endTime?.seconds)
    val expected = setOf(itinerary, unscheduled, scheduled)
    val voting = VoteRepositoryFirestore(alice.db)
    assertEquals(unscheduledVote, voting.getVote(tripId, unscheduled.linkedVoteId!!))
    assertEquals(
        scheduledVote,
        withTimeout(10_000) {
          voting.observeVote(tripId, scheduled.linkedVoteId!!).first { it != null }
        },
    )
    assertEquals(expected, observed.await().toSet())
    assertEquals(expected, alice.tripRepository.getActivitiesForTrip(tripId).toSet())
  }

  @Test
  fun concurrentSuggestionAttemptsAndChangedDraftRetriesReturnOneStoredPair() = runBlocking {
    val results =
        withTimeout(30_000) {
          List(2) { index ->
                async {
                  alice.repository.createSuggestionWithVote(
                      tripId,
                      "suggestion",
                      draft.copy(name = "Place $index"),
                  )
                }
              }
              .awaitAll()
        }
    val first = results.first()

    assertEquals(listOf(first, first), results)
    assertEquals(
        first,
        alice.repository.createSuggestionWithVote(
            tripId,
            "suggestion",
            draft.copy(name = "Changed"),
        ),
    )
    assertEquals(listOf(first.activity), alice.tripRepository.getActivitiesForTrip(tripId))
    assertEquals(listOf(first.vote), VoteRepositoryFirestore(alice.db).getVotes(tripId))
  }

  @Test
  fun anotherMemberCannotReuseAnExistingSuggestionId() = runBlocking {
    val (original) = alice.repository.createSuggestionWithVote(tripId, "suggestion", draft)
    addBobToTrip()

    val error = runCatching {
      bob.repository.createSuggestionWithVote(tripId, original.uid, draft.copy(name = "Other"))
    }

    assertTrue(error.exceptionOrNull() is ActivityCreationException.PermissionDenied)
    val stored = bob.tripRepository.getActivitiesForTrip(tripId).single()
    assertEquals(alice.userId, stored.createdByUserId)
    assertEquals(original, stored)
  }

  @Test
  fun createActivityCannotReuseASuggestionId() = runBlocking {
    val (original) = alice.repository.createSuggestionWithVote(tripId, "suggestion", draft)

    val error = runCatching { alice.repository.createActivity(tripId, original.uid, draft) }

    val conflict = error.exceptionOrNull() as ActivityCreationException.TypeConflict
    assertEquals(ActivityType.ACCEPTED, conflict.requested)
    assertEquals(ActivityType.SUGGESTION, conflict.existing)
    assertEquals(listOf(original), alice.tripRepository.getActivitiesForTrip(tripId))
  }

  @Test
  fun createSuggestionCannotReuseAnAcceptedActivityId() = runBlocking {
    val original = alice.repository.createActivity(tripId, "activity", draft)

    val error = runCatching {
      alice.repository.createSuggestionWithVote(tripId, original.uid, draft)
    }

    val conflict = error.exceptionOrNull() as ActivityCreationException.TypeConflict
    assertEquals(ActivityType.SUGGESTION, conflict.requested)
    assertEquals(ActivityType.ACCEPTED, conflict.existing)
    assertEquals(listOf(original), alice.tripRepository.getActivitiesForTrip(tripId))
  }

  @Test
  fun rawSuggestionWritesAndReadsRequireAuthenticationAndMembership() = runBlocking {
    val proposal = activityData(alice, ActivityType.SUGGESTION)
    val stored = activityDocument(alice, "suggestion")
    assertPermissionDenied { stored.set(proposal).await() }
    alice.repository.createSuggestionWithVote(tripId, stored.id, draft)

    // Use a separate, signed-out client whose cache and auth have never seen the trip.
    val signedOutDb = FirebaseEmulators.firestore(newApp())
    val collection = signedOutDb.collection("trips").document(tripId).collection("activities")
    assertPermissionDenied { collection.document("new").set(proposal).await() }
    assertPermissionDenied { collection.document(stored.id).get(Source.SERVER).await() }
    assertPermissionDenied {
      activityDocument(bob, "new").set(activityData(bob, ActivityType.SUGGESTION)).await()
    }
    assertPermissionDenied { activityDocument(bob, stored.id).get(Source.SERVER).await() }
    assertPermissionDenied {
      withTimeout(10_000) { bob.repository.observeActivities(tripId).first() }
    }
  }

  @Test
  fun linkedVoteSupportsBallotsClosingAndAnInvalidDraftRetry() = runBlocking {
    addBobToTrip()
    val (suggestion, vote) = alice.repository.createSuggestionWithVote(tripId, "proposal", draft)
    val voting = VoteRepositoryFirestore(alice.db)
    val ballot = VoteBallot(bob.userId, "YES", Timestamp(1_900_000_000, 0))
    VoteRepositoryFirestore(bob.db).submitBallot(tripId, vote.uid, ballot)
    voting.closeVote(tripId, vote.uid)
    assertEquals(
        listOf(ballot),
        withTimeout(10_000) { voting.observeFinalBallots(tripId, vote.uid).first() },
    )
    assertEquals(
        SuggestionWithVote(suggestion, vote.copy(status = VoteStatus.CLOSED)),
        alice.repository.createSuggestionWithVote(tripId, suggestion.uid, draft.copy(name = " ")),
    )
  }

  @Test
  fun nonMemberCannotCreatePairAndNeitherDocumentIsStored() = runBlocking {
    val error = runCatching { bob.repository.createSuggestionWithVote(tripId, "proposal", draft) }
    assertTrue(error.exceptionOrNull() is ActivityCreationException.PermissionDenied)
    assertTrue(alice.tripRepository.getActivitiesForTrip(tripId).isEmpty())
    assertTrue(VoteRepositoryFirestore(alice.db).getVotes(tripId).isEmpty())
  }

  @Test
  fun suggestionsCannotBeUpdatedOrDeleted() = runBlocking {
    val document = activityDocument(alice)
    val (suggestion) = alice.repository.createSuggestionWithVote(tripId, document.id, draft)
    val fields = suggestion.toFirestoreData()

    assertPermissionDenied { document.update("name", "Changed").await() }
    assertPermissionDenied { document.delete().await() }
    assertPermissionDenied { document.set(fields).await() }
    assertEquals(fields, document.get(Source.SERVER).await().data)
  }

  @Test
  fun activityWithoutOptionalFieldsAndCountryCodeIsStored() = runBlocking {
    val minimal =
        draft.copy(
            description = null,
            endTime = null,
            location = draft.location.copy(countryCode = null),
        )

    val created = alice.repository.createActivity(tripId, "activity-1", minimal)

    assertEquals(listOf(created), alice.tripRepository.getActivitiesForTrip(tripId))
  }

  @Test
  fun retryingWithTheSameIdKeepsTheFirstActivity() = runBlocking {
    val first = alice.repository.createActivity(tripId, "activity-1", draft)
    val second = alice.repository.createActivity(tripId, "activity-1", draft.copy(name = "Other"))

    assertEquals(first, second)
    assertEquals(1, alice.tripRepository.getActivitiesForTrip(tripId).size)
  }

  @Test
  fun anotherMemberCannotTakeOverAnExistingId() = runBlocking {
    addBobToTrip()
    alice.repository.createActivity(tripId, "activity-1", draft)

    val error = runCatching { bob.repository.createActivity(tripId, "activity-1", draft) }

    assertTrue(error.exceptionOrNull() is ActivityCreationException.PermissionDenied)
    assertEquals(
        alice.userId,
        alice.tripRepository.getActivitiesForTrip(tripId).single().createdByUserId,
    )
  }

  @Test
  fun otherMemberCanCreateAndSeeEachOthersActivities() = runBlocking {
    addBobToTrip()
    alice.repository.createActivity(tripId, "by-alice", draft)
    bob.repository.createActivity(tripId, "by-bob", draft)

    assertEquals(
        setOf("by-alice", "by-bob"),
        alice.tripRepository.getActivitiesForTrip(tripId).map { it.uid }.toSet(),
    )
  }

  @Test
  fun nonMemberCannotCreateReadOrObserveActivities() = runBlocking {
    alice.repository.createActivity(tripId, "activity-1", draft)

    val error = runCatching { bob.repository.createActivity(tripId, "activity-2", draft) }

    assertTrue(error.exceptionOrNull() is ActivityCreationException.PermissionDenied)
    assertPermissionDenied { bob.tripRepository.getActivitiesForTrip(tripId) }
    assertPermissionDenied {
      withTimeout(10_000) { bob.repository.observeActivities(tripId).first() }
    }
  }

  @Test
  fun signedOutRepositoryCannotCreate() = runBlocking {
    val signedOut = ActivityRepositoryFirestore(alice.db) { null }

    val error = runCatching { signedOut.createActivity(tripId, "activity-1", draft) }

    assertTrue(error.exceptionOrNull() is ActivityCreationException.NotAuthenticated)
  }

  @Test
  fun rulesRejectUnknownTypesAuthorImpersonationAndInvalidFieldSets() = runBlocking {
    val data = activityData(alice, ActivityType.SUGGESTION)
    val invalid =
        listOf(
            data + ("activityType" to 42),
            data + ("activityType" to "OTHER"),
            data + ("createdByUserId" to bob.userId),
            data + ("extra" to true),
            data - "creationTime",
            data - "startTime",
            data - "endTime",
        )

    invalid.forEach { fields ->
      assertPermissionDenied { activityDocument(alice).set(fields).await() }
    }
  }

  @Test
  fun rulesRejectMalformedFields() = runBlocking {
    val data = activityData(alice)
    val invalid =
        listOf(
            data + ("name" to " "),
            data + ("name" to 42),
            data + ("name" to "a".repeat(MAX_ACTIVITY_NAME_LENGTH + 1)),
            data + ("description" to "a".repeat(MAX_ACTIVITY_DESCRIPTION_LENGTH + 1)),
            data + ("description" to 42),
            data + ("startTime" to "tomorrow"),
            data +
                ("endTime" to
                    Timestamp(data.getValue("startTime").let { (it as Timestamp).seconds - 1 }, 0)),
            data + ("endTime" to "later"),
            data + ("creationTime" to 1),
            data + ("location" to "EPFL"),
            data + ("location" to mapOf("name" to "EPFL", "latitude" to 91.0, "longitude" to 0.0)),
            data + ("location" to mapOf("name" to " ", "latitude" to 0.0, "longitude" to 0.0)),
            data + ("location" to mapOf("name" to "EPFL", "latitude" to 0.0)),
        )

    ActivityType.entries.forEach { type ->
      invalid.forEach { fields ->
        assertPermissionDenied {
          activityDocument(alice).set(fields + ("activityType" to type.name)).await()
        }
      }
    }
  }

  @Test
  fun rulesRejectSuggestionsWithEndButNoStart() = runBlocking {
    val invalid = activityData(alice, ActivityType.SUGGESTION) + ("startTime" to null)

    assertPermissionDenied { activityDocument(alice).set(invalid).await() }
  }

  @Test
  fun rulesRejectAcceptedActivitiesWithoutStartTime() = runBlocking {
    val data = activityData(alice)
    val invalid =
        listOf(
            data + mapOf("startTime" to null, "endTime" to null),
            data + ("startTime" to null),
            data - "startTime",
        )

    invalid.forEach { fields ->
      assertPermissionDenied { activityDocument(alice).set(fields).await() }
    }
  }

  @Test
  fun rulesAcceptEqualStartAndEndAndNullOptionalFields() =
      runBlocking<Unit> {
        val start = Timestamp(1_900_000_000, 0)

        activityDocument(alice, "same-time")
            .set(activityData(alice) + ("startTime" to start) + ("endTime" to start))
            .await()
        activityDocument(alice, "no-end")
            .set(activityData(alice) + ("endTime" to null) + ("description" to null))
            .await()
      }

  @Test
  fun rulesAcceptNameAndDescriptionAtTheirMaximumLength() =
      runBlocking<Unit> {
        activityDocument(alice, "longest")
            .set(
                activityData(alice) +
                    ("name" to "a".repeat(MAX_ACTIVITY_NAME_LENGTH)) +
                    ("description" to "a".repeat(MAX_ACTIVITY_DESCRIPTION_LENGTH))
            )
            .await()
      }

  @Test
  fun activitiesCannotBeUpdatedOrDeleted() = runBlocking {
    alice.repository.createActivity(tripId, "activity-1", draft)
    val document = activityDocument(alice, "activity-1")

    assertPermissionDenied { document.update("name", "Changed").await() }
    assertPermissionDenied { document.delete().await() }
    assertPermissionDenied { document.set(activityData(alice)).await() }
    assertEquals(draft.name, alice.tripRepository.getActivitiesForTrip(tripId).single().name)
  }

  private class User(
      val userId: String,
      val db: FirebaseFirestore,
      val tripRepository: TripRepositoryFirestore,
      val repository: ActivityRepositoryFirestore,
  )

  private fun newApp(): FirebaseApp =
      FirebaseEmulators.newApp(context, "activity-test-${UUID.randomUUID()}").also { apps += it }

  private suspend fun signInUser(): User {
    val app = newApp()
    val auth = FirebaseEmulators.auth(app)
    val db = FirebaseEmulators.firestore(app)
    val userId = auth.signInAnonymously().await().user!!.uid
    val currentUserId = { auth.currentUser?.uid }
    return User(
        userId,
        db,
        TripRepositoryFirestore(db, currentUserId),
        ActivityRepositoryFirestore(db, currentUserId),
    )
  }

  private fun tripDraft() =
      TripDraft(
          name = "Swiss trip",
          destinations = listOf(TripLocation(46.5197, 6.6323, "Lausanne")),
          startDate = Instant.ofEpochSecond(1_900_000_000),
          endDate = Instant.ofEpochSecond(1_900_300_000),
          flexibleDates = false,
          travelMode = TravelMode.GROUP,
          travelerCount = 2,
      )

  private fun activityDocument(user: User, id: String = "activity"): DocumentReference =
      user.db.collection("trips").document(tripId).collection("activities").document(id)

  // Bypasses the repository and its Kotlin validation, so only the rules decide.
  private fun activityData(
      author: User,
      type: ActivityType = ActivityType.ACCEPTED,
  ): Map<String, Any?> =
      draft.toActivity("activity", tripId, author.userId, Timestamp.now(), type).toFirestoreData()

  private fun addBobToTrip() {
    val members =
        JSONArray(listOf(alice.userId, bob.userId).map { JSONObject().put("stringValue", it) })
    val fields =
        JSONObject()
            .put("memberIds", JSONObject().put("arrayValue", JSONObject().put("values", members)))
    // Emulator-only fixture; client rules still forbid membership changes.
    val write =
        JSONObject()
            .put(
                "update",
                JSONObject()
                    .put(
                        "name",
                        "projects/$PROJECT_ID/databases/(default)/documents/trips/$tripId",
                    )
                    .put("fields", fields),
            )
            .put("updateMask", JSONObject().put("fieldPaths", JSONArray(listOf("memberIds"))))
    val body = JSONObject().put("writes", JSONArray().put(write)).toString()
    val connection =
        URL(
                "http://$HOST:$FIRESTORE_PORT/v1/projects/$PROJECT_ID/databases/(default)/documents:commit"
            )
            .openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "POST"
      connection.setRequestProperty("Authorization", "Bearer owner")
      connection.setRequestProperty("Content-Type", "application/json")
      connection.doOutput = true
      connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
      check(connection.responseCode == HttpURLConnection.HTTP_OK) {
        "Seeding Firestore failed with HTTP ${connection.responseCode}"
      }
    } finally {
      connection.disconnect()
    }
  }

  private suspend fun assertPermissionDenied(block: suspend () -> Unit) {
    val error = runCatching { block() }.exceptionOrNull()
    assertTrue(
        error.toString(),
        error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private fun clearFirestore() {
    val url =
        URL(
            "http://$HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT_ID" +
                "/databases/(default)/documents"
        )
    val connection = url.openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "DELETE"
      check(connection.responseCode == HttpURLConnection.HTTP_OK) {
        "Clearing Firestore failed with HTTP ${connection.responseCode}"
      }
    } finally {
      connection.disconnect()
    }
  }
}
