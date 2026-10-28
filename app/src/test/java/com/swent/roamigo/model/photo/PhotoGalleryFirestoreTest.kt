// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoGalleryFirestoreTest {
  private val firestore = mock(FirebaseFirestore::class.java)
  private val registration = mock(ListenerRegistration::class.java)
  private var listener: EventListener<QuerySnapshot>? = null
  private var requestedLimit = -1L
  private var lookups = 0

  init {
    val trips = mock(CollectionReference::class.java)
    val trip = mock(DocumentReference::class.java)
    val photos = mock(CollectionReference::class.java)
    val ordered = mock(Query::class.java)
    val limited = mock(Query::class.java)
    `when`(firestore.collection("trips")).thenReturn(trips)
    `when`(trips.document("t1")).thenReturn(trip)
    `when`(trip.collection("photos")).thenReturn(photos)
    `when`(photos.orderBy("takenAt", Query.Direction.DESCENDING)).thenReturn(ordered)
    `when`(ordered.limit(anyLong())).thenAnswer {
      requestedLimit = it.getArgument(0)
      limited
    }
    `when`(limited.addSnapshotListener(any<EventListener<QuerySnapshot>>())).thenAnswer {
      listener = it.getArgument(0)
      registration
    }
  }

  private fun gallery(limit: Long = PhotoGalleryFirestore.LIMIT) =
      PhotoGalleryFirestore(
          {
            lookups++
            firestore
          },
          limit,
      )

  private fun document(
      id: String,
      uploadedBy: String? = "u1",
      takenAt: Long? = 1_000L,
      image: ByteArray? = byteArrayOf(1, 2),
      latitude: Double? = null,
      longitude: Double? = null,
  ): DocumentSnapshot {
    val document = mock(DocumentSnapshot::class.java)
    `when`(document.id).thenReturn(id)
    `when`(document.getString("uploadedBy")).thenReturn(uploadedBy)
    `when`(document.getLong("takenAt")).thenReturn(takenAt)
    `when`(document.getBlob("image")).thenReturn(image?.let { Blob.fromBytes(it) })
    `when`(document.getDouble("latitude")).thenReturn(latitude)
    `when`(document.getDouble("longitude")).thenReturn(longitude)
    return document
  }

  private fun snapshot(vararg documents: DocumentSnapshot): QuerySnapshot {
    val snapshot = mock(QuerySnapshot::class.java)
    `when`(snapshot.documents).thenReturn(documents.toList())
    return snapshot
  }

  @Test
  fun readsTheLatestPhotosOfTheTripNewestFirst() = runTest {
    val received = mutableListOf<List<TripPhoto>>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          gallery(limit = 7).observe("t1").collect { received += it }
        }

    assertEquals(7L, requestedLimit)
    listener!!.onEvent(snapshot(document("a", takenAt = 5), document("b", takenAt = 3)), null)

    assertEquals(listOf(listOf("a", "b")), received.map { photos -> photos.map { it.id } })
    job.cancel()
  }

  @Test
  fun turnsADocumentIntoAPhoto() = runTest {
    val received = mutableListOf<List<TripPhoto>>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          gallery().observe("t1").collect { received += it }
        }

    listener!!.onEvent(
        snapshot(document("a", "u9", 42, byteArrayOf(7, 8), latitude = 1.5, longitude = -2.5)),
        null,
    )

    assertEquals(
        listOf(tripPhoto("a", "u9", 42, 1.5, -2.5, byteArrayOf(7, 8))),
        received.single(),
    )
    job.cancel()
  }

  @Test
  fun keepsAMissingPositionMissing() = runTest {
    val received = mutableListOf<List<TripPhoto>>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          gallery().observe("t1").collect { received += it }
        }

    listener!!.onEvent(snapshot(document("a")), null)

    assertNull(received.single().single().latitude)
    assertNull(received.single().single().longitude)
    job.cancel()
  }

  @Test
  fun skipsDocumentsThatAreNotPhotos() = runTest {
    val received = mutableListOf<List<TripPhoto>>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          gallery().observe("t1").collect { received += it }
        }

    listener!!.onEvent(
        snapshot(
            document("good"),
            document("no-image", image = null),
            document("no-user", uploadedBy = null),
            document("no-time", takenAt = null),
        ),
        null,
    )

    assertEquals(listOf("good"), received.single().map { it.id })
    job.cancel()
  }

  @Test
  fun emitsAgainWhenAPhotoIsAdded() = runTest {
    val received = mutableListOf<List<String>>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          gallery().observe("t1").collect { received += it.map { photo -> photo.id } }
        }

    listener!!.onEvent(snapshot(document("a")), null)
    listener!!.onEvent(snapshot(document("b"), document("a")), null)

    assertEquals(listOf(listOf("a"), listOf("b", "a")), received)
    job.cancel()
  }

  @Test
  fun failsWhenThePhotosCannotBeRead() = runTest {
    var failure: Throwable? = null
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          gallery().observe("t1").catch { failure = it }.collect {}
        }
    val denied = FirebaseFirestoreException("no", FirebaseFirestoreException.Code.PERMISSION_DENIED)

    listener!!.onEvent(null, denied)

    assertEquals(denied, failure)
    assertTrue(job.isCompleted)
  }

  @Test
  fun ignoresAnEventWithNeitherPhotosNorError() = runTest {
    val received = mutableListOf<List<TripPhoto>>()
    val job =
        launch(UnconfinedTestDispatcher(testScheduler)) {
          gallery().observe("t1").collect { received += it }
        }

    listener!!.onEvent(null, null)

    assertTrue(received.isEmpty())
    job.cancel()
  }

  @Test
  fun stopsListeningWhenNobodyCollectsAnymore() = runTest {
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { gallery().observe("t1").collect {} }
    verify(registration, never()).remove()

    job.cancel()
    testScheduler.advanceUntilIdle()

    verify(registration).remove()
  }

  @Test
  fun doesNotTouchFirebaseBeforeTheFirstTripIsObserved() {
    gallery()

    assertEquals(0, lookups)
  }

  @Test
  fun readsAFewPhotosByDefault() = runTest {
    val job = launch(UnconfinedTestDispatcher(testScheduler)) { gallery().observe("t1").collect {} }

    assertEquals(50L, requestedLimit)
    job.cancel()
  }
}
