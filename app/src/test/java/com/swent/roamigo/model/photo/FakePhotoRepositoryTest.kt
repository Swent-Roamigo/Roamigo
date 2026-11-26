// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FakePhotoRepositoryTest {
  @get:Rule val folder = TemporaryFolder()

  private fun repository() = FakePhotoRepository(File(folder.root, "photos"), "me")

  @Test
  fun keepsSavedPhotosPerTrip() = runTest {
    val repository = repository()
    val file = repository.newPhotoFile().also { it.writeText("jpeg") }
    repository.savePhoto("trip", file, 7L, PhotoCoordinates(46.5, 6.6))
    repository.savePhoto("other", repository.newPhotoFile().also { it.writeText("jpeg") }, 8L, null)

    val photos = repository.pendingPhotos("trip").first()

    assertEquals(1, photos.size)
    assertEquals("me", photos.single().userId)
    assertEquals(7L, photos.single().takenAtMillis)
    assertEquals(file.name, photos.single().fileName)
    assertEquals(46.5, photos.single().latitude)
    assertEquals(6.6, photos.single().longitude)
    assertEquals(file, repository.fileOf(photos.single()))
  }

  @Test
  fun refusesToSaveAnEmptyPhotoLikeTheRealRepository() = runTest {
    val repository = repository()
    val file = repository.newPhotoFile()

    assertThrows(IllegalStateException::class.java) {
      kotlinx.coroutines.runBlocking { repository.savePhoto("trip", file, 1L, null) }
    }

    assertEquals(emptyList<PendingPhoto>(), repository.pendingPhotos("trip").first())
  }

  @Test
  fun startsEmpty() = runTest {
    assertEquals(emptyList<PendingPhoto>(), repository().pendingPhotos("trip").first())
  }

  @Test
  fun retryingWaitsAgainOnlyThatPhoto() = runTest {
    val failed = { id: String ->
      pendingPhoto(id, userId = "me", status = PhotoUploadStatus.FAILED)
    }
    val repository =
        FakePhotoRepository(
            File(folder.root, "photos"),
            "me",
            initialPhotos = listOf(failed("a"), failed("b")),
        )

    repository.retry("a")
    repository.retry("unknown")

    val statuses = repository.pendingPhotos("t1").first().associate { it.id to it.status }
    assertEquals(
        mapOf("a" to PhotoUploadStatus.WAITING, "b" to PhotoUploadStatus.FAILED),
        statuses,
    )
  }

  @Test
  fun listsOnlyThePhotosOfItsUser() = runTest {
    val repository =
        FakePhotoRepository(
            File(folder.root, "photos"),
            "me",
            initialPhotos = listOf(pendingPhoto("mine", userId = "me"), pendingPhoto("theirs")),
        )

    assertEquals(listOf("mine"), repository.pendingPhotos("t1").first().map { it.id })
    repository.resumeUploads()
  }

  @Test
  fun discardsAFile() {
    val repository = repository()
    val file = repository.newPhotoFile().also { it.writeText("x") }

    repository.discard(file)

    assertFalse(file.exists())
  }

  @Test
  fun hasUsableDefaults() = runTest {
    val repository = FakePhotoRepository()
    val file = repository.newPhotoFile().also { it.writeText("jpeg") }
    repository.savePhoto("trip", file, 1L, null)

    assertEquals("alex", repository.pendingPhotos("trip").first().single().userId)
    repository.discard(file)
  }

  @Test
  fun listsTheUploadedPhotosItIsGiven() = runTest {
    val repository = repository()

    assertEquals(emptyList<TripPhoto>(), repository.tripPhotos("trip").first())
    repository.setUploaded(listOf(tripPhoto("a")))

    assertEquals(listOf("a"), repository.tripPhotos("trip").first().map { it.id })
  }
}
