// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.trip

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.voting.Vote
import com.swent.roamigo.model.trip.voting.VoteBallot
import com.swent.roamigo.model.trip.voting.VoteOption
import com.swent.roamigo.model.trip.voting.VoteRepositoryFirestore
import com.swent.roamigo.model.trip.voting.VoteStatus
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs [TripRepositoryFirestore] against the Auth and Firestore emulators, so that the rules of
 * `firestore.rules` are enforced on every read and write.
 *
 * Requires `firebase emulators:exec --only auth,firestore --project demo-roamigo`.
 */
@RunWith(AndroidJUnit4::class)
class TripRepositoryFirestoreEmulatorTest {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private val apps = mutableListOf<FirebaseApp>()
  private val tripId = "trip-${UUID.randomUUID()}"

  private val draft =
      TripDraft(
          name = "Iberian tour",
          destinations =
              listOf(
                  TripLocation(38.7223, -9.1393, "Lisbon"),
                  TripLocation(41.1579, -8.6291, "Porto"),
              ),
          startDate = Instant.ofEpochSecond(1_900_000_000),
          endDate = Instant.ofEpochSecond(1_900_300_000),
          flexibleDates = false,
          travelMode = TravelMode.GROUP,
          travelerCount = 3,
      )

  private lateinit var alice: User
  private lateinit var bob: User

  @Before
  fun setUp() = runBlocking {
    alice = signInUser()
    bob = signInUser()
  }

  @After
  fun tearDown() {
    apps.forEach { it.delete() }
    clearFirestore()
  }

  @Test
  fun createTrip_isReadBackWithItsSingleOwner() = runBlocking {
    val created = alice.repository.createTrip(tripId, draft)

    assertEquals(listOf(created), alice.repository.getTrips().filter { it.uid == tripId })
    assertEquals(draft.destinations, created.destinations)
    val members = alice.repository.getMembersForTrip(tripId)
    assertEquals(1, members.size)
    assertEquals(alice.userId, members.single().userId)
    assertEquals(TripMemberRole.OWNER, members.single().role)
  }

  @Test
  fun createTrip_withMaximumDestinationsStaysWithinRuleLimits() = runBlocking {
    val destinations =
        List(10) { index -> TripLocation(-90.0 + index * 20, 180.0 - index * 40, "Stop $index") }

    val created = alice.repository.createTrip(tripId, draft.copy(destinations = destinations))

    assertEquals(destinations, created.destinations)
    assertEquals(listOf(created), alice.repository.getTrips().filter { it.uid == tripId })
  }

  @Test
  fun createTrip_withSameIdTwiceKeepsOneTripAndOneMember() = runBlocking {
    val first = alice.repository.createTrip(tripId, draft)
    val second = alice.repository.createTrip(tripId, draft)

    assertEquals(first, second)
    assertEquals(1, alice.repository.getTrips().count { it.uid == tripId })
    assertEquals(1, alice.repository.getMembersForTrip(tripId).size)
  }

  @Test
  fun nonMemberCannotReadTrip() = runBlocking {
    alice.repository.createTrip(tripId, draft)

    assertPermissionDenied { bob.db.collection("trips").document(tripId).get().await() }
    assertFalse(bob.repository.getTrips().any { it.uid == tripId })
  }

  @Test
  fun nonMemberCannotJoinTrip() = runBlocking {
    alice.repository.createTrip(tripId, draft)

    assertPermissionDenied { memberDocument(bob, tripId).set(ownerMemberData(bob.userId)).await() }
    assertEquals(listOf(alice.userId), alice.repository.getMembersForTrip(tripId).map { it.userId })
  }

  @Test
  fun tripOwnedByAnotherUserCannotBeWritten() = runBlocking {
    assertPermissionDenied { writeTripDirectly(bob, tripOwnedBy(alice)) }
  }

  @Test
  fun directWriteWithinTravelerBoundsAndDateOrderIsAllowed() = runBlocking {
    val validTrips =
        listOf(
            tripOwnedBy(alice).copy(travelMode = TravelMode.SOLO, travelerCount = 1),
            tripOwnedBy(alice).copy(travelMode = TravelMode.GROUP, travelerCount = 2),
            tripOwnedBy(alice).copy(travelMode = TravelMode.GROUP, travelerCount = 10),
            tripOwnedBy(alice)
                .copy(endDate = Timestamp(draft.startDate.epochSecond, draft.startDate.nano)),
        )

    validTrips.forEachIndexed { index, trip -> writeTripDirectly(alice, trip, "$tripId-$index") }
  }

