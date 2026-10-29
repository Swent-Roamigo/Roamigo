// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoUploaderFirestoreTest {
  private val firestore = mock(FirebaseFirestore::class.java)
  private val photoDocument = mock(DocumentReference::class.java)
  private val image = byteArrayOf(1, 2, 3, 4)
  private val file = File("/tmp/p1.jpg")

  init {
    val trips = mock(CollectionReference::class.java)
    val trip = mock(DocumentReference::class.java)
    val photos = mock(CollectionReference::class.java)
    `when`(firestore.collection("trips")).thenReturn(trips)
    `when`(trips.document("t1")).thenReturn(trip)
    `when`(trip.collection("photos")).thenReturn(photos)
    `when`(photos.document("p1")).thenReturn(photoDocument)
  }

  private fun uploader(compress: (File) -> ByteArray = { image }) =
      PhotoUploaderFirestore({ firestore }, PhotoCompressor(compress))

  private fun writeSucceeds() {
    `when`(photoDocument.set(any(Any::class.java))).thenReturn(Tasks.forResult(null))
  }

  private fun writeFails(code: Code) {
    `when`(photoDocument.set(any(Any::class.java)))
        .thenReturn(Tasks.forException(FirebaseFirestoreException("failed", code)))
  }

  private fun writtenData(): Map<*, *> {
    val captor = ArgumentCaptor.forClass(Any::class.java)
    verify(photoDocument).set(captor.capture())
    return captor.value as Map<*, *>
  }

  private suspend fun failureOf(upload: suspend () -> Unit): PhotoUploadException =
      runCatching { upload() }.exceptionOrNull() as PhotoUploadException

  @Test
  fun writesTheCompressedPhotoUnderTheTripsPhotos() = runTest {
    writeSucceeds()

    uploader().upload(pendingPhoto("p1", tripId = "t1", userId = "u9", takenAtMillis = 42), file)

    assertEquals(
        mapOf(
            PhotoUploaderFirestore.UPLOADED_BY to "u9",
            PhotoUploaderFirestore.TAKEN_AT to 42L,
            PhotoUploaderFirestore.IMAGE to Blob.fromBytes(image),
        ),
        writtenData(),
    )
  }

  @Test
  fun recordsThePositionWhenThereIsOne() = runTest {
    writeSucceeds()

    uploader().upload(pendingPhoto("p1", tripId = "t1", latitude = 1.5, longitude = -2.5), file)

    assertEquals(1.5, writtenData()[PhotoUploaderFirestore.LATITUDE])
    assertEquals(-2.5, writtenData()[PhotoUploaderFirestore.LONGITUDE])
  }

  @Test
  fun leavesOutALonePositionCoordinate() = runTest {
    writeSucceeds()

    uploader().upload(pendingPhoto("p1", tripId = "t1", latitude = 1.5), file)

    assertFalse(writtenData().containsKey(PhotoUploaderFirestore.LATITUDE))
    assertFalse(writtenData().containsKey(PhotoUploaderFirestore.LONGITUDE))
  }

  @Test
  fun shrinksThePhotoOnTheDispatcherItWasGiven() = runTest {
    writeSucceeds()
    var thread: Thread? = null
    val dispatcher = StandardTestDispatcher(testScheduler)

    PhotoUploaderFirestore(
            { firestore },
            PhotoCompressor {
              thread = Thread.currentThread()
              image
            },
            dispatcher,
        )
        .upload(pendingPhoto("p1", tripId = "t1"), file)

    assertEquals(Thread.currentThread(), thread)
  }

  @Test
  fun compressesTheFileItWasGiven() = runTest {
    writeSucceeds()
    var compressed: File? = null

    uploader {
          compressed = it
          image
        }
        .upload(pendingPhoto("p1", tripId = "t1"), file)

    assertSame(file, compressed)
  }

  @Test
  fun doesNotTouchFirebaseBeforeTheFirstUpload() {
    var lookups = 0
    PhotoUploaderFirestore({
      lookups++
      firestore
    })

    assertEquals(0, lookups)
  }

  @Test
  fun aServerRefusalIsFinal() = runTest {
    listOf(
            Code.PERMISSION_DENIED,
            Code.UNAUTHENTICATED,
            Code.INVALID_ARGUMENT,
            Code.FAILED_PRECONDITION,
        )
        .forEach { code ->
          writeFails(code)

          val failure = failureOf { uploader().upload(pendingPhoto("p1", tripId = "t1"), file) }

          assertFalse("$code", failure.retryable)
          assertEquals(code, (failure.cause as FirebaseFirestoreException).code)
        }
  }

  @Test
  fun aFailureThatMayGoAwayCanBeRetried() = runTest {
    listOf(Code.UNAVAILABLE, Code.DEADLINE_EXCEEDED, Code.RESOURCE_EXHAUSTED, Code.ABORTED)
        .forEach { code ->
          writeFails(code)

          val failure = failureOf { uploader().upload(pendingPhoto("p1", tripId = "t1"), file) }

          assertTrue("$code", failure.retryable)
        }
  }

  @Test
  fun aPhotoThatCannotBePreparedIsNeverWrittenAndNeverRetried() = runTest {
    val failure = failureOf {
      uploader { throw IOException("not an image") }.upload(pendingPhoto("p1", tripId = "t1"), file)
    }

    assertFalse(failure.retryable)
    assertTrue(failure.cause is IOException)
    verify(photoDocument, never()).set(any(Any::class.java))
  }

  @Test
  fun aStoppedUploadIsNotReportedAsAFailure() = runTest {
    // A write that is still waiting for the server when the caller gives up.
    `when`(photoDocument.set(any(Any::class.java))).thenReturn(TaskCompletionSource<Void>().task)
    var thrown: Throwable? = null

    // Not on the test dispatcher: the test thread blocks below while the photo is prepared.
    val job =
        launch(Dispatchers.Default) {
          try {
            uploader().upload(pendingPhoto("p1", tripId = "t1"), file)
          } catch (e: Throwable) {
            thrown = e
            throw e
          }
        }
    // The photo is prepared on another thread, so wait until the write is really pending.
    verify(photoDocument, timeout(5_000)).set(any(Any::class.java))
    job.cancelAndJoin()

    assertTrue(thrown is CancellationException)
  }

  @Test
  fun otherFailuresAreNotHidden() {
    `when`(photoDocument.set(any(Any::class.java)))
        .thenReturn(Tasks.forException(IllegalStateException("bug")))

    assertThrows(IllegalStateException::class.java) {
      kotlinx.coroutines.runBlocking { uploader().upload(pendingPhoto("p1", tripId = "t1"), file) }
    }
  }
}
