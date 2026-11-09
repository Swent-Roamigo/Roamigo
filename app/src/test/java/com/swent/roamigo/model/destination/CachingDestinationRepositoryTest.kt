// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.destination

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

// Robolectric supplies org.json, which the file store uses.
@RunWith(AndroidJUnit4::class)
class CachingDestinationRepositoryTest {
  @get:Rule val folder = TemporaryFolder()

  private val lisbon =
      Destination("geonames:1", DestinationKind.CITY, "Lisbon", "Portugal", "PT", 38.7, -9.1, 1)
  private val photo =
      DestinationPhoto(
          imageUrl = "https://example.org/lisbon.jpg",
          creator = "Ana",
          sourceUrl = "https://example.org/file",
          license = "CC BY 4.0",
          licenseUrl = "https://creativecommons.org/licenses/by/4.0/",
          title = "Lisbon",
          credit = null,
      )
  private var now = 1_000_000L
  private val delegate = CountingRepository()
  private val store = InMemoryStore()

  private fun repository(store: DestinationPhotoStore = this.store) =
      CachingDestinationRepository(delegate, store) { now }

  @Test
  fun aRepeatedSearchIsAnsweredWithoutTheNetwork() = runTest {
    val repository = repository()
    delegate.results = listOf(lisbon)

    assertEquals(listOf(lisbon), repository.searchDestinations("lis"))
    assertEquals(listOf(lisbon), repository.searchDestinations("  LIS "))

    assertEquals(1, delegate.searches)
  }

  @Test
  fun aSearchIsRepeatedOnceItsAnswerIsOld() = runTest {
    val repository = repository()
    repository.searchDestinations("lis")

    now += TimeUnit.MINUTES.toMillis(11)
    repository.searchDestinations("lis")

    assertEquals(2, delegate.searches)
  }

  @Test
  fun aFailedSearchIsNotRemembered() = runTest {
    val repository = repository()
    delegate.searchError = IOException("offline")
    try {
      repository.searchDestinations("lis")
      fail("The failure should reach the caller")
    } catch (_: IOException) {}

    delegate.searchError = null
    delegate.results = listOf(lisbon)

    assertEquals(listOf(lisbon), repository.searchDestinations("lis"))
  }

  @Test
  fun aFoundPhotoIsLookedUpOnceAndSurvivesARestart() = runTest {
    delegate.photo = photo

    assertEquals(photo, repository().findPhoto(lisbon))
    assertEquals(photo, repository().findPhoto(lisbon))

    assertEquals(1, delegate.photoLookups)
    assertEquals(1, store.writes)
  }

  @Test
  fun aSavedPhotoIsLookedUpAgainAfterThirtyDays() = runTest {
    delegate.photo = photo
    repository().findPhoto(lisbon)

    now += TimeUnit.DAYS.toMillis(31)
    repository().findPhoto(lisbon)

    assertEquals(2, delegate.photoLookups)
  }

  @Test
  fun aMissingPhotoIsRetriedAfterAShortWhileAndNeverSaved() = runTest {
    val repository = repository()
    assertNull(repository.findPhoto(lisbon))
    assertNull(repository.findPhoto(lisbon))
    assertEquals(1, delegate.photoLookups)
    assertEquals(0, store.writes)

    delegate.photo = photo
    now += TimeUnit.MINUTES.toMillis(16)

    assertEquals(photo, repository.findPhoto(lisbon))
    assertEquals(2, delegate.photoLookups)
  }

  @Test
  fun filePhotosRoundTripWithMissingFields() = runTest {
    val files = FileDestinationPhotoStore(folder.newFolder("photos"))
    val minimal = photo.copy(creator = null, licenseUrl = null, title = null)

    files.write("geonames:1", CachedDestinationPhoto(minimal, 42L))

    assertEquals(CachedDestinationPhoto(minimal, 42L), files.read("geonames:1"))
    assertNull(files.read("geonames:2"))
  }