  @Test
  fun directWriteWithTravelerCountOutsideItsModeIsDenied() = runBlocking {
    val invalidCounts =
        listOf(
            TravelMode.SOLO to 0,
            TravelMode.SOLO to 2,
            TravelMode.GROUP to 1,
            TravelMode.GROUP to 11,
        )

    invalidCounts.forEach { (travelMode, travelerCount) ->
      val trip = tripOwnedBy(alice).copy(travelMode = travelMode, travelerCount = travelerCount)
      assertPermissionDenied { writeTripDirectly(alice, trip) }
    }
  }

  @Test
  fun directWriteEndingBeforeItStartsIsDenied() = runBlocking {
    val endBeforeStart = Timestamp(draft.startDate.epochSecond - 1, 0)

    listOf(false, true).forEach { flexibleDates ->
      val trip = tripOwnedBy(alice).copy(endDate = endBeforeStart, flexibleDates = flexibleDates)
      assertPermissionDenied { writeTripDirectly(alice, trip) }
    }
  }

  @Test
  fun directWriteWithValidDestinationsIsAllowed() = runBlocking {
    val destinations =
        listOf(
            TripLocation(latitude = -90.0, longitude = -180.0, name = "South-west"),
            TripLocation(latitude = 90.0, longitude = 180.0, name = "North-east"),
            TripLocation(latitude = 38.7223, longitude = -9.1393, name = "Lisbon"),
        )
    val validTrips =
        listOf(
            tripOwnedBy(alice).copy(destinations = destinations),
            tripOwnedBy(alice).copy(destinations = List(10) { draft.destinations.first() }),
        )

    validTrips.forEachIndexed { index, trip -> writeTripDirectly(alice, trip, "$tripId-$index") }
  }

  @Test
  fun directWriteWithMalformedDestinationIsDenied() = runBlocking {
    val lisbon = mapOf("name" to "Lisbon", "latitude" to 38.7223, "longitude" to -9.1393)
    val malformedEntries =
        listOf(
            "Lisbon",
            lisbon - "latitude",
            lisbon + ("country" to "Portugal"),
            lisbon + ("name" to 42),
            lisbon + ("name" to " "),
            lisbon + ("latitude" to "38.7"),
            lisbon + ("longitude" to true),
        )

    malformedEntries.forEach { entry ->
      // The malformed entry comes after a valid one, so indexes beyond the first are checked too.
      assertPermissionDenied { writeTripDirectly(alice, destinations = listOf(lisbon, entry)) }
    }
  }

  @Test
  fun directWriteWithOutOfRangeCoordinatesIsDenied() = runBlocking {
    val lisbon = draft.destinations.first()
    val outOfRange =
        listOf(
            lisbon.copy(latitude = -90.1),
            lisbon.copy(latitude = 90.1),
            lisbon.copy(longitude = -180.1),
            lisbon.copy(longitude = 180.1),
        )

    outOfRange.forEach { destination ->
      val trip = tripOwnedBy(alice).copy(destinations = listOf(destination))
      assertPermissionDenied { writeTripDirectly(alice, trip) }
    }
  }

  @Test
  fun directWriteWithoutDestinationsOrWithTooManyIsDenied() = runBlocking {
    listOf(0, 11).forEach { count ->
      val trip = tripOwnedBy(alice).copy(destinations = List(count) { draft.destinations.first() })
      assertPermissionDenied { writeTripDirectly(alice, trip) }
    }
  }

  @Test
  fun memberCannotBeWrittenWithoutItsTrip() = runBlocking {
    assertPermissionDenied {
      memberDocument(alice, tripId).set(ownerMemberData(alice.userId)).await()
    }
    assertTrue(alice.repository.getTrips().none { it.uid == tripId })
  }

