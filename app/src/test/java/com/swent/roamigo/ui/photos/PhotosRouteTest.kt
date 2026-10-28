// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.connectivity.FakeConnectivityRepository
import com.swent.roamigo.model.photo.FakePhotoRepository
import com.swent.roamigo.model.photo.PhotoModule
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** A screen that can only explain a refusal once, as the system does after the first one. */
class RationaleActivity : ComponentActivity() {
  override fun shouldShowRequestPermissionRationale(permission: String) = true
}

/** Answers the permission dialog and the camera by itself, and records what was launched. */
private class FakeRegistry : ActivityResultRegistry() {
  val launched = mutableListOf<Pair<ActivityResultContract<*, *>, Any?>>()
  var permissionGranted = true
  var pictureTaken = true

  @Suppress("UNCHECKED_CAST")
  override fun <I, O> onLaunch(
      requestCode: Int,
      contract: ActivityResultContract<I, O>,
      input: I,
      options: ActivityOptionsCompat?,
  ) {
    launched += contract to input
    when (contract) {
      is ActivityResultContracts.RequestPermission ->
          dispatchResult(requestCode, permissionGranted as O)
      is ActivityResultContracts.TakePicture -> dispatchResult(requestCode, pictureTaken as O)
    }
  }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PhotosRouteTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private val registry = FakeRegistry()
  private val connectivity = FakeConnectivityRepository()
  private lateinit var repository: FakePhotoRepository
  private var tripsClicks = 0

  @Before
  fun setUp() {
    // FileProvider remembers the folder it serves per process, but every test has its own.
    FileProvider::class
        .java
        .getDeclaredField("sCache")
        .apply { isAccessible = true }
        .let { (it.get(null) as MutableMap<*, *>).clear() }
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    // The FileProvider only serves files inside the app's photo folder.
    repository = FakePhotoRepository(File(context.filesDir, PhotoModule.PHOTO_DIRECTORY))
  }

  private fun allowCamera() {
    shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
        .grantPermissions(Manifest.permission.CAMERA)
  }

  private fun show(activity: Activity? = null) {
    val owner =
        object : ActivityResultRegistryOwner {
          override val activityResultRegistry = registry
        }
    val viewModel = PhotosViewModel(repository, connectivity, "trip")
    composeRule.setContent {
      RoamigoTheme {
        CompositionLocalProvider(
            LocalActivityResultRegistryOwner provides owner,
            LocalActivity provides (activity ?: composeRule.activity),
        ) {
          PhotosRoute(
              viewModel = viewModel,
              tripName = "Lisbon",
              fileOf = repository::fileOf,
              onTripsClick = { tripsClicks++ },
          )
        }
      }
    }
  }

  private fun tap(tag: String) {
    composeRule.onNodeWithTag(tag).performClick()
    composeRule.waitForIdle()
  }

  /** Stands in for the camera app: writes the picture into the file it was asked to fill. */
  private fun writePicture(): File {
    val uri =
        launched(ActivityResultContracts.TakePicture::class.java).last().second as android.net.Uri
    val folder = File(composeRule.activity.filesDir, PhotoModule.PHOTO_DIRECTORY)
    return File(folder, uri.lastPathSegment!!).also { it.writeText("jpeg") }
  }

  private fun launched(contract: Class<*>) =
      registry.launched.filter { contract.isInstance(it.first) }

  @Test
  fun opensTheCameraStraightAwayWhenItIsAllowed() {
    allowCamera()
    show()

    tap(C.Tag.photos_take_photo)

    assertTrue(launched(ActivityResultContracts.RequestPermission::class.java).isEmpty())
    val uri =
        launched(ActivityResultContracts.TakePicture::class.java).single().second as android.net.Uri
    assertEquals("com.swent.roamigo.fileprovider", uri.authority)
    composeRule.onNodeWithTag(C.Tag.photo_preview).assertIsDisplayed()
  }

  @Test
  fun explainsThenAsksThenOpensTheCamera() {
    show()

    tap(C.Tag.photos_take_photo)
    composeRule.onNodeWithTag(C.Tag.camera_rationale_sheet).assertIsDisplayed()
    tap(C.Tag.camera_rationale_continue)

    assertEquals(
        Manifest.permission.CAMERA,
        launched(ActivityResultContracts.RequestPermission::class.java).single().second,
    )
    assertEquals(1, launched(ActivityResultContracts.TakePicture::class.java).size)
    composeRule.onNodeWithTag(C.Tag.photo_preview).assertIsDisplayed()
  }

  @Test
  fun keepsThePhotoAndGoesBackToTheList() {
    allowCamera()
    registry.pictureTaken = true
    show()
    tap(C.Tag.photos_take_photo)
    writePicture()

    tap(C.Tag.photo_preview_save)

    composeRule.onNodeWithTag(C.Tag.photos_screen).assertIsDisplayed()
    val photos = runBlocking { repository.pendingPhotos("trip").first() }
    assertEquals(1, photos.size)
    composeRule.onNodeWithTag(C.Tag.photos_item_prefix + photos.single().id).assertIsDisplayed()
  }

  @Test
  fun explainsARefusalTheSystemWillRepeat() {
    registry.permissionGranted = false
    val activity = Robolectric.buildActivity(RationaleActivity::class.java).setup().get()
    show(activity)

    tap(C.Tag.photos_take_photo)
    tap(C.Tag.camera_rationale_continue)

    composeRule.onNodeWithTag(C.Tag.camera_denied_sheet).assertIsDisplayed()
    assertTrue(launched(ActivityResultContracts.TakePicture::class.java).isEmpty())
  }

  @Test
  fun sendsTheUserToSettingsAfterAFinalRefusal() {
    registry.permissionGranted = false
    show()

    tap(C.Tag.photos_take_photo)
    tap(C.Tag.camera_rationale_continue)
    composeRule.onNodeWithTag(C.Tag.camera_blocked_sheet).assertIsDisplayed()
    tap(C.Tag.camera_blocked_open_settings)

    val intent: Intent = shadowOf(composeRule.activity).nextStartedActivity
    assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
    assertEquals("package:com.swent.roamigo", intent.data.toString())
  }

  @Test
  fun staysOnTheListWhenTheCameraIsCancelled() {
    allowCamera()
    registry.pictureTaken = false
    show()

    tap(C.Tag.photos_take_photo)

    composeRule.onNodeWithTag(C.Tag.photos_screen).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.photo_preview).assertDoesNotExist()
  }

  @Test
  fun retakingOpensTheCameraAgain() {
    allowCamera()
    show()
    tap(C.Tag.photos_take_photo)

    tap(C.Tag.photo_preview_retake)

    assertEquals(2, launched(ActivityResultContracts.TakePicture::class.java).size)
  }

  @Test
  fun backClosesThePreviewAndThrowsThePhotoAway() {
    allowCamera()
    show()
    tap(C.Tag.photos_take_photo)
    val file = writePicture()

    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeRule.waitForIdle()

    composeRule.onNodeWithTag(C.Tag.photos_screen).assertIsDisplayed()
    assertFalse(file.exists())
  }

  @Test
  fun closingThePreviewReturnsToTheList() {
    allowCamera()
    show()
    tap(C.Tag.photos_take_photo)

    tap(C.Tag.photo_preview_close)

    composeRule.onNodeWithTag(C.Tag.photos_screen).assertIsDisplayed()
  }

  @Test
  fun theTripsTabLeavesTheScreen() {
    show()

    tap(C.Tag.trips_navigation_trips)

    assertEquals(1, tripsClicks)
  }
}
