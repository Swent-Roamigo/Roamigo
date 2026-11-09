// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BitmapPhotoCompressorTest {
  @get:Rule val folder = TemporaryFolder()

  /** Writes a JPEG of the given size, plain or full of noise, which does not compress well. */
  private fun jpeg(
      width: Int,
      height: Int,
      noisy: Boolean = false,
      orientation: Int? = null,
  ): File {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    if (noisy) {
      val random = Random(1)
      val pixels =
          IntArray(width * height) {
            Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
          }
      bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    } else {
      bitmap.eraseColor(Color.rgb(200, 120, 40))
    }
    val file = folder.newFile()
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    bitmap.recycle()
    if (orientation != null) {
      ExifInterface(file.path).apply {
        setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
        saveAttributes()
      }
    }
    return file
  }

  private fun sizeOf(bytes: ByteArray): Pair<Int, Int> {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    assertNotNull("the result must be a decodable image", bitmap)
    return bitmap.width to bitmap.height
  }

  private fun jpegBytesAt(width: Int, height: Int, quality: Int): Int {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val random = Random(1)
    bitmap.setPixels(
        IntArray(width * height) {
          Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
        },
        0,
        width,
        0,
        0,
        width,
        height,
    )
    return ByteArrayOutputStream()
        .also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        .size()
  }

  @Test
  fun scalesALargePhotoDownToTheLongestSide() {
    val result = BitmapPhotoCompressor().compress(jpeg(3200, 2000))

    assertEquals(1600 to 1000, sizeOf(result))
  }

  @Test
  fun scalesAPortraitPhotoByItsHeight() {
    val result = BitmapPhotoCompressor(maxEdge = 500).compress(jpeg(900, 1500))

    assertEquals(300 to 500, sizeOf(result))
  }

  @Test
  fun neverEnlargesASmallPhoto() {
    val result = BitmapPhotoCompressor().compress(jpeg(800, 600))

    assertEquals(800 to 600, sizeOf(result))
  }

  @Test
  fun keepsAPhotoAlreadyAtTheLimit() {
    val result = BitmapPhotoCompressor(maxEdge = 640).compress(jpeg(640, 480))

    assertEquals(640 to 480, sizeOf(result))
  }

  @Test
  fun staysWithinTheByteLimitForARealisticPhoto() {
    val result = BitmapPhotoCompressor().compress(jpeg(2400, 1800, noisy = true))

    assertTrue("${result.size} bytes", result.size <= BitmapPhotoCompressor.MAX_BYTES)
  }

  @Test
  fun lowersTheQualityUntilThePhotoFits() {
    val high = jpegBytesAt(600, 600, 80)
    val low = jpegBytesAt(600, 600, 40)
    val limit = (high + low) / 2

    val result = BitmapPhotoCompressor(maxBytes = limit).compress(jpeg(600, 600, noisy = true))

    assertTrue("${result.size} > $limit", result.size <= limit)
    assertTrue(result.size > low / 2)
  }

  @Test
  fun failsWhenThePhotoCannotBeMadeSmallEnough() {
    val compressor = BitmapPhotoCompressor(maxBytes = 100)

    assertThrows(IOException::class.java) { compressor.compress(jpeg(600, 600, noisy = true)) }
  }

  @Test
  fun failsOnAFileThatIsNotAnImage() {
    val file = folder.newFile().apply { writeText("not an image") }

    assertThrows(IOException::class.java) { BitmapPhotoCompressor().compress(file) }
  }

  @Test
  fun failsOnAMissingFile() {
    assertThrows(IOException::class.java) {
      BitmapPhotoCompressor().compress(File(folder.root, "missing.jpg"))
    }
  }

  @Test
  fun turnsAPhotoTheCameraTookSidewaysUpright() {
    val rotated90 = jpeg(400, 200, orientation = ExifInterface.ORIENTATION_ROTATE_90)
    val rotated270 = jpeg(400, 200, orientation = ExifInterface.ORIENTATION_ROTATE_270)

    assertEquals(200 to 400, sizeOf(BitmapPhotoCompressor().compress(rotated90)))
    assertEquals(200 to 400, sizeOf(BitmapPhotoCompressor().compress(rotated270)))
  }

  @Test
  fun keepsTheSizeForRotationsAndFlipsThatDoNotSwapTheSides() {
    listOf(
            ExifInterface.ORIENTATION_NORMAL,
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
        )
        .forEach { orientation ->
          val result = BitmapPhotoCompressor().compress(jpeg(400, 200, orientation = orientation))

          assertEquals("orientation $orientation", 400 to 200, sizeOf(result))
        }
  }

  @Test
  fun swapsTheSidesForTheTransposedOrientations() {
    listOf(ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_TRANSVERSE).forEach {
        orientation ->
      val result = BitmapPhotoCompressor().compress(jpeg(400, 200, orientation = orientation))

      assertEquals("orientation $orientation", 200 to 400, sizeOf(result))
    }
  }

  @Test
  fun scalesAfterRotatingSoTheLongestSideStillFits() {
    val result =
        BitmapPhotoCompressor(maxEdge = 300)
            .compress(jpeg(600, 400, orientation = ExifInterface.ORIENTATION_ROTATE_90))

    assertEquals(200 to 300, sizeOf(result))
  }
}
