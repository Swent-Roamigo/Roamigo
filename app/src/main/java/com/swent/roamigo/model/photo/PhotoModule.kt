// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import android.content.Context
import androidx.work.WorkManager
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.swent.roamigo.model.location.LocationRepository
import java.io.File

/**
 * The photo pipeline, built once per app process. It lives outside any screen because WorkManager
 * needs the [syncer] to upload photos while no screen is open.
 *
 * @param locationRepository the app-wide location repository, used to tag photos.
 * @param currentUserId the signed-in user, or null.
 */
class PhotoModule(
    context: Context,
    locationRepository: LocationRepository,
    currentUserId: () -> String? = { Firebase.auth.currentUser?.uid },
) {
  private val appContext = context.applicationContext
  private val files = PhotoFileStore(File(appContext.filesDir, PHOTO_DIRECTORY))
  private val queue = DataStorePhotoQueue(appContext)

  val syncer = PhotoSyncer(queue, files, PhotoUploaderFirestore(), currentUserId)
  // WorkManager starts only when a photo is saved or a sync is scheduled, not with the app.
  val scheduler: PhotoScheduler = PhotoSchedulerWorkManager { WorkManager.getInstance(appContext) }
  val repository: PhotoRepository =
      DefaultPhotoRepository(
          queue = queue,
          files = files,
          scheduler = scheduler,
          locationRepository = locationRepository,
          gallery = PhotoGalleryFirestore(),
          currentUserId = currentUserId,
      )

  companion object {
    const val PHOTO_DIRECTORY = "pending-photos"
  }
}
