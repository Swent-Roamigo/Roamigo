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
    val repository = repository()
    repository.savePhoto("trip", repository.newPhotoFile())

    repository.retry(repository.pendingPhotos("trip").first().single().id)
    repository.retry("unknown")

    assertEquals(
        PhotoUploadStatus.WAITING,
        repository.pendingPhotos("trip").first().single().status,
    )
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

  @Test
  fun listsTheUploadedPhotosItIsGiven() = runTest {
    val repository = repository()

    assertEquals(emptyList<TripPhoto>(), repository.tripPhotos("trip").first())
    repository.uploaded.value = listOf(tripPhoto("a"))

    assertEquals(listOf("a"), repository.tripPhotos("trip").first().map { it.id })
  }
}
