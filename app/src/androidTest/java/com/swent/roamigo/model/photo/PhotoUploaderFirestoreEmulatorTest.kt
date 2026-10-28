// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.TravelMode
import com.swent.roamigo.model.trip.TripDraft
import com.swent.roamigo.model.trip.TripRepositoryFirestore
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
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
 * Runs [PhotoUploaderFirestore] against the Auth and Firestore emulators, so that the photo rules
 * of `firestore.rules` are enforced on every read and write.
 *
 * Requires `firebase emulators:exec --only auth,firestore --project demo-roamigo`.
 */
@RunWith(AndroidJUnit4::class)
class PhotoUploaderFirestoreEmulatorTest {

  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private val apps = mutableListOf<FirebaseApp>()
  private val tripId = "trip-${UUID.randomUUID()}"

  private class User(
      val userId: String,
      val db: FirebaseFirestore,
      val trips: TripRepositoryFirestore,
  )

  private lateinit var alice: User
  private lateinit var bob: User
  private lateinit var photoFile: File

  @Before
  fun setUp() =
      runBlocking<Unit> {
        alice = signInUser()
        bob = signInUser()
        alice.trips.createTrip(
            tripId,
            TripDraft(
                name = "Iberian tour",
                destinations = listOf(TripLocation(38.7223, -9.1393, "Lisbon")),
                startDate = Instant.ofEpochSecond(1_900_000_000),
                endDate = Instant.ofEpochSecond(1_900_300_000),
                flexibleDates = false,
                travelMode = TravelMode.SOLO,
                travelerCount = 1,
            ),
        )
        photoFile =
            File(context.cacheDir, "emulator-photo-${UUID.randomUUID()}.jpg").also(::writeJpeg)
      }

  @After
  fun tearDown() {
    photoFile.delete()
    apps.forEach { it.delete() }
    clearFirestore()
  }

  private fun writeJpeg(file: File) {
    val bitmap = Bitmap.createBitmap(2400, 1800, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.rgb(40, 120, 200))
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
  }

  private fun photo(owner: User, id: String = "photo-1", latitude: Double? = null) =
      PendingPhoto(
          id = id,
          tripId = tripId,
          userId = owner.userId,
          fileName = "$id.jpg",
          takenAtMillis = 1_700_000_000_000,
          latitude = latitude,
          longitude = latitude?.let { 2.0 },
      )

  private fun uploader(user: User) = PhotoUploaderFirestore({ user.db })

  private fun photoDocument(user: User, id: String = "photo-1"): DocumentReference =
      user.db.collection("trips").document(tripId).collection("photos").document(id)

  private fun validData(owner: User, image: ByteArray = ByteArray(1000) { 1 }): Map<String, Any> =
      mapOf(
          "uploadedBy" to owner.userId,
          "takenAt" to 1_700_000_000_000L,
          "image" to Blob.fromBytes(image),
      )

  @Test
  fun aMemberCanUploadAPhotoAndReadItBack() =
      runBlocking<Unit> {
        uploader(alice).upload(photo(alice, latitude = 41.5), photoFile)

        val data = photoDocument(alice).get().await().data!!
        assertEquals(alice.userId, data["uploadedBy"])
        assertEquals(1_700_000_000_000L, data["takenAt"])
        assertEquals(41.5, data["latitude"])
        val image = (data["image"] as Blob).toBytes()
        assertTrue("${image.size} bytes", image.size in 1..BitmapPhotoCompressor.MAX_BYTES)
      }

  @Test
  fun uploadingTheSamePhotoTwiceKeepsOneDocument() =
      runBlocking<Unit> {
        uploader(alice).upload(photo(alice), photoFile)
        uploader(alice).upload(photo(alice), photoFile)

        val photos =
            alice.db.collection("trips").document(tripId).collection("photos").get().await()
        assertEquals(1, photos.size())
      }

