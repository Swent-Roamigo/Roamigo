// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoSyncerTest {
  @get:Rule val folder = TemporaryFolder()

  private val queue = InMemoryPhotoQueue()
  private val uploader = FakePhotoUploader()
  private var userId: String? = "u1"
  private lateinit var files: PhotoFileStore
  private lateinit var syncer: PhotoSyncer

  private fun create() {
    files = PhotoFileStore(folder.newFolder())
    syncer = PhotoSyncer(queue, files, uploader) { userId }
  }

  /** Saves a photo as the camera and the repository would: its file exists and it is queued. */
  private suspend fun save(
      id: String,
      owner: String = "u1",
      status: PhotoUploadStatus = PhotoUploadStatus.WAITING,
  ): File {
    val photo = pendingPhoto(id, userId = owner, status = status)
    val file = files.fileOf(photo)
    file.writeText("jpeg")
    queue.add(photo)
    return file
  }

  @Test
  fun uploadsAPhotoThenForgetsItAndDeletesItsFile() = runTest {
    create()
    val file = save("a")

    val retryLater = syncer.sync()

    assertEquals(listOf("a"), uploader.uploaded)
    assertFalse(retryLater)
    assertEquals(emptyList<PendingPhoto>(), queue.current)
    assertFalse(file.exists())
  }

  @Test
  fun uploadsPhotosInTheOrderTheyWereSaved() = runTest {
    create()
    listOf("c", "a", "b").forEach { save(it) }

    syncer.sync()

    assertEquals(listOf("c", "a", "b"), uploader.uploaded)
  }

  @Test
  fun showsThePhotoAsUploadingWhileItIsSent() = runTest {
    create()
    save("a")
    var statusDuringUpload: PhotoUploadStatus? = null
    uploader.onUpload = { statusDuringUpload = queue.current.single().status }

    syncer.sync()

    assertEquals(PhotoUploadStatus.UPLOADING, statusDuringUpload)
  }

  @Test
  fun doesNothingWhenNobodyIsSignedIn() = runTest {
    create()
    save("a")
    userId = null

    val retryLater = syncer.sync()

    assertFalse(retryLater)
    assertEquals(emptyList<String>(), uploader.uploaded)
    assertEquals(1, queue.current.size)
  }

  @Test
  fun leavesAnotherUsersPhotosUntilTheirOwnerSignsIn() = runTest {
    create()
    val others = save("theirs", owner = "u2")
    save("mine")

    syncer.sync()

    assertEquals(listOf("mine"), uploader.uploaded)
    assertEquals(listOf("theirs"), queue.current.map { it.id })
    assertTrue(others.exists())

    userId = "u2"
    syncer.sync()

    assertEquals(listOf("mine", "theirs"), uploader.uploaded)
  }

  @Test
  fun keepsAFailedPhotoAndAsksForARetryLater() = runTest {
    create()
    val file = save("a")
    uploader.failure = { PhotoUploadException("offline", retryable = true) }

    val retryLater = syncer.sync()

    assertTrue(retryLater)
    assertEquals(PhotoUploadStatus.FAILED, queue.current.single().status)
    assertTrue(file.exists())
  }

  @Test
  fun keepsARefusedPhotoButDoesNotAskForARetry() = runTest {
    create()
    save("a")
    uploader.failure = { PhotoUploadException("not a member", retryable = false) }

    val retryLater = syncer.sync()

    assertFalse(retryLater)
    assertEquals(PhotoUploadStatus.FAILED, queue.current.single().status)
  }

  @Test
  fun treatsAnIoFailureAsOneThatMayGoAway() = runTest {
    create()
    save("a")
    uploader.failure = { IOException("reset") }

    val retryLater = syncer.sync()

    assertTrue(retryLater)
    assertEquals(PhotoUploadStatus.FAILED, queue.current.single().status)
  }

  @Test
  fun goesOnWithTheNextPhotoAfterAFailure() = runTest {
    create()
    listOf("a", "b", "c").forEach { save(it) }
    uploader.failure = { if (it.id == "b") PhotoUploadException("boom") else null }

    val retryLater = syncer.sync()

    assertEquals(listOf("a", "c"), uploader.uploaded)
    assertTrue(retryLater)
    assertEquals(listOf("b"), queue.current.map { it.id })
  }

  @Test
  fun aRefusalAndATransientFailureTogetherStillAskForARetry() = runTest {
    create()
    save("refused")
    save("flaky")
    uploader.failure = { PhotoUploadException("x", retryable = it.id == "flaky") }

    assertTrue(syncer.sync())
  }

  @Test
  fun triesAFailedPhotoAgainOnTheNextRound() = runTest {
    create()
    save("a", status = PhotoUploadStatus.FAILED)

    syncer.sync()

    assertEquals(listOf("a"), uploader.uploaded)
  }

  @Test
  fun takesUpAPhotoLeftUploadingByAProcessThatWasKilled() = runTest {
    create()
    save("a", status = PhotoUploadStatus.UPLOADING)

    syncer.sync()

    assertEquals(listOf("a"), uploader.uploaded)
  }

  @Test
  fun forgetsAPhotoWhoseFileIsGone() = runTest {
    create()
    queue.add(pendingPhoto("ghost"))
    save("a")

    assertFalse(syncer.sync())

    assertEquals(listOf("a"), uploader.uploaded)
    assertEquals(emptyList<PendingPhoto>(), queue.current)
  }

  @Test
  fun aStoppedUploadLeavesThePhotoWaitingAndRethrows() = runTest {
    create()
    val file = save("a")
    uploader.onUpload = { awaitCancellation() }

    val job = launch { syncer.sync() }
    runCurrent()
    assertEquals(PhotoUploadStatus.UPLOADING, queue.current.single().status)
    job.cancelAndJoin()

    assertTrue(job.isCancelled)
    assertEquals(PhotoUploadStatus.WAITING, queue.current.single().status)
    assertTrue(file.exists())
  }

  @Test
  fun neverRunsTwoRoundsAtOnce() = runTest {
    create()
    save("a")
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    uploader.onUpload = {
      started.complete(Unit)
      release.await()
    }

    val first = launch { syncer.sync() }
    runCurrent()
    started.await()
    val second = launch { syncer.sync() }
    runCurrent()
    release.complete(Unit)
    advanceUntilIdle()

    assertTrue(first.isCompleted && second.isCompleted)
    // The second round waited for the first, found nothing left, and did not send the photo again.
    assertEquals(listOf("a"), uploader.uploaded)
  }
}
