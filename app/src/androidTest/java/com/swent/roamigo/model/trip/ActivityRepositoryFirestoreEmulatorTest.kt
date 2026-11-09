// AI assistance was used for implementation support.
package com.swent.roamigo.model.trip

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.swent.roamigo.model.TripLocation
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
  fun setUp() = runBlocking {
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
  fun rulesRejectSuggestionsAndAuthorImpersonation() = runBlocking {
    val data = activityData(alice)
    val invalid =
        listOf(
            data + ("activityType" to "SUGGESTION"),
            data + ("activityType" to "OTHER"),
            data + ("createdByUserId" to bob.userId),
            data + ("extra" to true),
            data - "creationTime",
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

    invalid.forEach { fields ->
      assertPermissionDenied { activityDocument(alice).set(fields).await() }
    }
  }

  @Test
  fun rulesAcceptEqualStartAndEndAndNullOptionalFields() = runBlocking {
    val start = Timestamp(1_900_000_000, 0)

    activityDocument(alice, "same-time")
        .set(activityData(alice) + ("startTime" to start) + ("endTime" to start))
        .await()
    activityDocument(alice, "no-end")
        .set(activityData(alice) + ("endTime" to null) + ("description" to null))
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

  private suspend fun signInUser(): User {
    val appName = "activity-test-${UUID.randomUUID()}"
    val options =
        FirebaseOptions.Builder()
            .setProjectId(PROJECT_ID)
            .setApplicationId("1:1234567890:android:roamigo-activity-emulator")
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
  private fun activityData(author: User): Map<String, Any?> =
      draft.toActivity("activity", tripId, author.userId, Timestamp.now()).toFirestoreData()

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
