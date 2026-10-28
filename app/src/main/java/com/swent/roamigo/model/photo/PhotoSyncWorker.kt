// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters

/**
 * Background job that uploads the waiting photos. It is scheduled under a "network connected"
 * constraint, so it only runs once the device is online, even if the app was closed meanwhile.
 */
class PhotoSyncWorker(
    context: Context,
    params: WorkerParameters,
    private val syncer: PhotoSyncer,
) : CoroutineWorker(context, params) {

  override suspend fun doWork(): Result {
    val result = syncer.sync()
    // A failure that may go away is tried again later with a backoff, but not forever: the photo
    // stays on the device and the user can retry it, or the next scheduled sync takes it.
    return if (result.shouldRetry && runAttemptCount < MAX_ATTEMPTS) Result.retry()
    else Result.success()
  }

  companion object {
    const val MAX_ATTEMPTS = 5
  }
}

/** Gives [PhotoSyncWorker] its syncer, which WorkManager cannot create by itself. */
class PhotoSyncWorkerFactory(private val syncer: () -> PhotoSyncer) : WorkerFactory() {
  override fun createWorker(
      appContext: Context,
      workerClassName: String,
      workerParameters: WorkerParameters,
  ): PhotoSyncWorker? =
      if (workerClassName == PhotoSyncWorker::class.java.name) {
        PhotoSyncWorker(appContext, workerParameters, syncer())
      } else {
        null
      }
}
