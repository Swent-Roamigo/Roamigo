// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.destination

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Keeps one small JSON file per destination in [directory], meant to be inside the app's cache
 * directory: the system may empty it, which only means photos are looked up again.
 */
class FileDestinationPhotoStore(private val directory: File) : DestinationPhotoStore {
  override suspend fun read(destinationId: String): CachedDestinationPhoto? =
      withContext(Dispatchers.IO) {
        val file = fileFor(destinationId)
        if (!file.isFile) return@withContext null
        try {
          parse(JSONObject(file.readText()))
        } catch (_: Exception) {
          // A damaged or foreign file is dropped so that it is replaced by the next lookup.
          discard(file)
          null
        }
      }

  override suspend fun write(destinationId: String, photo: CachedDestinationPhoto) {
    withContext(Dispatchers.IO) {
      try {
        directory.mkdirs()
        // Written aside and renamed, so a reader never sees half a file.
        val temporary = File(directory, "${fileNameOf(destinationId)}.tmp")
        temporary.writeText(encode(photo))
        if (!temporary.renameTo(fileFor(destinationId))) discard(temporary)
      } catch (_: Exception) {
        // Only a cache: the photo is shown anyway.
      }
    }
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