  @Test
  fun aDamagedOrUnsafeFileIsIgnoredAndRemoved() = runTest {
    val directory = folder.newFolder("photos")
    val files = FileDestinationPhotoStore(directory)
    files.write("geonames:1", CachedDestinationPhoto(photo, 1L))
    val saved = directory.listFiles()!!.single { it.extension == "json" }

    saved.writeText("{not json")
    assertNull(files.read("geonames:1"))
    assertTrue(!saved.exists())

    saved.writeText(
        """{"savedAt":1,"imageUrl":"file:///etc/passwd","sourceUrl":"https://x.org","license":"CC0"}"""
    )
    assertNull(files.read("geonames:1"))
  }

  @Test
  fun aPhotoKeptInFilesIsFoundByANewRepository() = runTest {
    val directory = folder.newFolder("photos")
    delegate.photo = photo
    repository(FileDestinationPhotoStore(directory)).findPhoto(lisbon)

    val found = repository(FileDestinationPhotoStore(directory)).findPhoto(lisbon)

    assertEquals(photo, found)
    assertEquals(1, delegate.photoLookups)
  }

  @Test
  fun theOldestSearchesAreForgottenBeyondFifty() = runTest {
    val repository = repository()
    repeat(51) { repository.searchDestinations("city $it") }

    repository.searchDestinations("city 50")
    assertEquals(51, delegate.searches)
    repository.searchDestinations("city 0")

    assertEquals(52, delegate.searches)
  }

  @Test
  fun aFoundPhotoIsAnsweredFromMemoryTheSecondTime() = runTest {
    val repository = repository()
    delegate.photo = photo
    repository.findPhoto(lisbon)

    delegate.photo = null
    val again = repository.findPhoto(lisbon)

    assertEquals(photo, again)
    assertEquals(1, delegate.photoLookups)
  }

  @Test
  fun aPhotoWithAllItsCreditsSurvivesTheFile() = runTest {
    val directory = folder.newFolder("credits")
    val credited = photo.copy(credit = "Photo: Ana", creator = null, title = null)
    FileDestinationPhotoStore(directory).write("geonames:1", CachedDestinationPhoto(credited, 7L))

    val read = FileDestinationPhotoStore(directory).read("geonames:1")

    assertEquals(CachedDestinationPhoto(credited, 7L), read)
  }

  @Test
  fun aFileWithANonHttpsSourceLinkIsIgnored() = runTest {
    val directory = folder.newFolder("source")
    val store = FileDestinationPhotoStore(directory)
    store.write(
        "geonames:1",
        CachedDestinationPhoto(photo.copy(sourceUrl = "http://example.org/file"), 7L),
    )

    assertNull(store.read("geonames:1"))
  }

  @Test
  fun failingToWriteTheFileDoesNotFailTheLookup() = runTest {
    // A plain file where the folder should be: nothing can be written under it.
    val blocked = folder.newFile("blocked")

    FileDestinationPhotoStore(blocked).write("geonames:1", CachedDestinationPhoto(photo, 7L))

    assertNull(FileDestinationPhotoStore(blocked).read("geonames:1"))
  }

  private class CountingRepository : DestinationRepository {
    var results: List<Destination> = emptyList()
    var searchError: Exception? = null
    var photo: DestinationPhoto? = null
    var searches = 0
    var photoLookups = 0

    override suspend fun searchDestinations(query: String): List<Destination> {
      searches++
      searchError?.let { throw it }
      return results
    }

    override suspend fun findPhoto(destination: Destination): DestinationPhoto? {
      photoLookups++
      return photo
    }
  }

  private class InMemoryStore : DestinationPhotoStore {
    private val saved = HashMap<String, CachedDestinationPhoto>()
    var writes = 0

    override suspend fun read(destinationId: String) = saved[destinationId]

    override suspend fun write(destinationId: String, photo: CachedDestinationPhoto) {
      writes++
      saved[destinationId] = photo
    }
  }
}
