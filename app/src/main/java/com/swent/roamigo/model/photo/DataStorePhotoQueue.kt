// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * [PhotoQueue] saved in a DataStore, so the photos waiting survive the app being closed or killed.
 * The queue is short and short-lived, which is why it is not a database.
 *
 * Each photo is one line of tab-separated, URL-encoded fields. A line that cannot be read is
 * skipped instead of failing the whole queue.
 */
class DataStorePhotoQueue(private val store: DataStore<Preferences>) : PhotoQueue {

  constructor(context: Context) : this(context.pendingPhotosDataStore)

  private val photos: Flow<List<PendingPhoto>> = store.data.map { decode(it[KEY]) }

  override fun observe(tripId: String): Flow<List<PendingPhoto>> =
      photos.map { list -> list.filter { it.tripId == tripId } }.distinctUntilChanged()

  override suspend fun all(): List<PendingPhoto> = photos.first()

  override suspend fun add(photo: PendingPhoto) = update { list ->
    list.filterNot { it.id == photo.id } + photo
  }

  override suspend fun setStatus(id: String, status: PhotoUploadStatus) = update { list ->
    list.map { if (it.id == id) it.copy(status = status) else it }
  }

  override suspend fun remove(id: String) = update { list -> list.filterNot { it.id == id } }

  private suspend fun update(transform: (List<PendingPhoto>) -> List<PendingPhoto>) {
    store.edit { preferences -> preferences[KEY] = encode(transform(decode(preferences[KEY]))) }
  }

  private fun encode(photos: List<PendingPhoto>): String =
      photos.joinToString(LINE) { photo ->
        listOf(
                photo.id,
                photo.tripId,
                photo.userId,
                photo.fileName,
                photo.takenAtMillis.toString(),
                photo.latitude?.toString().orEmpty(),
                photo.longitude?.toString().orEmpty(),
                photo.status.name,
            )
            .joinToString(FIELD) { URLEncoder.encode(it, CHARSET) }
      }

  private fun decode(text: String?): List<PendingPhoto> =
      text.orEmpty().lines().mapNotNull { line -> decodeLine(line) }

  private fun decodeLine(line: String): PendingPhoto? {
    val fields = line.split(FIELD).map { URLDecoder.decode(it, CHARSET) }
    if (fields.size != FIELD_COUNT) return null
    val takenAt = fields[4].toLongOrNull() ?: return null
    val status = PhotoUploadStatus.entries.firstOrNull { it.name == fields[7] } ?: return null
    return PendingPhoto(
        id = fields[0],
        tripId = fields[1],
        userId = fields[2],
        fileName = fields[3],
        takenAtMillis = takenAt,
        latitude = fields[5].toDoubleOrNull(),
        longitude = fields[6].toDoubleOrNull(),
        status = status,
    )
  }

  private companion object {
    val KEY = stringPreferencesKey("pending_photos")
    const val LINE = "\n"
    const val FIELD = "\t"
    const val FIELD_COUNT = 8
    const val CHARSET = "UTF-8"
  }
}

// A single instance per file is allowed in a process, hence the extension property.
private val Context.pendingPhotosDataStore by preferencesDataStore(name = "pending_photos")
