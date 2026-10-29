// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import com.swent.roamigo.model.connectivity.FakeConnectivityRepository
import com.swent.roamigo.model.photo.pendingPhoto
import com.swent.roamigo.model.photo.tripPhoto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class PhotosViewModelTest {
  @get:Rule val folder = TemporaryFolder()

  private val dispatcher = UnconfinedTestDispatcher()
  private val connectivity = FakeConnectivityRepository()
  private lateinit var repository: TestPhotoRepository
  private lateinit var viewModel: PhotosViewModel
  private val effects = mutableListOf<PhotosEffect>()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    repository = TestPhotoRepository(folder.newFolder())
    viewModel = PhotosViewModel(repository, connectivity, "trip")
    viewModel.effects
        .onEach { effects += it }
        .launchIn(kotlinx.coroutines.CoroutineScope(dispatcher))
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  private val state
    get() = viewModel.uiState.value

  /** Takes a photo the way the screen does, with the camera allowed and a picture written. */
  private fun takePicture(): java.io.File {
    viewModel.onTakePhotoClick(permissionGranted = true)
    val file = (effects.last() as PhotosEffect.LaunchCamera).file
    file.writeText("jpeg")
    viewModel.onPictureTaken(true)
    return file
  }

  @Test
  fun startsOnlineWithNothingWaiting() {
    assertEquals(PhotosUiState(), state)
  }

  @Test
  fun asksForTheWaitingPhotosToBeUploadedWhenItOpens() {
    assertEquals(1, repository.resumed)
  }

  @Test
  fun showsTheTripsWaitingPhotos() {
    repository.photos.value =
        listOf(pendingPhoto("a", tripId = "trip"), pendingPhoto("b", tripId = "x"))

    assertEquals(listOf("a"), state.photos.map { it.id })
  }

  @Test
  fun followsTheConnection() {
    connectivity.online.value = false
    assertFalse(state.isOnline)

    connectivity.online.value = true
    assertTrue(state.isOnline)
  }

  @Test
  fun opensTheCameraDirectlyWhenItIsAllowed() {
    viewModel.onTakePhotoClick(permissionGranted = true)

    val launch = effects.single() as PhotosEffect.LaunchCamera
    assertEquals(repository.created.single(), launch.file)
    assertEquals(CameraStep.NONE, state.cameraStep)
  }

  @Test
  fun explainsTheCameraBeforeAskingForIt() {
    viewModel.onTakePhotoClick(permissionGranted = false)

    assertEquals(CameraStep.RATIONALE, state.cameraStep)
    assertEquals(emptyList<PhotosEffect>(), effects)
  }

  @Test
  fun asksTheSystemOnceTheUserAgrees() {
    viewModel.onTakePhotoClick(permissionGranted = false)

    viewModel.onRationaleContinue()

    assertEquals(CameraStep.NONE, state.cameraStep)
    assertEquals(listOf<PhotosEffect>(PhotosEffect.RequestCameraPermission), effects)
  }

  @Test
  fun doesNotAskWhenTheUserSaysNotNow() {
    viewModel.onTakePhotoClick(permissionGranted = false)

    viewModel.onStepDismissed()

    assertEquals(CameraStep.NONE, state.cameraStep)
    assertEquals(emptyList<PhotosEffect>(), effects)
  }

  @Test
  fun opensTheCameraWhenThePermissionIsGranted() {
    viewModel.onCameraPermissionResult(granted = true, shouldShowRationale = false)

    assertTrue(effects.single() is PhotosEffect.LaunchCamera)
  }

  @Test
  fun aRefusalTheSystemWillRepeatIsDenied() {
    viewModel.onCameraPermissionResult(granted = false, shouldShowRationale = true)

    assertEquals(CameraStep.DENIED, state.cameraStep)
    assertEquals(emptyList<PhotosEffect>(), effects)
  }

  @Test
  fun aFinalRefusalIsBlocked() {
    viewModel.onCameraPermissionResult(granted = false, shouldShowRationale = false)

    assertEquals(CameraStep.BLOCKED, state.cameraStep)
  }

  @Test
  fun sendsTheUserToSettingsWhenBlocked() {
    viewModel.onCameraPermissionResult(granted = false, shouldShowRationale = false)

    viewModel.onOpenSettings()

    assertEquals(CameraStep.NONE, state.cameraStep)
    assertEquals(listOf<PhotosEffect>(PhotosEffect.OpenSettings), effects)
  }

  @Test
  fun showsThePhotoJustTaken() {
    val file = takePicture()

    assertEquals(file, state.preview)
    assertFalse(state.saveFailed)
  }

  @Test
  fun throwsAwayAPhotoTheUserCancelled() {
    viewModel.onTakePhotoClick(permissionGranted = true)
    val file = (effects.last() as PhotosEffect.LaunchCamera).file

    viewModel.onPictureTaken(false)

    assertNull(state.preview)
    assertEquals(listOf(file), repository.discarded)
  }

  @Test
  fun ignoresACameraResultItDidNotAskFor() {
    viewModel.onPictureTaken(true)

    assertNull(state.preview)
    assertEquals(emptyList<java.io.File>(), repository.discarded)
  }

  @Test
  fun savesThePhotoToTheTrip() = runTest {
    val file = takePicture()

    viewModel.onSave()

    assertEquals(listOf("trip" to file), repository.saved)
    assertNull(state.preview)
    assertFalse(state.saving)
  }

  @Test
  fun keepsThePhotoInThePreviewWhenSavingFails() {
    val file = takePicture()
    repository.saveError = IllegalStateException("no user")

    viewModel.onSave()

    assertEquals(file, state.preview)
    assertTrue(state.saveFailed)
    assertFalse(state.saving)
  }

  @Test
  fun canSaveAgainAfterAFailure() {
    takePicture()
    repository.saveError = IllegalStateException("no user")
    viewModel.onSave()
    repository.saveError = null

    viewModel.onSave()

    assertEquals(1, repository.saved.size)
    assertNull(state.preview)
    assertFalse(state.saveFailed)
  }

  @Test
  fun savesOnlyOnceWhileSaving() {
    takePicture()
    val gate = CompletableDeferred<Unit>()
    repository.saveGate = gate

    viewModel.onSave()
    assertTrue(state.saving)
    viewModel.onSave()
    gate.complete(Unit)

    assertEquals(1, repository.saved.size)
  }

  @Test
  fun savingWithoutAPhotoDoesNothing() {
    viewModel.onSave()

    assertEquals(emptyList<Pair<String, java.io.File>>(), repository.saved)
    assertFalse(state.saving)
  }

  @Test
  fun retakingThrowsTheFirstPhotoAwayAndOpensTheCameraAgain() {
    val first = takePicture()

    viewModel.onRetake()

    assertNull(state.preview)
    assertEquals(listOf(first), repository.discarded)
    val second = (effects.last() as PhotosEffect.LaunchCamera).file
    assertTrue(second != first)
  }

  @Test
  fun retakingWithoutAPhotoDoesNothing() {
    viewModel.onRetake()

    assertEquals(emptyList<PhotosEffect>(), effects)
  }

  @Test
  fun cannotRetakeOrCloseWhileSaving() {
    val file = takePicture()
    repository.saveGate = CompletableDeferred()
    viewModel.onSave()

    viewModel.onRetake()
    viewModel.onPreviewClosed()

    assertEquals(file, state.preview)
    assertEquals(emptyList<java.io.File>(), repository.discarded)
  }

  @Test
  fun closingThePreviewThrowsThePhotoAway() {
    val file = takePicture()

    viewModel.onPreviewClosed()

    assertNull(state.preview)
    assertEquals(listOf(file), repository.discarded)
  }

  @Test
  fun closingWithoutAPreviewDoesNothing() {
    viewModel.onPreviewClosed()

    assertEquals(emptyList<java.io.File>(), repository.discarded)
  }

  @Test
  fun aSecondCaptureDiscardsTheFileOfTheFirstOneThatNeverReported() {
    viewModel.onTakePhotoClick(permissionGranted = true)
    val first = (effects.last() as PhotosEffect.LaunchCamera).file

    viewModel.onTakePhotoClick(permissionGranted = true)

    assertEquals(listOf(first), repository.discarded)
  }

  @Test
  fun retryingAsksTheRepository() {
    viewModel.onRetry("a")

    assertEquals(listOf("a"), repository.retried)
  }

  @Test
  fun leavingTheScreenDiscardsWhatWasNotKept() {
    val preview = takePicture()
    viewModel.onTakePhotoClick(permissionGranted = true)
    val capture = (effects.last() as PhotosEffect.LaunchCamera).file

    clear()

    assertTrue(repository.discarded.containsAll(listOf(preview, capture)))
  }

  @Test
  fun leavingTheScreenDoesNotDiscardAPhotoBeingSaved() {
    takePicture()
    repository.saveGate = CompletableDeferred()
    viewModel.onSave()

    clear()

    assertEquals(emptyList<java.io.File>(), repository.discarded)
  }

  @Test
  fun leavingTheScreenWithNothingPendingDiscardsNothing() {
    clear()

    assertEquals(emptyList<java.io.File>(), repository.discarded)
  }

  @Test
  fun showsThePhotosTheTripAlreadyHas() {
    repository.uploaded.value = listOf(tripPhoto("a"), tripPhoto("b"))

    assertEquals(listOf("a", "b"), state.uploaded.map { it.id })
    assertFalse(state.galleryFailed)
  }

  @Test
  fun followsNewUploadedPhotos() {
    repository.uploaded.value = listOf(tripPhoto("a"))
    repository.uploaded.value = listOf(tripPhoto("b"), tripPhoto("a"))

    assertEquals(listOf("b", "a"), state.uploaded.map { it.id })
  }

  @Test
  fun knowsTheSignedInUser() {
    viewModel = PhotosViewModel(repository, connectivity, "trip", currentUserId = "me")

    assertEquals("me", state.currentUserId)
  }

  @Test
  fun reportsThatThePhotosCouldNotBeRead() {
    repository.galleryError = IllegalStateException("denied")
    viewModel = PhotosViewModel(repository, connectivity, "trip")

    assertTrue(state.galleryFailed)
    assertEquals(emptyList<Any>(), state.uploaded)
  }

  @Test
  fun readsThePhotosAgainOnRetry() {
    repository.galleryError = IllegalStateException("denied")
    viewModel = PhotosViewModel(repository, connectivity, "trip")
    repository.galleryError = null
    repository.uploaded.value = listOf(tripPhoto("a"))
    val before = repository.galleryObservations

    viewModel.onGalleryRetry()

    assertFalse(state.galleryFailed)
    assertEquals(listOf("a"), state.uploaded.map { it.id })
    assertEquals(before + 1, repository.galleryObservations)
  }

  @Test
  fun doesNotReadThePhotosAgainWhenNothingFailed() {
    val before = repository.galleryObservations

    viewModel.onGalleryRetry()

    assertEquals(before, repository.galleryObservations)
  }

  @Test
  fun opensAnUploadedPhotoFullScreen() {
    repository.uploaded.value = listOf(tripPhoto("a"), tripPhoto("b"))

    viewModel.onPhotoClick("b")

    assertEquals("b", state.viewing?.id)
  }

  @Test
  fun closesTheViewer() {
    repository.uploaded.value = listOf(tripPhoto("a"))
    viewModel.onPhotoClick("a")

    viewModel.onViewerClosed()

    assertNull(state.viewing)
  }

  @Test
  fun showsNothingForAPhotoThatIsNotInTheGallery() {
    repository.uploaded.value = listOf(tripPhoto("a"))

    viewModel.onPhotoClick("unknown")

    assertNull(state.viewing)
  }

  @Test
  fun theViewerClosesByItselfWhenItsPhotoDisappears() {
    repository.uploaded.value = listOf(tripPhoto("a"), tripPhoto("b"))
    viewModel.onPhotoClick("a")

    repository.uploaded.value = listOf(tripPhoto("b"))

    assertNull(state.viewing)
  }

  private fun clear() {
    // onCleared is protected, and only the framework calls it when the screen is left for good.
    val method = PhotosViewModel::class.java.getDeclaredMethod("onCleared")
    method.isAccessible = true
    method.invoke(viewModel)
  }
}
