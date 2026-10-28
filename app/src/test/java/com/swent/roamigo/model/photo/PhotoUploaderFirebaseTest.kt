// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoUploaderFirebaseTest {
  private val storage = mock(FirebaseStorage::class.java)
  private val root = mock(StorageReference::class.java)
  private val photoReference = mock(StorageReference::class.java)
  private val file = File("/tmp/p1.jpg")

  init {
    `when`(storage.reference).thenReturn(root)
    `when`(root.child("trips/t1/photos/p1.jpg")).thenReturn(photoReference)
  }

  private fun uploading(task: UploadTask) {
    `when`(photoReference.putFile(any(Uri::class.java), any(StorageMetadata::class.java)))
        .thenReturn(task)
  }

  /** A finished upload task, which is what `await` returns from without waiting. */
  private fun finished(error: Exception? = null): UploadTask {
    val task = mock(UploadTask::class.java)
    `when`(task.isComplete).thenReturn(true)
    `when`(task.exception).thenReturn(error)
    `when`(task.isCanceled).thenReturn(false)
    `when`(task.result).thenReturn(mock(UploadTask.TaskSnapshot::class.java))
    return task
  }

  @Test
  fun uploadsTheFileUnderTheTripsPhotos() = runTest {
    uploading(finished())

    PhotoUploaderFirebase { storage }.upload(pendingPhoto("p1", tripId = "t1"), file)

    val uri = ArgumentCaptor.forClass(Uri::class.java)
    verify(photoReference).putFile(uri.capture(), any(StorageMetadata::class.java))
    assertEquals(file.path, uri.value.path)
  }

  @Test
  fun recordsWhoTookThePhotoAndWhenAndWhere() = runTest {
    uploading(finished())

    PhotoUploaderFirebase { storage }
        .upload(
            pendingPhoto("p1", userId = "u9", takenAtMillis = 42, latitude = 1.5, longitude = -2.5),
            file,
        )

    val metadata = ArgumentCaptor.forClass(StorageMetadata::class.java)
    verify(photoReference).putFile(any(Uri::class.java), metadata.capture())
    assertEquals("image/jpeg", metadata.value.contentType)
    assertEquals("u9", metadata.value.getCustomMetadata(PhotoUploaderFirebase.UPLOADED_BY))
    assertEquals("42", metadata.value.getCustomMetadata(PhotoUploaderFirebase.TAKEN_AT))
    assertEquals("1.5", metadata.value.getCustomMetadata(PhotoUploaderFirebase.LATITUDE))
    assertEquals("-2.5", metadata.value.getCustomMetadata(PhotoUploaderFirebase.LONGITUDE))
  }

  @Test
  fun leavesThePositionOutWhenThereIsNone() = runTest {
    uploading(finished())

    PhotoUploaderFirebase { storage }.upload(pendingPhoto("p1"), file)

    val metadata = ArgumentCaptor.forClass(StorageMetadata::class.java)
    verify(photoReference).putFile(any(Uri::class.java), metadata.capture())
    assertNull(metadata.value.getCustomMetadata(PhotoUploaderFirebase.LATITUDE))
    assertNull(metadata.value.getCustomMetadata(PhotoUploaderFirebase.LONGITUDE))
  }

  @Test
  fun aNetworkFailureMayBeTriedAgain() = runTest {
    val failure = StorageException.fromException(java.io.IOException("offline"))
    uploading(finished(failure))

    val error =
        runCatching { PhotoUploaderFirebase { storage }.upload(pendingPhoto("p1"), file) }
            .exceptionOrNull() as PhotoUploadException

    assertTrue(error.retryable)
    assertSame(failure, error.cause)
  }

  @Test
  fun aRefusalByTheRulesIsFinal() = runTest {
    val refusal = StorageException.fromExceptionAndHttpCode(null, 403)!!
    assertEquals(StorageException.ERROR_NOT_AUTHORIZED, refusal.errorCode)
    uploading(finished(refusal))

    val error =
        runCatching { PhotoUploaderFirebase { storage }.upload(pendingPhoto("p1"), file) }
            .exceptionOrNull() as PhotoUploadException

    assertFalse(error.retryable)
  }

  @Test
  fun aMissingSignInIsFinal() = runTest {
    uploading(finished(StorageException.fromExceptionAndHttpCode(null, 401)!!))

    val error =
        runCatching { PhotoUploaderFirebase { storage }.upload(pendingPhoto("p1"), file) }
            .exceptionOrNull() as PhotoUploadException

    assertFalse(error.retryable)
  }

  @Test
  fun aStoppedUploadIsNotReportedAsAFailure() = runTest {
    // A task that never completes: the upload is still running when the caller gives up.
    val pending = mock(UploadTask::class.java)
    `when`(pending.isComplete).thenReturn(false)
    uploading(pending)
    var thrown: Throwable? = null

    val job = launch {
      try {
        PhotoUploaderFirebase { storage }.upload(pendingPhoto("p1"), file)
      } catch (e: Throwable) {
        thrown = e
        throw e
      }
    }
    runCurrent()
    job.cancel()
    runCurrent()

    assertTrue(thrown is CancellationException)
  }

  @Test
  fun otherFailuresAreNotHidden() {
    uploading(finished(IllegalStateException("bug")))

    assertThrows(IllegalStateException::class.java) {
      kotlinx.coroutines.runBlocking {
        PhotoUploaderFirebase { storage }.upload(pendingPhoto("p1"), file)
      }
    }
  }
}