  @Test
  fun tripMembersCanCreateReadListAndObserveVotes() = runBlocking {
    alice.repository.createTrip(tripId, draft)
    addBobToTrip()
    val vote = voteCreatedBy(bob)
    bob.voteRepository.createVote(vote)
    // Retrying creation reads the existing vote without needing update permission.
    bob.voteRepository.createVote(vote)

    assertEquals(vote, alice.voteRepository.getVote(tripId, vote.uid))
    assertEquals(listOf(vote), bob.voteRepository.getVotes(tripId))
    assertEquals(
        listOf(vote),
        withTimeout(10_000) {
          alice.voteRepository.observeVotes(tripId).first { it == listOf(vote) }
        },
    )
  }

  @Test
  fun nonMemberCannotReadListObserveOrCreateVotes() = runBlocking {
    alice.repository.createTrip(tripId, draft)
    val vote = voteCreatedBy(alice)
    alice.voteRepository.createVote(vote)

    assertPermissionDenied { bob.voteRepository.getVote(tripId, vote.uid) }
    assertPermissionDenied { bob.voteRepository.getVotes(tripId) }
    assertPermissionDenied {
      withTimeout(10_000) { bob.voteRepository.observeVotes(tripId).first() }
    }
    assertPermissionDenied { bob.voteRepository.createVote(voteCreatedBy(bob).copy(uid = "other")) }
  }

  @Test
  fun voteCreationRejectsMismatchedIdentityAndMalformedData() = runBlocking {
    alice.repository.createTrip(tripId, draft)
    val vote = voteCreatedBy(alice)
    val data = voteData(vote)
    val invalid =
        listOf(
            data + ("uid" to "wrong"),
            data + ("tripId" to "another-trip"),
            data + ("createdByUserId" to bob.userId),
            data + ("question" to " "),
            data + ("options" to emptyList<Any>()),
            data + ("creationTime" to "invalid"),
            data + ("status" to "CLOSED"),
            data + ("closingTime" to Timestamp.now()),
            data + ("winningOptionId" to "a"),
            data + ("extra" to true),
            data - "question",
        )
    invalid.forEach { fields -> assertPermissionDenied { voteDocument(alice).set(fields).await() } }
  }

  @Test
  fun clientsCannotUpdateOrDeleteVotes() = runBlocking {
    alice.repository.createTrip(tripId, draft)
    alice.voteRepository.createVote(voteCreatedBy(alice))

    assertPermissionDenied { voteDocument(alice).update("question", "Changed?").await() }
    assertPermissionDenied { voteDocument(alice).update("status", "CLOSED").await() }
    assertPermissionDenied { voteDocument(alice).update("winningOptionId", "a").await() }
    assertPermissionDenied { voteDocument(alice).delete().await() }
  }

  @Test
  fun membersCanReadBallotsAndUpdateTheirOwnChoice() = runBlocking {
    alice.repository.createTrip(tripId, draft)
    addBobToTrip()
    alice.voteRepository.createVote(voteCreatedBy(alice))
    val ballot = VoteBallot(bob.userId, "a", Timestamp.now())
    bob.voteRepository.submitBallot(tripId, "vote", ballot)
    assertEquals(
        listOf(ballot),
        withTimeout(10_000) {
          alice.voteRepository.observeBallots(tripId, "vote").first { it == listOf(ballot) }
        },
    )

    val updated = ballot.copy(optionId = "b", updatedAt = Timestamp.now())
    bob.voteRepository.submitBallot(tripId, "vote", updated)
    assertEquals(
        listOf(updated),
        withTimeout(10_000) {
          alice.voteRepository.observeBallots(tripId, "vote").first { it == listOf(updated) }
        },
    )
  }

  @Test
  fun membersCannotCreateOrUpdateAnotherUsersBallot() = runBlocking {
    alice.repository.createTrip(tripId, draft)
    addBobToTrip()
    alice.voteRepository.createVote(voteCreatedBy(alice))
    val ballot = VoteBallot(alice.userId, "a", Timestamp.now())

    assertPermissionDenied { bob.voteRepository.submitBallot(tripId, "vote", ballot) }
    alice.voteRepository.submitBallot(tripId, "vote", ballot)
    assertPermissionDenied {
      bob.voteRepository.submitBallot(tripId, "vote", ballot.copy(optionId = "b"))
    }
    assertPermissionDenied {
      voteDocument(bob)
          .collection("ballots")
          .document(alice.userId)
          .set(ballotData(ballot.copy(userId = bob.userId)))
          .await()
    }
    assertPermissionDenied {
      voteDocument(alice).collection("ballots").document(alice.userId).delete().await()
    }
  }

