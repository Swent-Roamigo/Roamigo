// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FakePhotoRepositoryTest {
  @get:Rule val folder = TemporaryFolder()

  private fun repository() = FakePhotoRepository(File(folder.root, "photos"), "me") { 7L }

  @Test
  fun keepsSavedPhotosPerTrip() = runTest {
    val repository = repository()
    val file = repository.newPhotoFile()
    repository.savePhoto("trip", file)
    repository.savePhoto("other", repository.newPhotoFile())

    val photos = repository.pendingPhotos("trip").first()

    assertEquals(1, photos.size)
    assertEquals("me", photos.single().userId)
    assertEquals(7L, photos.single().takenAtMillis)
    assertEquals(file.name, photos.single().fileName)
    assertEquals(file, repository.fileOf(photos.single()))
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
    val file = repository.newPhotoFile()
    repository.savePhoto("trip", file)

    assertEquals("alex", repository.pendingPhotos("trip").first().single().userId)
    repository.discard(file)
  }
}
