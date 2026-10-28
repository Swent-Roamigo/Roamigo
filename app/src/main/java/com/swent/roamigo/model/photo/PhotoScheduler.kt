// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Asks for the waiting photos to be uploaded as soon as the device is online. */
interface PhotoScheduler {
  fun schedule()
}

/** [PhotoScheduler] that runs a [PhotoSyncWorker] once the device has a network connection. */
class PhotoSchedulerWorkManager(private val workManager: () -> WorkManager) : PhotoScheduler {

  override fun schedule() {
    val request =
        OneTimeWorkRequestBuilder<PhotoSyncWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
    // A photo saved while an upload runs must not be missed: the new work starts after the running
    // one, instead of being dropped or cancelling it.
    workManager().enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
  }

  companion object {
    const val WORK_NAME = "photo-sync"
    private const val BACKOFF_SECONDS = 30L
  }
}