  @Test
  fun nonMembersAndSignedOutUsersCannotAccessVotingData() = runBlocking {
    alice.repository.createTrip(tripId, draft)
    alice.voteRepository.createVote(voteCreatedBy(alice))
    val ballot = VoteBallot(bob.userId, "a", Timestamp.now())
    assertPermissionDenied { voteDocument(bob).collection("ballots").get().await() }
    assertPermissionDenied { bob.voteRepository.submitBallot(tripId, "vote", ballot) }

    alice.auth.signOut()
    assertPermissionDenied { alice.voteRepository.getVotes(tripId) }
    assertPermissionDenied {
      alice.voteRepository.createVote(voteCreatedBy(alice).copy(uid = "new"))
    }
    assertPermissionDenied { voteDocument(alice).collection("ballots").get().await() }
    assertPermissionDenied {
      alice.voteRepository.submitBallot(tripId, "vote", ballot.copy(userId = alice.userId))
    }
  }

  @Test
  fun ballotsRequireAnExistingVoteAndValidFields() = runBlocking {
    alice.repository.createTrip(tripId, draft)
    val ballot = VoteBallot(alice.userId, "a", Timestamp.now())
    assertPermissionDenied { alice.voteRepository.submitBallot(tripId, "missing", ballot) }
    alice.voteRepository.createVote(voteCreatedBy(alice))
    val data = ballotData(ballot)
    val invalid =
        listOf(
            data + ("userId" to bob.userId),
            data + ("optionId" to " "),
            data + ("updatedAt" to "invalid"),
            data + ("extra" to true),
            data - "optionId",
        )
    invalid.forEach { fields ->
      assertPermissionDenied {
        voteDocument(alice).collection("ballots").document(alice.userId).set(fields).await()
      }
    }
  }

  @Test
  fun ballotRulesDoNotIntroduceFutureClosedVoteRestrictions() = runBlocking {
    alice.repository.createTrip(tripId, draft)
    alice.voteRepository.createVote(voteCreatedBy(alice))
    // Seed CLOSED as trusted backend data; client vote updates remain forbidden.
    updateAsAdmin(
        "trips/$tripId/votes/vote",
        JSONObject().put("status", JSONObject().put("stringValue", "CLOSED")),
    )
    val ballot = VoteBallot(alice.userId, "a", Timestamp.now())
    alice.voteRepository.submitBallot(tripId, "vote", ballot)
    alice.voteRepository.submitBallot(tripId, "vote", ballot.copy(optionId = "b"))
    assertEquals(
        "b",
        withTimeout(10_000) {
              alice.voteRepository.observeBallots(tripId, "vote").first {
                it.singleOrNull()?.optionId == "b"
              }
            }
            .single()
            .optionId,
    )
  }

  private class User(
      val userId: String,
      val db: FirebaseFirestore,
      val repository: TripRepositoryFirestore,
      val auth: FirebaseAuth,
  ) {
    val voteRepository = VoteRepositoryFirestore(db)
  }

  private suspend fun signInUser(): User {
    val appName = "trip-test-${UUID.randomUUID()}"
    val options =
        FirebaseOptions.Builder()
            .setProjectId(PROJECT_ID)
            .setApplicationId("1:1234567890:android:roamigo-trip-emulator")
            // Firebase Auth keeps emulator settings per API key, which must not point to the
            // app of a previous test once it is deleted.
            .setApiKey("fake-api-key-$appName")
            .build()
    val app = FirebaseApp.initializeApp(context, options, appName)
    apps += app
    val auth = FirebaseAuth.getInstance(app).apply { useEmulator(EMULATOR_HOST, AUTH_PORT) }
    val db =
        FirebaseFirestore.getInstance(app).apply {
          useEmulator(EMULATOR_HOST, FIRESTORE_PORT)
          // A local cache could answer reads that the rules would deny.
          firestoreSettings =
              FirebaseFirestoreSettings.Builder()
                  .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                  .build()
        }
    val userId = auth.signInAnonymously().await().user!!.uid
    return User(userId, db, TripRepositoryFirestore(db) { auth.currentUser?.uid }, auth)
  }

