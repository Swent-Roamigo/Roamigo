// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.photo

import androidx.work.Configuration
import androidx.work.WorkerParameters
import androidx.work.testing.WorkManagerTestInitHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.swent.roamigo.RoamigoApplication
import com.swent.roamigo.model.location.FakeLocationRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoModuleTest {
  private val application = RuntimeEnvironment.getApplication() as RoamigoApplication

  // Robolectric does not start Firebase by itself, and the application's location repository
  // reaches for it as soon as it is built.
  private fun startFirebase() {
    if (FirebaseApp.getApps(application).isNotEmpty()) return
    FirebaseApp.initializeApp(
        application,
        FirebaseOptions.Builder()
            .setApplicationId("1:1:android:photo-module-test")
            .setProjectId("demo-roamigo")
            .setApiKey("fake-api-key")
            .setDatabaseUrl("https://demo-roamigo-default-rtdb.firebaseio.com")
            .build(),
    )
  }

  @Test
  fun savesPhotosInsideTheAppsPrivatePhotoFolder() {
    val module = PhotoModule(application, FakeLocationRepository()) { "me" }

    val file = module.repository.newPhotoFile()

    assertEquals(File(application.filesDir, PhotoModule.PHOTO_DIRECTORY), file.parentFile)
  }

  @Test
  fun keepsASavedPhotoAndSchedulesItsUpload() {
    WorkManagerTestInitHelper.initializeTestWorkManager(
        application,
        Configuration.Builder().build(),
    )
    val module = PhotoModule(application, FakeLocationRepository()) { "me" }
    val file = module.repository.newPhotoFile().also { it.writeText("jpeg") }

    runBlocking { module.repository.savePhoto("trip", file) }

    val saved = runBlocking { module.repository.pendingPhotos("trip").first() }
    assertEquals(listOf(file.name), saved.map { it.fileName })
    assertEquals("me", saved.single().userId)
  }

  @Test
  fun theApplicationHandsWorkManagerAFactoryForThePhotoSync() {
    startFirebase()
    val worker =
        application.workManagerConfiguration.workerFactory.createWorker(
            application,
            PhotoSyncWorker::class.java.name,
            mock(WorkerParameters::class.java),
        )

    assertNotNull(worker)
    assertNull(
        application.workManagerConfiguration.workerFactory.createWorker(
            application,
            "some.other.Worker",
            mock(WorkerParameters::class.java),
        )
    )
  }

  @Test
  fun theApplicationSharesOneLocationRepositoryAndOnePhotoModule() {
    startFirebase()
    assertSame(application.locationRepository, application.locationRepository)
    assertSame(application.photos, application.photos)
  }
}
