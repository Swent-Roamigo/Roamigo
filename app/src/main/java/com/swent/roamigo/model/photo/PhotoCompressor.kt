// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shrinks a photo from the camera to a size the server accepts. Full-resolution phone photos are
 * several megabytes, while the free Firebase plan stores photos in Firestore documents, which hold
 * at most 1 MiB.
 */
fun interface PhotoCompressor {
  /**
   * @return the photo as a JPEG, upright, within the size limits of this compressor.
   * @throws IOException if the file is not a readable image, or cannot be made small enough.
   */
  fun compress(file: File): ByteArray
}

/**
 * [PhotoCompressor] that scales the photo down so that its longest side is at most [maxEdge]
 * pixels, and lowers the JPEG quality until the result is at most [maxBytes]. The decoder turns the
 * photo upright from the rotation the camera recorded in the file.
 */
class BitmapPhotoCompressor(
    private val maxEdge: Int = MAX_EDGE,
    private val maxBytes: Int = MAX_BYTES,
) : PhotoCompressor {

  override fun compress(file: File): ByteArray {
    val bitmap =
        try {
          // Decoding at the final size spares the memory of a full-resolution bitmap.
          ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > maxEdge) {
              val factor = maxEdge.toFloat() / longest
              decoder.setTargetSize(
                  (info.size.width * factor).roundToInt().coerceAtLeast(1),
                  (info.size.height * factor).roundToInt().coerceAtLeast(1),
              )
            }
          }
        } catch (e: ImageDecoder.DecodeException) {
          throw IOException("${file.name} is not a readable image", e)
        }
    try {
      for (quality in QUALITIES) {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        if (out.size() <= maxBytes) return out.toByteArray()
      }
    } finally {
      bitmap.recycle()
    }
    throw IOException("The photo is still larger than $maxBytes bytes at the lowest quality")
  }

  companion object {
    /** Plenty for a phone screen and a map pin, and far below the document limit. */
    const val MAX_EDGE = 1600

    /** Leaves room under the 1 MiB of a Firestore document for the other fields. */
    const val MAX_BYTES = 700_000

    private val QUALITIES = listOf(80, 70, 60, 50, 40)
  }
}