  @Test
  fun aNonMemberCannotUploadAndIsNotAskedToRetry() =
      runBlocking<Unit> {
        val failure =
            runCatching { uploader(bob).upload(photo(bob), photoFile) }.exceptionOrNull()
                as PhotoUploadException

        assertFalse(failure.retryable)
        assertFalse(photoDocument(alice).get().await().exists())
      }

  @Test
  fun aMemberCannotUploadInSomeoneElsesName() =
      runBlocking<Unit> {
        val failure =
            runCatching {
              uploader(alice).upload(photo(alice).copy(userId = bob.userId), photoFile)
            }
                .exceptionOrNull() as PhotoUploadException

        assertFalse(failure.retryable)
      }

  @Test
  fun aNonMemberCannotReadPhotos() =
      runBlocking<Unit> {
        uploader(alice).upload(photo(alice), photoFile)

        assertPermissionDenied { photoDocument(bob).get().await() }
        assertPermissionDenied {
          bob.db.collection("trips").document(tripId).collection("photos").get().await()
        }
      }

  @Test
  fun aPhotoCannotBeChanged() =
      runBlocking<Unit> {
        uploader(alice).upload(photo(alice), photoFile)

        assertPermissionDenied {
          photoDocument(alice).set(validData(alice, image = ByteArray(500) { 2 })).await()
        }
        assertPermissionDenied { photoDocument(alice).update("takenAt", 1L).await() }
      }

  @Test
  fun aPhotoCannotBeDeleted() =
      runBlocking<Unit> {
        uploader(alice).upload(photo(alice), photoFile)

        assertPermissionDenied { photoDocument(alice).delete().await() }
        assertTrue(photoDocument(alice).get().await().exists())
      }

  @Test
  fun aPhotoAboveTheSizeLimitIsRefused() =
      runBlocking<Unit> {
        assertPermissionDenied {
          photoDocument(alice).set(validData(alice, ByteArray(800_001))).await()
        }
        photoDocument(alice).set(validData(alice, ByteArray(800_000))).await()
      }

  @Test
  fun anEmptyPhotoIsRefused() =
      runBlocking<Unit> {
        assertPermissionDenied { photoDocument(alice).set(validData(alice, ByteArray(0))).await() }
      }

  @Test
  fun aPhotoWithAnUnknownFieldIsRefused() =
      runBlocking<Unit> {
        assertPermissionDenied {
          photoDocument(alice).set(validData(alice) + ("caption" to "hello")).await()
        }
      }

  @Test
  fun aPhotoWithoutItsRequiredFieldsIsRefused() =
      runBlocking<Unit> {
        listOf("uploadedBy", "takenAt", "image").forEach { missing ->
          assertPermissionDenied { photoDocument(alice).set(validData(alice) - missing).await() }
        }
      }

  @Test
  fun aPhotoWithTheWrongTypesIsRefused() =
      runBlocking<Unit> {
        assertPermissionDenied {
          photoDocument(alice).set(validData(alice) + ("takenAt" to "now")).await()
        }
        assertPermissionDenied {
          photoDocument(alice).set(validData(alice) + ("image" to "text")).await()
        }
      }

  @Test
  fun aPositionOutsideTheEarthIsRefused() =
      runBlocking<Unit> {
        assertPermissionDenied {
          photoDocument(alice).set(validData(alice) + ("latitude" to 91.0)).await()
        }
        assertPermissionDenied {
          photoDocument(alice).set(validData(alice) + ("longitude" to -181.0)).await()
        }
        photoDocument(alice)
            .set(validData(alice) + mapOf("latitude" to -90.0, "longitude" to 180.0))
            .await()
      }

  private suspend fun assertPermissionDenied(block: suspend () -> Unit) {
    val error = runCatching { block() }.exceptionOrNull()
    assertTrue(
        error.toString(),
        error is FirebaseFirestoreException &&
            error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
  }

  private suspend fun signInUser(): User {
    val appName = "photo-test-${UUID.randomUUID()}"
    val options =
        FirebaseOptions.Builder()
            .setProjectId(PROJECT_ID)
            .setApplicationId("1:1234567890:android:roamigo-photo-emulator")
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
