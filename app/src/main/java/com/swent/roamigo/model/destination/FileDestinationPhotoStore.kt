// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.destination

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps one small JSON file per destination in [directory], meant to be inside the app's cache
 * directory: the system may empty it, which only means photos are looked up again.
 *
 * @param ioDispatcher where the file work runs, replaceable in tests.
 */
class FileDestinationPhotoStore(
    private val directory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DestinationPhotoStore {
  override suspend fun read(destinationId: String): CachedDestinationPhoto? =
      withContext(ioDispatcher) {
        val file = fileFor(destinationId)
        if (!file.isFile) return@withContext null
        try {
          // A file that parses but is not trusted (a link that is not https) is removed as well.
          parse(JSONObject(file.readText())).also { if (it == null) discard(file) }
        } catch (_: Exception) {
          // A damaged or foreign file is dropped so that it is replaced by the next lookup.
          discard(file)
          null
        }
      }

  override suspend fun write(destinationId: String, photo: CachedDestinationPhoto) {
    withContext(ioDispatcher) {
      try {
        directory.mkdirs()
        // Written aside and renamed, so a reader never sees half a file.
        val temporary = File(directory, "${fileNameOf(name)}.tmp")
        temporary.writeText(content)
        if (!temporary.renameTo(fileFor(name))) discard(temporary)
      } catch (_: Exception) {
        // Only a cache: the result is used anyway.
      }
    }
  }

  override suspend fun readSearch(query: String): CachedSearch? =
      withContext(ioDispatcher) {
        val file = fileFor("search_$query")
        if (!file.isFile) return@withContext null
        try {
          parseSearch(JSONObject(file.readText()))
        } catch (_: Exception) {
          discard(file)
          null
        }
      }

  override suspend fun writeSearch(query: String, search: CachedSearch) {
    writeAtomically("search_$query", encodeSearch(search))
  }

  private fun encodeSearch(search: CachedSearch): String =
      JSONObject()
          .put("savedAt", search.savedAtMillis)
          .put(
              "destinations",
              JSONArray(
                  search.destinations.map {
                    JSONObject()
                        .put("name", it.name)
                        .put("countryName", it.countryName)
                        .put("countryCode", it.countryCode)
                        .put("lat", it.latitude)
                        .put("lng", it.longitude)
                        .put("geonameId", it.geonameId)
                  }
              ),
          )
          .toString()

  private fun parseSearch(json: JSONObject): CachedSearch? {
    val items = json.getJSONArray("destinations")
    val destinations =
        (0 until items.length()).map { index ->
          val item = items.getJSONObject(index)
          val id = item.getInt("geonameId")
          Destination(
              stableId = "geonames:$id",
              kind = DestinationKind.CITY,
              name = item.getString("name"),
              countryName = item.getString("countryName"),
              countryCode = item.getString("countryCode"),
              latitude = item.getDouble("lat"),
              longitude = item.getDouble("lng"),
              geonameId = id,
          )
        }
    return CachedSearch(destinations, json.getLong("savedAt"))
  }

  /** Removes [file] if it is there. It only frees space, so a failure to remove it is ignored. */
  private fun discard(file: File) {
    try {
      Files.deleteIfExists(file.toPath())
    } catch (_: IOException) {
      // Only a cache: the file is replaced or ignored on the next lookup.
    }
  }

  private fun fileFor(destinationId: String) = File(directory, fileNameOf(destinationId))

  private fun fileNameOf(destinationId: String) =
      destinationId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json"

  private fun encode(cached: CachedDestinationPhoto): String =
      JSONObject()
          .put("savedAt", cached.savedAtMillis)
          .put("imageUrl", cached.photo.imageUrl)
          .put("creator", cached.photo.creator ?: JSONObject.NULL)
          .put("sourceUrl", cached.photo.sourceUrl)
          .put("license", cached.photo.license)
          .put("licenseUrl", cached.photo.licenseUrl ?: JSONObject.NULL)
          .put("title", cached.photo.title ?: JSONObject.NULL)
          .put("credit", cached.photo.credit ?: JSONObject.NULL)
          .toString()

  private fun parse(json: JSONObject): CachedDestinationPhoto? {
    val imageUrl = json.getString("imageUrl")
    val sourceUrl = json.getString("sourceUrl")
    // The file is outside the app's control, so a link that is not web is not trusted.
    if (!imageUrl.startsWith("https://") || !sourceUrl.startsWith("https://")) return null
    return CachedDestinationPhoto(
        photo =
            DestinationPhoto(
                imageUrl = imageUrl,
                creator = json.optionalString("creator"),
                sourceUrl = sourceUrl,
                license = json.getString("license"),
                licenseUrl = json.optionalString("licenseUrl"),
                title = json.optionalString("title"),
                credit = json.optionalString("credit"),
            ),
        savedAtMillis = json.getLong("savedAt"),
    )
  }

  private fun JSONObject.optionalString(name: String): String? =
      if (isNull(name)) null else getString(name)
}
