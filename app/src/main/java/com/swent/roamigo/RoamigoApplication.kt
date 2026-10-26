// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.util.DebugLogger
import com.swent.roamigo.model.destination.DESTINATION_USER_AGENT
import okhttp3.OkHttpClient

class RoamigoApplication : Application(), ImageLoaderFactory {
  override fun newImageLoader(): ImageLoader =
      ImageLoader.Builder(this)
          .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                  chain.proceed(
                      chain
                          .request()
                          .newBuilder()
                          .header(
                              "User-Agent",
                              DESTINATION_USER_AGENT,
                          )
                          .build()
                  )
                }
                .build()
          }
          .logger(if (BuildConfig.DEBUG) DebugLogger() else null)
          .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve(DESTINATION_IMAGE_CACHE_DIRECTORY))
                .maxSizeBytes(MAX_IMAGE_CACHE_BYTES)
                .build()
          }
          .build()

  companion object {
    private const val MAX_IMAGE_CACHE_BYTES = 50L * 1024 * 1024
    private const val DESTINATION_IMAGE_CACHE_DIRECTORY = "destination-images"
  }
}
