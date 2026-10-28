// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PhotoFileStoreTest {
  @get:Rule val folder = TemporaryFolder()

  private fun store(): Pair<PhotoFileStore, File> {
    val directory = File(folder.root, "photos")
    return PhotoFileStore(directory) to directory
  }

  @Test
  fun createsItsFolderOnFirstUse() {
    val (store, directory) = store()
    assertFalse(directory.exists())

    val file = store.newFile()

    assertTrue(directory.isDirectory)
    assertEquals(directory, file.parentFile)
  }

  @Test
  fun givesEachNewFileItsOwnJpegName() {
    val (store, _) = store()

    val first = store.newFile()
    val second = store.newFile()

    assertNotEquals(first.name, second.name)
    assertTrue(first.name.endsWith(".jpg"))
  }

  @Test
  fun namesTheFileAfterThePhotoId() {
    val (store, _) = store()
    val file = store.newFile()

    assertEquals(file.name, store.idOf(file) + PhotoFileStore.EXTENSION)
  }

  @Test
  fun findsTheFileOfAPhoto() {
    val (store, directory) = store()

    assertEquals(File(directory, "p1.jpg"), store.fileOf(pendingPhoto("p1")))
  }

  @Test
  fun deletesAFile() {
    val (store, _) = store()
    val file = store.newFile().also { it.writeText("x") }

    store.delete(file)

    assertFalse(file.exists())
  }

  @Test
  fun deletingAFileThatIsGoneIsNotAnError() {
    val (store, _) = store()

    store.delete(store.newFile())
  }

  @Test
  fun deletingAFolderThatIsNotEmptyIsNotAnError() {
    val (store, directory) = store()
    val file = store.newFile()
    // A folder named like a photo cannot be deleted while it holds something.
    val folderLikeFile = File(directory, "x.jpg").apply { mkdirs() }
    File(folderLikeFile, "inside").writeText("x")

    store.delete(folderLikeFile)

    assertTrue(folderLikeFile.exists())
    assertFalse(file.exists())
  }
}
