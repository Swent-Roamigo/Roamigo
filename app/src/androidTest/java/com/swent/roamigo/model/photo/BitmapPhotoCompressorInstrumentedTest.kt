// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs [BitmapPhotoCompressor] with the decoder of a real device, which the JVM tests simulate. */
@RunWith(AndroidJUnit4::class)
class BitmapPhotoCompressorInstrumentedTest {
  private val directory = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
  private val files = mutableListOf<File>()

  @After
  fun tearDown() {
    files.forEach { it.delete() }
  }

  private fun jpeg(width: Int, height: Int, orientation: Int? = null): File {
    val file = File.createTempFile("compressor", ".jpg", directory).also { files += it }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    orientation?.let {
      ExifInterface(file.path).apply {
        setAttribute(ExifInterface.TAG_ORIENTATION, it.toString())
        saveAttributes()
      }
    }
    return file
  }

  private fun sizeOf(bytes: ByteArray) =
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size).let { it.width to it.height }

  @Test
  fun scalesALargePhotoDownToTheLongestSide() {
    assertEquals(1600 to 1000, sizeOf(BitmapPhotoCompressor().compress(jpeg(3200, 2000))))
  }

  @Test
  fun neverEnlargesASmallPhoto() {
    assertEquals(800 to 600, sizeOf(BitmapPhotoCompressor().compress(jpeg(800, 600))))
  }

  @Test
  fun turnsAPhotoTheCameraTookSidewaysUpright() {
    val sideways = jpeg(400, 200, ExifInterface.ORIENTATION_ROTATE_90)

    assertEquals(200 to 400, sizeOf(BitmapPhotoCompressor().compress(sideways)))
  }

  @Test
  fun scalesAfterRotatingSoTheLongestSideStillFits() {
    val sideways = jpeg(600, 400, ExifInterface.ORIENTATION_ROTATE_90)

    assertEquals(200 to 300, sizeOf(BitmapPhotoCompressor(maxEdge = 300).compress(sideways)))
  }

  @Test
  fun staysWithinTheByteLimit() {
    assertTrue(BitmapPhotoCompressor().compress(jpeg(3200, 2000)).size <= 700_000)
  }

  @Test
  fun failsOnAFileThatIsNotAnImage() {
    val text =
        File.createTempFile("compressor", ".jpg", directory).also {
          files += it
          it.writeText("not an image")
        }

    assertThrows(IOException::class.java) { BitmapPhotoCompressor().compress(text) }
  }
}
