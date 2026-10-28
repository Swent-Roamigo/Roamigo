// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStorePhotoQueueTest {
  @get:Rule val folder = TemporaryFolder()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private lateinit var file: File
  private lateinit var store: DataStore<Preferences>
  private lateinit var queue: DataStorePhotoQueue

  @Before
  fun setUp() {
    file = File(folder.newFolder(), "photos.preferences_pb")
    store = PreferenceDataStoreFactory.create(scope = scope) { file }
    queue = DataStorePhotoQueue(store)
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test fun isEmptyAtFirst() = runTest { assertEquals(emptyList<PendingPhoto>(), queue.all()) }

  @Test
  fun keepsPhotosInTheOrderTheyWereAdded() = runTest {
    val photos = listOf(pendingPhoto("b"), pendingPhoto("a"), pendingPhoto("c"))
    photos.forEach { queue.add(it) }

    assertEquals(photos, queue.all())
  }

  @Test
  fun addingAnIdTwiceReplacesThePhoto() = runTest {
    queue.add(pendingPhoto("a", takenAtMillis = 1))
    queue.add(pendingPhoto("a", takenAtMillis = 2))

    assertEquals(listOf(pendingPhoto("a", takenAtMillis = 2)), queue.all())
  }

  @Test
  fun keepsEveryFieldIncludingThePosition() = runTest {
    val photo =
        pendingPhoto(
            "a",
            tripId = "trip",
            userId = "user",
            status = PhotoUploadStatus.FAILED,
            takenAtMillis = 1_700_000_000_123,
            latitude = 46.5191,
            longitude = -6.5668,
        )
    queue.add(photo)

    assertEquals(photo, queue.all().single())
  }

  @Test
  fun keepsAMissingPositionMissing() = runTest {
    queue.add(pendingPhoto("a"))

    val saved = queue.all().single()
    assertNull(saved.latitude)
    assertNull(saved.longitude)
  }

  @Test
  fun keepsFieldsThatContainTheSeparators() = runTest {
    val photo = pendingPhoto("a\tb", tripId = "trip\nid", userId = "ü ser/%")
    queue.add(photo)

    assertEquals(listOf(photo), queue.all())
  }

  @Test
  fun changesTheStatusOfOnePhotoOnly() = runTest {
    queue.add(pendingPhoto("a"))
    queue.add(pendingPhoto("b"))

    queue.setStatus("a", PhotoUploadStatus.UPLOADING)

    assertEquals(
        listOf(PhotoUploadStatus.UPLOADING, PhotoUploadStatus.WAITING),
        queue.all().map { it.status },
    )
  }

  @Test
  fun changingTheStatusOfAnUnknownPhotoDoesNothing() = runTest {
    queue.add(pendingPhoto("a"))

    queue.setStatus("missing", PhotoUploadStatus.FAILED)

    assertEquals(listOf(pendingPhoto("a")), queue.all())
  }

  @Test
  fun removesOnePhotoOnly() = runTest {
    queue.add(pendingPhoto("a"))
    queue.add(pendingPhoto("b"))

    queue.remove("a")

    assertEquals(listOf("b"), queue.all().map { it.id })
  }

  @Test
  fun removingAnUnknownPhotoDoesNothing() = runTest {
    queue.add(pendingPhoto("a"))

    queue.remove("missing")

    assertEquals(listOf("a"), queue.all().map { it.id })
  }

  @Test
  fun observesOnlyTheTripsPhotos() = runTest {
    queue.add(pendingPhoto("a", tripId = "t1"))
    queue.add(pendingPhoto("b", tripId = "t2"))

    assertEquals(listOf("a"), queue.observe("t1").first().map { it.id })
    assertEquals(emptyList<PendingPhoto>(), queue.observe("none").first())
  }

  @Test
  fun observingFollowsChangesAndSkipsRepeatedLists() = runBlocking {
    val emissions = Channel<List<String>>(Channel.UNLIMITED)
    val collector = scope.launch {
      queue.observe("t1").collect { emissions.send(it.map { p -> p.id }) }
    }
    suspend fun next() = withTimeout(5_000) { emissions.receive() }

    assertEquals(emptyList<String>(), next())
    queue.add(pendingPhoto("a", tripId = "t1"))
    assertEquals(listOf("a"), next())
    // Another trip's photo changes the store, but not this trip's list, so nothing is emitted for
    // it.
    queue.add(pendingPhoto("b", tripId = "t2"))
    queue.add(pendingPhoto("c", tripId = "t1"))
    assertEquals(listOf("a", "c"), next())
    collector.cancel()
  }

  @Test
  fun survivesARestart() = runTest {
    queue.add(pendingPhoto("a"))
    scope.cancel()

    val reopened =
        DataStorePhotoQueue(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO)) { file }
        )

    assertEquals(listOf("a"), reopened.all().map { it.id })
  }

  @Test
  fun skipsLinesItCannotRead() = runTest {
    val good = pendingPhoto("good")
    queue.add(good)
    store.edit { preferences ->
      val key = stringPreferencesKey("pending_photos")
      val valid = preferences[key].orEmpty()
      val fields = valid.split("\t")
      val badTime = fields.toMutableList().also { it[4] = "yesterday" }.joinToString("\t")
      val badStatus = fields.toMutableList().also { it[7] = "LOST" }.joinToString("\t")
      preferences[key] = listOf(valid, "too\tfew", badTime, badStatus, "").joinToString("\n")
    }

    assertEquals(listOf(good), queue.all())
  }
}
