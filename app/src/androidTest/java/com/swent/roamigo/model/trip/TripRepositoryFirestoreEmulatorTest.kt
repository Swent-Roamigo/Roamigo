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
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
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
          startDate = Timestamp(1_900_000_000, 0),
          endDate = Timestamp(1_900_300_000, 0),
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
    val trip =
        Trip(
            uid = tripId,
            name = draft.name,
            description = null,
            destinations = draft.destinations,
            startDate = draft.startDate,
            endDate = draft.endDate,
            flexibleDates = draft.flexibleDates,
            travelMode = draft.travelMode,
            travelerCount = draft.travelerCount,
            ownerId = alice.userId,
            memberIds = listOf(alice.userId),
            status = TripStatus.PLANNED,
            createdAt = Timestamp.now(),
        )

    assertPermissionDenied {
      bob.db.collection("trips").document(tripId).set(trip.toFirestoreData()).await()
    }
  }

  @Test
  fun memberCannotBeWrittenWithoutItsTrip() = runBlocking {
    assertPermissionDenied {
      memberDocument(alice, tripId).set(ownerMemberData(alice.userId)).await()
    }
    assertTrue(alice.repository.getTrips().none { it.uid == tripId })
  }

  private class User(
      val userId: String,
      val db: FirebaseFirestore,
      val repository: TripRepositoryFirestore,
  )

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
    return User(userId, db, TripRepositoryFirestore(db) { auth.currentUser?.uid })
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
