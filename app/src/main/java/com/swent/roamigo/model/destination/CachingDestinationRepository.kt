// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.destination

import java.util.concurrent.TimeUnit

/**
 * Remembers what [delegate] already answered, so that the same city is not looked up on the network
 * again and again.
 *
 * - City searches are kept in memory for a few minutes, so typing the same query twice, or Trips
 *   home searching a city the form has just searched, costs nothing.
 * - Photos that were found are kept in [photoStore] for weeks, so they survive a restart.
 * - A city without a photo is remembered in memory only, and only briefly: [findPhoto] returns null
 *   both when no photo exists and when the lookup failed (for instance offline), and a failure must
 *   not hide a photo for long.
 *
 * Failed searches are never kept: their exception goes to the caller as before.
 *
 * @param delegate the source of truth, normally the online repository.
 * @param photoStore where found photos are kept between runs.
 * @param clock the current time in milliseconds, replaceable in tests.
 */
class CachingDestinationRepository(
    private val delegate: DestinationRepository,
    private val photoStore: DestinationPhotoStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : DestinationRepository {
  private val searches = LinkedHashMap<String, Pair<Long, List<Destination>>>(16, 0.75f, true)
  private val photos = HashMap<String, DestinationPhoto>()
  private val missesUntil = HashMap<String, Long>()

  override suspend fun searchDestinations(query: String): List<Destination> {
    val key = query.trim().lowercase()
    val now = clock()
    synchronized(searches) {
      searches[key]
          ?.takeIf { now - it.first < SEARCH_TTL_MILLIS }
          ?.let {
            return it.second
          }
    }
    val results = delegate.searchDestinations(query)
    synchronized(searches) {
      searches[key] = clock() to results
      if (searches.size > MAX_CACHED_SEARCHES) searches.remove(searches.keys.first())
    }
    return results
  }

  override suspend fun findPhoto(destination: Destination): DestinationPhoto? {
    val id = destination.stableId
    val now = clock()
    synchronized(photos) {
      photos[id]?.let {
        return it
      }
      if ((missesUntil[id] ?: 0L) > now) return null
    }
    photoStore
        .read(id)
        ?.takeIf { now - it.savedAtMillis < PHOTO_TTL_MILLIS }
        ?.let { stored ->
          synchronized(photos) { photos[id] = stored.photo }
          return stored.photo
        }
    val photo = delegate.findPhoto(destination)
    if (photo == null) {
      synchronized(photos) { missesUntil[id] = clock() + MISS_TTL_MILLIS }
    } else {
      synchronized(photos) { photos[id] = photo }
      photoStore.write(id, CachedDestinationPhoto(photo, clock()))
    }
    return photo
  }

  private companion object {
    val SEARCH_TTL_MILLIS = TimeUnit.MINUTES.toMillis(10)
    val PHOTO_TTL_MILLIS = TimeUnit.DAYS.toMillis(30)
    val MISS_TTL_MILLIS = TimeUnit.MINUTES.toMillis(15)
    const val MAX_CACHED_SEARCHES = 50
  }
}

/** A photo found for a destination and when it was saved, to know when to look it up again. */
data class CachedDestinationPhoto(val photo: DestinationPhoto, val savedAtMillis: Long)

/** Keeps found photos between runs of the app. Reads and writes must be safe on any thread. */
interface DestinationPhotoStore {
  /** The photo saved for [destinationId], or null when there is none or it cannot be read. */
  suspend fun read(destinationId: String): CachedDestinationPhoto?

  /** Saves [photo] for [destinationId]. Failing to save is not an error: it is only a cache. */
  suspend fun write(destinationId: String, photo: CachedDestinationPhoto)
}
