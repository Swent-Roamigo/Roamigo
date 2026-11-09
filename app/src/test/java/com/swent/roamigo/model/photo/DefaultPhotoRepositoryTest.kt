// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import com.swent.roamigo.model.location.DeviceLocation
import com.swent.roamigo.model.location.FakeLocationRepository
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPhotoRepositoryTest {
  @get:Rule val folder = TemporaryFolder()

  private val queue = InMemoryPhotoQueue()
  private val scheduler = FakePhotoScheduler()
  private val location = FakeLocationRepository()
  private val gallery = MutableStateFlow<List<TripPhoto>>(emptyList())
  private var userId: String? = "u1"
  private lateinit var files: PhotoFileStore
  private lateinit var repository: DefaultPhotoRepository

  private fun create(locationTimeout: kotlin.time.Duration = 5.seconds) {
    files = PhotoFileStore(folder.newFolder())
    repository =
        DefaultPhotoRepository(
            queue = queue,
            files = files,
            scheduler = scheduler,
            locationRepository = location,
            gallery = { gallery },
            currentUserId = { userId },
            clock = { 1234L },
            locationTimeout = locationTimeout,
        )
  }

  private fun takePhoto(content: String = "jpeg"): File =
      repository.newPhotoFile().also { it.writeText(content) }

  @Test
  fun savesAPhotoForTheSignedInUserAndSchedulesItsUpload() = runTest {
    create()
    val file = takePhoto()

    repository.savePhoto("trip", file)

    assertEquals(
        listOf(
            PendingPhoto(
                id = files.idOf(file),
                tripId = "trip",
                userId = "u1",
                fileName = file.name,
                takenAtMillis = 1234L,
            )
        ),
        queue.current,
    )
    assertEquals(1, scheduler.calls)
    assertTrue(file.exists())
  }

  @Test
  fun tagsThePhotoWithThePositionWhenLocationIsAllowed() = runTest {
    create()
    location.hasPermission = true
    location.currentLocation = DeviceLocation(46.5, 6.6)

    repository.savePhoto("trip", takePhoto())

    assertEquals(46.5, queue.current.single().latitude)
    assertEquals(6.6, queue.current.single().longitude)
  }

  @Test
  fun neverAsksForAPositionWithoutThePermission() = runTest {
    create()
    location.hasPermission = false
    location.currentLocation = DeviceLocation(46.5, 6.6)

    repository.savePhoto("trip", takePhoto())

    assertEquals(0, location.getCurrentLocationCalls)
    assertNull(queue.current.single().latitude)
    assertNull(queue.current.single().longitude)
  }

  @Test
  fun savesWithoutAPositionWhenNoneIsAvailable() = runTest {
    create()
    location.hasPermission = true
    location.currentLocation = null

    repository.savePhoto("trip", takePhoto())

    assertNull(queue.current.single().latitude)
  }

  @Test
  fun doesNotWaitLongerThanTheTimeoutForAPosition() = runTest {
    create(locationTimeout = 2.seconds)
    location.hasPermission = true
    val slow =
        object : com.swent.roamigo.model.location.LocationRepository by location {
          override suspend fun getCurrentLocation(): DeviceLocation? {
            delay(60.seconds)
            return DeviceLocation(1.0, 2.0)
          }
        }
    repository =
        DefaultPhotoRepository(
            queue,
            files,
            scheduler,
            slow,
            { gallery },
            { userId },
            { 1L },
            2.seconds,
        )

    repository.savePhoto("trip", takePhoto())

    assertNull(queue.current.single().latitude)
    assertEquals(1, scheduler.calls)
  }

  @Test
  fun refusesToSaveWhenNobodyIsSignedIn() = runTest {
    create()
    userId = null
    val file = takePhoto()

    assertThrows(IllegalStateException::class.java) {
      kotlinx.coroutines.runBlocking { repository.savePhoto("trip", file) }
    }

    assertEquals(emptyList<PendingPhoto>(), queue.current)
    assertEquals(0, scheduler.calls)
  }

  @Test
  fun refusesToSaveAnEmptyPhoto() = runTest {
    create()
    val file = takePhoto(content = "")

    assertThrows(IllegalStateException::class.java) {
      kotlinx.coroutines.runBlocking { repository.savePhoto("trip", file) }
    }

    assertEquals(emptyList<PendingPhoto>(), queue.current)
  }

  @Test
  fun listsTheTripsPendingPhotosOnly() = runTest {
    create()
    repository.savePhoto("trip", takePhoto())
    repository.savePhoto("other", takePhoto())

    assertEquals(1, repository.pendingPhotos("trip").first().size)
    assertEquals(emptyList<PendingPhoto>(), repository.pendingPhotos("none").first())
  }

  @Test
  fun listsOnlyThePhotosOfTheSignedInUser() = runTest {
    create()
    queue.add(pendingPhoto("mine", tripId = "trip", userId = "u1"))
    queue.add(pendingPhoto("theirs", tripId = "trip", userId = "someone-else"))

    assertEquals(listOf("mine"), repository.pendingPhotos("trip").first().map { it.id })

    userId = null
    assertEquals(emptyList<PendingPhoto>(), repository.pendingPhotos("trip").first())
  }

  @Test
  fun resumingSchedulesAnUploadWithoutChangingThePhotos() = runTest {
    create()
    queue.add(pendingPhoto("a"))

    repository.resumeUploads()

    assertEquals(1, scheduler.calls)
    assertEquals(listOf(pendingPhoto("a")), queue.current)
  }

  @Test
  fun givesTheFileOfAPhoto() = runTest {
    create()
    val file = takePhoto()
    repository.savePhoto("trip", file)

    assertEquals(file, repository.fileOf(queue.current.single()))
  }

  @Test
  fun discardsAPhotoTheUserDidNotKeep() {
    create()
    val file = takePhoto()

    repository.discard(file)

    assertFalse(file.exists())
  }

  @Test
  fun retryingPutsThePhotoBackAndSchedulesTheUpload() = runTest {
    create()
    queue.add(pendingPhoto("a", status = PhotoUploadStatus.FAILED))

    repository.retry("a")

    assertEquals(PhotoUploadStatus.WAITING, queue.current.single().status)
    assertEquals(1, scheduler.calls)
  }

  @Test
  fun listsThePhotosTheServerHasThroughTheGallery() = runTest {
    create()
    val requested = mutableListOf<String>()
    repository =
        DefaultPhotoRepository(
            queue,
            files,
            scheduler,
            location,
            { tripId ->
              requested += tripId
              gallery
            },
            { userId },
        )
    gallery.value = listOf(tripPhoto("a"), tripPhoto("b"))

    assertEquals(listOf("a", "b"), repository.tripPhotos("trip").first().map { it.id })
    assertEquals(listOf("trip"), requested)
  }
}
