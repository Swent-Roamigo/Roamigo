// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A photo taken offline goes through the repository, the persisted queue and the syncer. Only the
 * network is faked.
 */
class PhotoOfflineDataPathTest {
  @get:Rule val folder = TemporaryFolder()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val uploader = FakePhotoUploader()
  private lateinit var files: PhotoFileStore
  private lateinit var queue: DataStorePhotoQueue
  private lateinit var repository: DefaultPhotoRepository
  private lateinit var syncer: PhotoSyncer

  @Before
  fun setUp() {
    files = PhotoFileStore(folder.newFolder())
    val store =
        PreferenceDataStoreFactory.create(scope = scope) {
          File(folder.newFolder(), "photos.preferences_pb")
        }
    queue = DataStorePhotoQueue(store)
    repository =
        DefaultPhotoRepository(
            queue = queue,
            files = files,
            scheduler = FakePhotoScheduler(),
            gallery = { throw UnsupportedOperationException() },
            currentUserId = { "u1" },
        )
    syncer = PhotoSyncer(queue, files, uploader) { "u1" }
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  /** Saves a photo, then lets the first round fail like an offline device and the next succeed. */
  private suspend fun saveOfflineThenUploadOnline(coordinates: PhotoCoordinates?): PendingPhoto {
    val file = repository.newPhotoFile().also { it.writeText("jpeg") }
    repository.savePhoto("trip", file, takenAtMillis = 4_242L, coordinates = coordinates)

    uploader.failure = { PhotoUploadException("offline", IOException(), retryable = true) }
    assertTrue(syncer.sync())
    assertEquals(emptyList<PendingPhoto>(), uploader.uploadedPhotos)
    assertEquals(PhotoUploadStatus.FAILED, queue.all().single().status)

    uploader.failure = { null }
    syncer.sync()
    assertEquals(emptyList<PendingPhoto>(), queue.all())
    return uploader.uploadedPhotos.single()
  }

  @Test
  fun aPhotoTakenOfflineKeepsItsPositionAndTimeUntilItIsUploaded() = runTest {
    val uploaded = saveOfflineThenUploadOnline(PhotoCoordinates(46.5191, 6.5668))

    assertEquals(46.5191, uploaded.latitude)
    assertEquals(6.5668, uploaded.longitude)
    assertEquals(4_242L, uploaded.takenAtMillis)
  }

  @Test
  fun aPhotoWithoutAPositionNeverGetsOneOnTheWay() = runTest {
    val uploaded = saveOfflineThenUploadOnline(null)

    assertNull(uploaded.latitude)
    assertNull(uploaded.longitude)
    assertEquals(4_242L, uploaded.takenAtMillis)
  }
}
