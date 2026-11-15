// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.connectivity.FakeConnectivityRepository
import com.swent.roamigo.model.photo.FakePhotoLocator
import com.swent.roamigo.model.photo.PHOTO_LOCATION_TIMEOUT
import com.swent.roamigo.model.photo.PhotoCoordinates
import com.swent.roamigo.model.photo.PhotoLocationResult
import com.swent.roamigo.model.photo.PhotoLocator
import com.swent.roamigo.model.photo.pendingPhoto
import com.swent.roamigo.model.photo.tripPhoto
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class PhotosViewModelTest {
  @get:Rule val folder = TemporaryFolder()

  private val dispatcher = UnconfinedTestDispatcher()
  private val connectivity = FakeConnectivityRepository()
  private val locator = FakePhotoLocator()
  private val clock = { dispatcher.scheduler.currentTime }
  private lateinit var repository: TestPhotoRepository
  private lateinit var viewModel: PhotosViewModel
  private val effects = mutableListOf<PhotosEffect>()

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    repository = TestPhotoRepository(folder.newFolder())
    viewModel = PhotosViewModel(repository, connectivity, "trip", locator, clock = clock)
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
  fun aSaveStillFinishesWhenTheScreenIsLeftWhileWaiting() {
    val file = takePicture()
    repository.saveGate = CompletableDeferred()
    viewModel.onSave()

    clear()
    viewModel.viewModelScope.cancel()
    repository.saveGate?.complete(Unit)

    assertEquals(listOf("trip" to file), repository.saved)
    assertEquals(emptyList<java.io.File>(), repository.discarded)
  }

  @Test
  fun aSaveThatFailsAfterTheScreenWasLeftDiscardsThePhoto() {
    val file = takePicture()
    repository.saveGate = CompletableDeferred()
    repository.saveError = IllegalStateException("no space")
    viewModel.onSave()

    clear()
    viewModel.viewModelScope.cancel()
    repository.saveGate?.complete(Unit)

    assertEquals(listOf(file), repository.discarded)
  }

  @Test
  fun aPictureTakenAfterTheAppWasRecreatedIsShown() {
    val handle = SavedStateHandle()
    var model =
        PhotosViewModel(repository, connectivity, "trip", locator, savedStateHandle = handle)
    model.effects.onEach { effects += it }.launchIn(kotlinx.coroutines.CoroutineScope(dispatcher))
    model.onTakePhotoClick(permissionGranted = true)
    val file = (effects.last() as PhotosEffect.LaunchCamera).file
    file.writeText("jpeg")

    // Android killed the process while the camera was open: a new view model gets the same state.
    model = PhotosViewModel(repository, connectivity, "trip", locator, savedStateHandle = handle)
    model.onPictureTaken(true)

    assertEquals(file, model.uiState.value.preview)
  }

  @Test
  fun aCancelledPictureAfterTheAppWasRecreatedDiscardsTheFile() {
    val handle = SavedStateHandle()
    val first =
        PhotosViewModel(repository, connectivity, "trip", locator, savedStateHandle = handle)
    first.effects.onEach { effects += it }.launchIn(kotlinx.coroutines.CoroutineScope(dispatcher))
    first.onTakePhotoClick(permissionGranted = true)
    val file = (effects.last() as PhotosEffect.LaunchCamera).file

    PhotosViewModel(repository, connectivity, "trip", locator, savedStateHandle = handle)
        .onPictureTaken(false)

    assertEquals(listOf(file), repository.discarded)
  }

  @Test
  fun takesThePhotoWhenBackFromSettingsWithTheCameraAllowed() {
    viewModel.onOpenSettings()
    val before = effects.size

    viewModel.onScreenResumed(permissionGranted = true)

    assertTrue(effects.size == before + 1 && effects.last() is PhotosEffect.LaunchCamera)
  }

  @Test
  fun doesNothingWhenBackFromSettingsWithTheCameraStillRefused() {
    viewModel.onOpenSettings()
    val before = effects.size

    viewModel.onScreenResumed(permissionGranted = false)

    assertEquals(before, effects.size)
  }

  @Test
  fun resumingForAnyOtherReasonDoesNotOpenTheCamera() {
    viewModel.onScreenResumed(permissionGranted = true)

    assertEquals(emptyList<PhotosEffect>(), effects)
  }

  @Test
  fun backFromSettingsOpensTheCameraOnlyOnce() {
    viewModel.onOpenSettings()
    viewModel.onScreenResumed(permissionGranted = true)
    val before = effects.size

    viewModel.onScreenResumed(permissionGranted = true)

    assertEquals(before, effects.size)
  }

  @Test
  fun anUnavailableCameraDiscardsTheFileItWasGiven() {
    viewModel.onTakePhotoClick(permissionGranted = true)
    val file = (effects.last() as PhotosEffect.LaunchCamera).file

    viewModel.onCameraUnavailable()
    viewModel.onPictureTaken(true)

    assertEquals(listOf(file), repository.discarded)
    assertNull(state.preview)
  }

  @Test
  fun aPictureTakenAfterTheProcessWasKilledIsShown() {
    val handle = SavedStateHandle()
    val first =
        PhotosViewModel(repository, connectivity, "trip", locator, savedStateHandle = handle)
    first.effects.onEach { effects += it }.launchIn(kotlinx.coroutines.CoroutineScope(dispatcher))
    first.onTakePhotoClick(permissionGranted = true)
    val file = (effects.last() as PhotosEffect.LaunchCamera).file
    file.writeText("jpeg")

    // What Android does on process death: the saved state goes through a Parcel and comes back.
    val parcel = Parcel.obtain()
    parcel.writeBundle(handle.savedStateProvider().saveState())
    parcel.setDataPosition(0)
    val restored = SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
    parcel.recycle()
    val second =
        PhotosViewModel(repository, connectivity, "trip", locator, savedStateHandle = restored)
    second.onPictureTaken(true)

    assertEquals(file, second.uiState.value.preview)
  }

  @Test
  fun showsThePhotosTheTripAlreadyHas() {
    repository.uploaded.value = listOf(tripPhoto("a"), tripPhoto("b"))

    assertEquals(listOf("a", "b"), state.uploaded.map { it.id })
    assertFalse(state.galleryFailed)
  }

  @Test
  fun isLoadingTheGalleryUntilThePhotosAreRead() {
    val gate = CompletableDeferred<Unit>()
    repository.galleryGate = gate
    viewModel = PhotosViewModel(repository, connectivity, "trip", locator)

    assertTrue(state.galleryLoading)

    gate.complete(Unit)

    assertFalse(state.galleryLoading)
  }

  @Test
  fun stopsLoadingTheGalleryWhenThePhotosCouldNotBeRead() {
    repository.galleryError = IllegalStateException("denied")
    viewModel = PhotosViewModel(repository, connectivity, "trip", locator)

    assertFalse(state.galleryLoading)
  }

  @Test
  fun loadsTheGalleryAgainOnRetry() {
    repository.galleryError = IllegalStateException("denied")
    viewModel = PhotosViewModel(repository, connectivity, "trip", locator)
    repository.galleryError = null
    repository.galleryGate = CompletableDeferred()

    viewModel.onGalleryRetry()

    assertTrue(state.galleryLoading)
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
    viewModel = PhotosViewModel(repository, connectivity, "trip", locator, currentUserId = "me")

    assertEquals("me", state.currentUserId)
  }

  @Test
  fun reportsThatThePhotosCouldNotBeRead() {
    repository.galleryError = IllegalStateException("denied")
    viewModel = PhotosViewModel(repository, connectivity, "trip", locator)

    assertTrue(state.galleryFailed)
    assertEquals(emptyList<Any>(), state.uploaded)
  }

  @Test
  fun readsThePhotosAgainOnRetry() {
    repository.galleryError = IllegalStateException("denied")
    viewModel = PhotosViewModel(repository, connectivity, "trip", locator)
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

  @Test
  fun savesThePositionReadWhenTheCameraReturnedEvenIfSavingIsMuchLater() = runTest {
    locator.answer(PhotoLocationResult.Found(PhotoCoordinates(46.5, 6.6)))
    takePicture()
    assertEquals(PhotoLocationStatus.Attached(46.5, 6.6), state.previewLocation)

    // The user wanders off before saving; the position is not read again.
    locator.answer(PhotoLocationResult.Found(PhotoCoordinates(1.0, 2.0)))
    advanceTimeBy(60.seconds)
    viewModel.onSave()

    assertEquals(PhotoCoordinates(46.5, 6.6), repository.savedPhotos.single().coordinates)
    assertEquals(listOf(PHOTO_LOCATION_TIMEOUT), locator.timeouts)
  }

  @Test
  fun savesTheTimeTheCameraReturnedNotTheTimeOfSaving() = runTest {
    advanceTimeBy(1_000)
    takePicture()
    advanceTimeBy(60.seconds)

    viewModel.onSave()

    assertEquals(1_000L, repository.savedPhotos.single().takenAtMillis)
  }

  @Test
  fun savesWithoutAPositionAndNeverAsksForPermissionWhenLocationIsOff() = runTest {
    locator.answer(PhotoLocationResult.PermissionOff)
    takePicture()
    assertEquals(PhotoLocationStatus.Off, state.previewLocation)

    viewModel.onSave()

    assertNull(repository.savedPhotos.single().coordinates)
    assertTrue(effects.none { it is PhotosEffect.RequestCameraPermission })
    assertEquals(1, effects.size)
  }

  @Test
  fun waitsOnlyForWhatIsLeftOfTheTimeoutThenSavesWithoutAPosition() = runTest {
    locator.answerLater()
    advanceTimeBy(10_000)
    takePicture()
    val cameraReturnedAt = currentTime
    assertEquals(PhotoLocationStatus.Locating, state.previewLocation)

    advanceTimeBy(3.seconds)
    viewModel.onSave()
    assertTrue(state.saving)
    advanceTimeBy(2.seconds - 1.milliseconds)
    runCurrent()
    assertTrue(repository.saved.isEmpty())

    advanceTimeBy(1.milliseconds)
    runCurrent()

    assertEquals(cameraReturnedAt + 5_000, currentTime)
    assertNull(repository.savedPhotos.single().coordinates)
    assertEquals(cameraReturnedAt, repository.savedPhotos.single().takenAtMillis)
    assertFalse(state.saving)
  }

  @Test
  fun stopsWaitingForALocatorThatIgnoresItsTimeout() = runTest {
    val stuck =
        object : PhotoLocator {
          var cancelled = false

          override suspend fun locate(timeout: kotlin.time.Duration): PhotoLocationResult {
            try {
              delay(Long.MAX_VALUE)
            } finally {
              cancelled = true
            }
            error("unreachable")
          }
        }
    viewModel = PhotosViewModel(repository, connectivity, "trip", stuck, clock = clock)
    viewModel.effects.onEach { effects += it }.launchIn(CoroutineScope(dispatcher))
    takePicture()
    advanceTimeBy(1.seconds)

    viewModel.onSave()
    advanceTimeBy(4.seconds)
    runCurrent()

    assertEquals(5_000L, currentTime)
    assertNull(repository.savedPhotos.single().coordinates)
    assertTrue(stuck.cancelled)
  }

  @Test
  fun savesWhenTheLocatorFails() = runTest {
    val failing =
        object : PhotoLocator {
          override suspend fun locate(timeout: kotlin.time.Duration): PhotoLocationResult =
              throw IllegalStateException("broken")
        }
    viewModel = PhotosViewModel(repository, connectivity, "trip", failing, clock = clock)
    viewModel.effects.onEach { effects += it }.launchIn(CoroutineScope(dispatcher))
    takePicture()
    assertEquals(PhotoLocationStatus.Unavailable, state.previewLocation)

    viewModel.onSave()

    assertNull(repository.savedPhotos.single().coordinates)
  }

  @Test
  fun geotagsTheNextPhotoOnceThePermissionIsGrantedWithoutAnyPrompt() = runTest {
    takePicture()
    assertEquals(PhotoLocationStatus.Off, state.previewLocation)
    viewModel.onPreviewClosed()

    // The user allowed the location on the trip map in the meantime.
    locator.answer(PhotoLocationResult.Found(PhotoCoordinates(46.5, 6.6)))
    val effectsBefore = effects.size
    takePicture()
    viewModel.onSave()

    assertEquals(PhotoCoordinates(46.5, 6.6), repository.savedPhotos.single().coordinates)
    assertTrue(effects.drop(effectsBefore).all { it is PhotosEffect.LaunchCamera })
  }

  @Test
  fun aPhotoBeingSavedWhenTheScreenIsLeftIsStillSavedWithTheRemainingWait() = runTest {
    locator.answerLater()
    takePicture()
    advanceTimeBy(1.seconds)
    viewModel.onSave()

    clear()
    viewModel.viewModelScope.cancel()
    advanceTimeBy(4.seconds)
    runCurrent()

    assertEquals(1, repository.savedPhotos.size)
    assertNull(repository.savedPhotos.single().coordinates)
  }

  @Test
  fun aPhotoBeingSavedWhenTheScreenIsLeftKeepsAPositionThatArrivedBefore() = runTest {
    val fix = locator.answerLater()
    takePicture()
    repository.saveGate = CompletableDeferred()
    viewModel.onSave()
    fix.complete(PhotoLocationResult.Found(PhotoCoordinates(46.5, 6.6)))

    clear()
    viewModel.viewModelScope.cancel()
    repository.saveGate?.complete(Unit)

    assertEquals(PhotoCoordinates(46.5, 6.6), repository.savedPhotos.single().coordinates)
  }

  @Test
  fun hasNoLocationStatusWithoutAPreview() = runTest {
    assertNull(state.previewLocation)
    takePicture()
    viewModel.onPreviewClosed()

    assertNull(state.previewLocation)
  }

  /** A view model with its own saved state and locator, listening to its effects. */
  private fun modelWith(
      handle: SavedStateHandle,
      locator: PhotoLocator,
  ): PhotosViewModel =
      PhotosViewModel(
              repository,
              connectivity,
              "trip",
              locator,
              savedStateHandle = handle,
              clock = clock,
          )
          .also {
            it.effects.onEach { effect -> effects += effect }.launchIn(CoroutineScope(dispatcher))
          }

  private fun capture(model: PhotosViewModel): java.io.File {
    model.onTakePhotoClick(permissionGranted = true)
    val file = (effects.last() as PhotosEffect.LaunchCamera).file
    file.writeText("jpeg")
    model.onPictureTaken(true)
    return file
  }

  /** What Android does on process death: the saved state goes through a Parcel and comes back. */
  private fun afterProcessDeath(handle: SavedStateHandle): SavedStateHandle {
    val parcel = Parcel.obtain()
    parcel.writeBundle(handle.savedStateProvider().saveState())
    parcel.setDataPosition(0)
    val restored = SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
    parcel.recycle()
    return restored
  }

  @Test
  fun retakingCancelsTheFetchAndStartsAFreshOneWithANewTime() = runTest {
    locator.answerLater()
    advanceTimeBy(1_000)
    takePicture()
    advanceTimeBy(2.seconds)

    viewModel.onRetake()

    assertEquals(1, locator.cancelledCalls)
    assertNull(state.previewLocation)
    assertNull(state.preview)

    advanceTimeBy(10.seconds)
    locator.answer(PhotoLocationResult.Found(PhotoCoordinates(1.0, 2.0)))
    val file = (effects.last() as PhotosEffect.LaunchCamera).file.also { it.writeText("jpeg") }
    viewModel.onPictureTaken(true)
    viewModel.onSave()

    assertEquals(listOf(PHOTO_LOCATION_TIMEOUT, PHOTO_LOCATION_TIMEOUT), locator.timeouts)
    val saved = repository.savedPhotos.single()
    assertEquals(file, saved.file)
    assertEquals(PhotoCoordinates(1.0, 2.0), saved.coordinates)
    assertEquals(13_000L, saved.takenAtMillis)
  }

  @Test
  fun aLateResultFromTheOldFetchDoesNotChangeTheNewPreview() = runTest {
    val late = CompletableDeferred<PhotoLocationResult>()
    var calls = 0
    val stubborn =
        object : PhotoLocator {
          override suspend fun locate(timeout: kotlin.time.Duration): PhotoLocationResult {
            // The first read still returns after it was cancelled.
            if (++calls == 1) return withContext(NonCancellable) { late.await() }
            awaitCancellation()
          }
        }
    viewModel = PhotosViewModel(repository, connectivity, "trip", stubborn, clock = clock)
    viewModel.effects.onEach { effects += it }.launchIn(CoroutineScope(dispatcher))
    takePicture()
    viewModel.onRetake()
    (effects.last() as PhotosEffect.LaunchCamera).file.writeText("jpeg")
    viewModel.onPictureTaken(true)

    late.complete(PhotoLocationResult.Found(PhotoCoordinates(9.0, 9.0)))
    runCurrent()

    assertEquals(PhotoLocationStatus.Locating, state.previewLocation)
    viewModel.onSave()
    advanceTimeBy(5.seconds)
    runCurrent()
    assertNull(repository.savedPhotos.single().coordinates)
  }

  @Test
  fun closingThePreviewCancelsTheFetch() = runTest {
    locator.answerLater()
    takePicture()

    viewModel.onPreviewClosed()

    assertEquals(1, locator.cancelledCalls)
    assertNull(state.previewLocation)
  }

  @Test
  fun aPositionFoundBeforeTheProcessWasKilledIsRestoredAndSavedAsIs() = runTest {
    locator.answer(PhotoLocationResult.Found(PhotoCoordinates(46.5, 6.6)))
    val handle = SavedStateHandle()
    advanceTimeBy(1_000)
    val file = capture(modelWith(handle, locator))
    val restoredHandle = afterProcessDeath(handle)
    advanceTimeBy(30.seconds)
    val newLocator = FakePhotoLocator()

    val restored = modelWith(restoredHandle, newLocator)
    restored.onSave()

    assertEquals(emptyList<kotlin.time.Duration>(), newLocator.timeouts)
    val saved = repository.savedPhotos.single()
    assertEquals(file, saved.file)
    assertEquals(PhotoCoordinates(46.5, 6.6), saved.coordinates)
    assertEquals(1_000L, saved.takenAtMillis)
  }

  @Test
  fun aRestoredPreviewShowsItsPosition() = runTest {
    locator.answer(PhotoLocationResult.Found(PhotoCoordinates(46.5, 6.6)))
    val handle = SavedStateHandle()
    val file = capture(modelWith(handle, locator))

    val restored = modelWith(afterProcessDeath(handle), FakePhotoLocator())

    assertEquals(file, restored.uiState.value.preview)
    assertEquals(PhotoLocationStatus.Attached(46.5, 6.6), restored.uiState.value.previewLocation)
  }

  @Test
  fun aPositionStillBeingReadWhenTheProcessWasKilledIsFetchedAgainWithTheTimeLeft() = runTest {
    locator.answerLater()
    val handle = SavedStateHandle()
    advanceTimeBy(1_000)
    capture(modelWith(handle, locator))
    val restoredHandle = afterProcessDeath(handle)
    advanceTimeBy(2.seconds)
    val newLocator = FakePhotoLocator()
    val fix = newLocator.answerLater()

    val restored = modelWith(restoredHandle, newLocator)

    assertEquals(listOf(3.seconds), newLocator.timeouts)
    assertEquals(PhotoLocationStatus.Locating, restored.uiState.value.previewLocation)
    fix.complete(PhotoLocationResult.Found(PhotoCoordinates(1.0, 2.0)))
    advanceTimeBy(10.seconds)
    restored.onSave()
    val saved = repository.savedPhotos.single()
    assertEquals(PhotoCoordinates(1.0, 2.0), saved.coordinates)
    // The original camera time is kept, not the time of the restore.
    assertEquals(1_000L, saved.takenAtMillis)
  }

  @Test
  fun withNoTimeLeftARestoredPositionIsUnavailableWithoutAskingTheLocator() = runTest {
    locator.answerLater()
    val handle = SavedStateHandle()
    advanceTimeBy(1_000)
    capture(modelWith(handle, locator))
    val restoredHandle = afterProcessDeath(handle)
    advanceTimeBy(6.seconds)
    val newLocator = FakePhotoLocator()

    val restored = modelWith(restoredHandle, newLocator)

    assertEquals(emptyList<kotlin.time.Duration>(), newLocator.timeouts)
    assertEquals(PhotoLocationStatus.Unavailable, restored.uiState.value.previewLocation)
    restored.onSave()
    assertNull(repository.savedPhotos.single().coordinates)
    assertEquals(1_000L, repository.savedPhotos.single().takenAtMillis)
  }

  @Test
  fun anOffOrUnavailablePositionIsNotFetchedAgainAfterTheProcessWasKilled() = runTest {
    listOf(
            PhotoLocationResult.PermissionOff to PhotoLocationStatus.Off,
            PhotoLocationResult.Unavailable to PhotoLocationStatus.Unavailable,
        )
        .forEach { (result, status) ->
          locator.answer(result)
          val handle = SavedStateHandle()
          capture(modelWith(handle, locator))
          val newLocator = FakePhotoLocator()

          val restored = modelWith(afterProcessDeath(handle), newLocator)

          assertEquals(status, restored.uiState.value.previewLocation)
          assertEquals(emptyList<kotlin.time.Duration>(), newLocator.timeouts)
        }
  }

  @Test
  fun aSavedPhotoIsNotRestoredAfterTheProcessWasKilled() = runTest {
    val handle = SavedStateHandle()
    val model = modelWith(handle, locator)
    capture(model)
    model.onSave()

    val restored = modelWith(afterProcessDeath(handle), FakePhotoLocator())

    assertNull(restored.uiState.value.preview)
    assertNull(restored.uiState.value.previewLocation)
  }

  @Test
  fun aPreviewWhoseFileIsGoneIsNotRestored() = runTest {
    val handle = SavedStateHandle()
    val file = capture(modelWith(handle, locator))
    val restoredHandle = afterProcessDeath(handle)
    file.delete()

    val restored = modelWith(restoredHandle, FakePhotoLocator())

    assertNull(restored.uiState.value.preview)
    assertNull(restored.uiState.value.previewLocation)
  }

  private fun clear() {
    // onCleared is protected, and only the framework calls it when the screen is left for good.
    val method = PhotosViewModel::class.java.getDeclaredMethod("onCleared")
    method.isAccessible = true
    method.invoke(viewModel)
  }
}
