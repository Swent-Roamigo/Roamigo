// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoSyncWorkerTest {
  @get:Rule val folder = TemporaryFolder()

  private val context: Context = ApplicationProvider.getApplicationContext()
  private val queue = InMemoryPhotoQueue()
  private val uploader = FakePhotoUploader()
  private lateinit var files: PhotoFileStore
  private lateinit var syncer: PhotoSyncer

  @Before
  fun setUp() {
    files = PhotoFileStore(folder.newFolder())
    syncer = PhotoSyncer(queue, files, uploader) { "u1" }
  }

  private fun saveOnePhoto(): File {
    val photo = pendingPhoto("a")
    return files.fileOf(photo).also {
      it.writeText("jpeg")
      runBlocking { queue.add(photo) }
    }
  }

  private fun run(attempt: Int = 0): ListenableWorker.Result = runBlocking {
    TestListenableWorkerBuilder<PhotoSyncWorker>(context)
        .setWorkerFactory(PhotoSyncWorkerFactory { syncer })
        .setRunAttemptCount(attempt)
        .build()
        .doWork()
  }

  @Test
  fun uploadsTheWaitingPhotosAndSucceeds() {
    saveOnePhoto()

    val result = run()

    assertEquals(ListenableWorker.Result.success(), result)
    assertEquals(listOf("a"), uploader.uploaded)
  }

  @Test
  fun succeedsWhenThereIsNothingToUpload() {
    assertEquals(ListenableWorker.Result.success(), run())
  }

  @Test
  fun asksForARetryWhenAFailureMayGoAway() {
    saveOnePhoto()
    uploader.failure = { PhotoUploadException("offline") }

    assertEquals(ListenableWorker.Result.retry(), run())
  }

  @Test
  fun givesUpRetryingAfterTheLastAttempt() {
    saveOnePhoto()
    uploader.failure = { PhotoUploadException("offline") }

    // The attempt count starts at 0: the run with MAX_ATTEMPTS - 1 is the last of MAX_ATTEMPTS.
    assertEquals(ListenableWorker.Result.retry(), run(attempt = PhotoSyncWorker.MAX_ATTEMPTS - 2))
    assertEquals(ListenableWorker.Result.success(), run(attempt = PhotoSyncWorker.MAX_ATTEMPTS - 1))
    // The photo is still on the device for the next sync or a manual retry.
    assertEquals(1, queue.current.size)
  }

  @Test
  fun doesNotRetryARefusal() {
    saveOnePhoto()
    uploader.failure = { PhotoUploadException("not a member", retryable = false) }

    assertEquals(ListenableWorker.Result.success(), run())
  }

  @Test
  fun theFactoryBuildsOnlyItsOwnWorker() {
    val factory = PhotoSyncWorkerFactory { syncer }
    val parameters = mock(WorkerParameters::class.java)

    assertNotNull(factory.createWorker(context, PhotoSyncWorker::class.java.name, parameters))
    assertNull(factory.createWorker(context, "some.other.Worker", parameters))
    assertTrue(PhotoSyncWorker.MAX_ATTEMPTS > 1)
  }
}
