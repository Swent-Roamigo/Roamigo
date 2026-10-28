// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
 * pixels, applies the rotation the camera recorded in the file, and lowers the JPEG quality until
 * the result is at most [maxBytes].
 */
class BitmapPhotoCompressor(
    private val maxEdge: Int = MAX_EDGE,
    private val maxBytes: Int = MAX_BYTES,
) : PhotoCompressor {

  override fun compress(file: File): ByteArray {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
      throw IOException("${file.name} is not a readable image")
    }
    // Decoding at a fraction of the size spares the memory of a full-resolution bitmap.
    val options =
        BitmapFactory.Options().apply {
          inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
    val decoded =
        BitmapFactory.decodeFile(file.path, options)
            ?: throw IOException("${file.name} could not be decoded")
    val upright = decoded.transformed(orientationMatrix(file))
    val scaled = upright.scaledDown()
    try {
      return encode(scaled)
    } finally {
      scaled.recycle()
    }
  }

  private fun sampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (max(width, height) / (sample * 2) >= maxEdge) sample *= 2
    return sample
  }

  private fun Bitmap.scaledDown(): Bitmap {
    val longest = max(width, height)
    if (longest <= maxEdge) return this
    val factor = maxEdge.toFloat() / longest
    return Bitmap.createScaledBitmap(
            this,
            (width * factor).roundToInt().coerceAtLeast(1),
            (height * factor).roundToInt().coerceAtLeast(1),
            true,
        )
        .also { if (it !== this) recycle() }
  }

  private fun Bitmap.transformed(matrix: Matrix?): Bitmap {
    if (matrix == null) return this
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also {
      if (it !== this) recycle()
    }
  }

  private fun encode(bitmap: Bitmap): ByteArray {
    for (quality in QUALITIES) {
      val out = ByteArrayOutputStream()
      bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
      if (out.size() <= maxBytes) return out.toByteArray()
    }
    throw IOException("The photo is still larger than $maxBytes bytes at the lowest quality")
  }

  /** The matrix that turns the photo upright, or null if the camera recorded no rotation. */
  private fun orientationMatrix(file: File): Matrix? {
    val orientation =
        try {
          ExifInterface(file.path)
              .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: IOException) {
          ExifInterface.ORIENTATION_NORMAL
        }
    val matrix = Matrix()
    when (orientation) {
      ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
      ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
      ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
      ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
      ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
      ExifInterface.ORIENTATION_TRANSPOSE -> {
        matrix.postRotate(90f)
        matrix.postScale(-1f, 1f)
      }
      ExifInterface.ORIENTATION_TRANSVERSE -> {
        matrix.postRotate(-90f)
        matrix.postScale(-1f, 1f)
      }
      else -> return null
    }
    return matrix
  }

  companion object {
    /** Plenty for a phone screen and a map pin, and far below the document limit. */
    const val MAX_EDGE = 1600

    /** Leaves room under the 1 MiB of a Firestore document for the other fields. */
    const val MAX_BYTES = 700_000

    private val QUALITIES = listOf(80, 70, 60, 50, 40)
  }
}