  private fun voteCreatedBy(user: User) =
      Vote(
          uid = "vote",
          tripId = tripId,
          question = "Where next?",
          options = listOf(VoteOption("a", "Museum"), VoteOption("b", "Park")),
          createdByUserId = user.userId,
          status = VoteStatus.OPEN,
          creationTime = Timestamp.now(),
          closingTime = null,
          winningOptionId = null,
      )

  private fun voteDocument(user: User) =
      user.db.collection("trips").document(tripId).collection("votes").document("vote")

  private fun voteData(vote: Vote): Map<String, Any?> =
      mapOf(
          "uid" to vote.uid,
          "tripId" to vote.tripId,
          "question" to vote.question,
          "options" to vote.options.map { mapOf("uid" to it.uid, "name" to it.name) },
          "createdByUserId" to vote.createdByUserId,
          "status" to vote.status.name,
          "creationTime" to vote.creationTime,
          "closingTime" to vote.closingTime,
          "winningOptionId" to vote.winningOptionId,
      )

  private fun ballotData(ballot: VoteBallot): Map<String, Any?> =
      mapOf(
          "userId" to ballot.userId,
          "optionId" to ballot.optionId,
          "updatedAt" to ballot.updatedAt,
      )

  private fun addBobToTrip() {
    val members =
        JSONArray(listOf(alice.userId, bob.userId).map { JSONObject().put("stringValue", it) })
    updateAsAdmin(
        "trips/$tripId",
        JSONObject()
            .put("memberIds", JSONObject().put("arrayValue", JSONObject().put("values", members))),
    )
  }

  /** Emulator-only fixtures; client rules still forbid membership changes and vote closing. */
  private fun updateAsAdmin(documentPath: String, fields: JSONObject) {
    val update =
        JSONObject()
            .put("name", "projects/$PROJECT_ID/databases/(default)/documents/$documentPath")
            .put("fields", fields)
    val write =
        JSONObject()
            .put("update", update)
            .put(
                "updateMask",
                JSONObject().put("fieldPaths", JSONArray(fields.keys().asSequence().toList())),
            )
    val body = JSONObject().put("writes", JSONArray().put(write)).toString()
    val connection =
        URL(
                "http://$EMULATOR_HOST:$FIRESTORE_PORT/v1/projects/$PROJECT_ID/databases/(default)/documents:commit"
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

  private fun tripOwnedBy(owner: User) =
      Trip(
          uid = tripId,
          name = draft.name,
          description = null,
          destinations = draft.destinations,
          startDate = Timestamp(draft.startDate.epochSecond, draft.startDate.nano),
          endDate = Timestamp(draft.endDate.epochSecond, draft.endDate.nano),
          flexibleDates = draft.flexibleDates,
          travelMode = draft.travelMode,
          travelerCount = draft.travelerCount,
          ownerId = owner.userId,
          memberIds = listOf(owner.userId),
          status = TripStatus.PLANNED,
          createdAt = Timestamp.now(),
      )

  // Bypasses the repository and its Kotlin validation, so only the rules decide.
  private suspend fun writeTripDirectly(user: User, trip: Trip, id: String = tripId) {
    user.db.collection("trips").document(id).set(trip.toFirestoreData()).await()
  }

  // Writes raw destination entries that a TripLocation cannot represent.
  private suspend fun writeTripDirectly(user: User, destinations: List<Any?>) {
    val data = tripOwnedBy(user).toFirestoreData() + ("destinations" to destinations)
    user.db.collection("trips").document(tripId).set(data).await()
  }

  private fun memberDocument(user: User, tripId: String) =
      user.db.collection("trips").document(tripId).collection("members").document(user.userId)

  private fun ownerMemberData(userId: String) =
      TripMember(userId, TripMemberRole.OWNER, Timestamp.now()).toFirestoreData()

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
            "http://$EMULATOR_HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT_ID" +
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

  private companion object {
    const val PROJECT_ID = "demo-roamigo"
    const val EMULATOR_HOST = "10.0.2.2"
    const val AUTH_PORT = 9099
    const val FIRESTORE_PORT = 8080
  }
}
