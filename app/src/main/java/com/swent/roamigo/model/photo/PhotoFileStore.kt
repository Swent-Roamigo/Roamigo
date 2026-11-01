// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID

/**
 * The folder holding photos until they are uploaded. It is private to the app, and the camera
 * writes into it through a `FileProvider`.
 *
 * @param directory created on first use.
 */
class PhotoFileStore(private val directory: File) {

  /** An empty file for a new photo. Its name is the photo's id, so the file and the id match. */
  fun newFile(): File {
    directory.mkdirs()
    return File(directory, "${UUID.randomUUID()}$EXTENSION")
  }

  /** The photo id that [file] was named after by [newFile]. */
  fun idOf(file: File): String = file.name.removeSuffix(EXTENSION)

  fun fileOf(photo: PendingPhoto): File = File(directory, photo.fileName)

  /**
   * Deletes [file] if it is there. A file that is already gone is not an error, and neither is one
   * that cannot be deleted: it is only a photo that stays on the device a little longer.
   */
  fun delete(file: File) {
    try {
      Files.deleteIfExists(file.toPath())
    } catch (_: IOException) {
      // Nothing else can be done with it here.
    }
  }

  companion object {
    const val EXTENSION = ".jpg"
  }
}
