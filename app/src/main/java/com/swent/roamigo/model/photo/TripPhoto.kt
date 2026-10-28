// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

/**
 * A photo of a trip that the server has, whoever took it.
 *
 * @param id identifies the photo.
 * @param uploadedBy the user who took it.
 * @param takenAtMillis when it was taken, in epoch milliseconds.
 * @param latitude where it was taken, or null if the position was not available.
 * @param longitude see [latitude].
 * @param image the photo as a JPEG, already shrunk for upload.
 */
class TripPhoto(
    val id: String,
    val uploadedBy: String,
    val takenAtMillis: Long,
    val latitude: Double?,
    val longitude: Double?,
    val image: ByteArray,
) {
  // The bytes are compared by content, so that an unchanged photo is not shown again.
  override fun equals(other: Any?): Boolean =
      other is TripPhoto &&
          id == other.id &&
          uploadedBy == other.uploadedBy &&
          takenAtMillis == other.takenAtMillis &&
          latitude == other.latitude &&
          longitude == other.longitude &&
          image.contentEquals(other.image)

  override fun hashCode(): Int = 31 * id.hashCode() + image.contentHashCode()
}
