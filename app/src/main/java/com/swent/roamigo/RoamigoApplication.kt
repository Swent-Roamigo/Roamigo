// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo

import android.app.Application
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.util.DebugLogger
import com.swent.roamigo.model.destination.DESTINATION_USER_AGENT
import com.swent.roamigo.model.location.DefaultLocationRepository
import com.swent.roamigo.model.location.LocationRepository
import com.swent.roamigo.model.photo.PhotoModule
import com.swent.roamigo.model.photo.PhotoSyncWorkerFactory
import okhttp3.OkHttpClient

class RoamigoApplication : Application(), ImageLoaderFactory, Configuration.Provider {
  // One instance for the whole app, as the repository requires.
  val locationRepository: LocationRepository by lazy { DefaultLocationRepository(this) }

  // Outside any screen, because the uploads run in the background.
  val photos: PhotoModule by lazy { PhotoModule(this, locationRepository) }

  override val workManagerConfiguration: Configuration
    get() =
        Configuration.Builder().setWorkerFactory(PhotoSyncWorkerFactory { photos.syncer }).build()

  override fun newImageLoader(): ImageLoader =
      ImageLoader.Builder(this)
          .okHttpClient(::destinationImageHttpClient)
          .logger(if (BuildConfig.DEBUG) DebugLogger() else null)
          .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve(DESTINATION_IMAGE_CACHE_DIRECTORY))
                .maxSizeBytes(MAX_IMAGE_CACHE_BYTES)
                .build()
          }
          .build()

  internal fun destinationImageHttpClient(): OkHttpClient =
      OkHttpClient.Builder()
          .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder().header("User-Agent", DESTINATION_USER_AGENT).build()
            )
          }
          .build()

  companion object {
    private const val MAX_IMAGE_CACHE_BYTES = 50L * 1024 * 1024
    private const val DESTINATION_IMAGE_CACHE_DIRECTORY = "destination-images"
  }
}
