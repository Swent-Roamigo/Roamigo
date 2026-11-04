// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.swent.roamigo.R
import com.swent.roamigo.model.connectivity.ConnectivityRepository
import com.swent.roamigo.model.photo.PhotoRepository
import java.io.File

/**
 * Obtains [PhotosViewModel] for [tripId] and connects it to the permission dialog, camera and
 * system settings.
 *
 * @param tripId identifier of the trip whose photos are shown.
 * @param currentUserId identifier of the signed-in user, or null when signed out.
 * @param photoUri provides the content URI handed to the external camera for a capture file.
 */
@Composable
fun PhotosRoute(
    photoRepository: PhotoRepository,
    connectivityRepository: ConnectivityRepository,
    tripId: String,
    tripName: String,
    currentUserId: String?,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    photoUri: (File) -> Uri = rememberPhotoUri(),
) {
  val viewModel = viewModel {
    PhotosViewModel(
        repository = photoRepository,
        connectivity = connectivityRepository,
        tripId = tripId,
        currentUserId = currentUserId,
        savedStateHandle = createSavedStateHandle(),
    )
  }
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val activity = LocalActivity.current

  val cameraLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        viewModel.onPictureTaken(taken)
      }
  val permissionLauncher =
      rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Asked after the answer: only then does the system tell a refusal it will repeat from one
        // that is final.
        val canAskAgain =
            activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.CAMERA,
                )
        viewModel.onCameraPermissionResult(granted, canAskAgain)
      }

  LaunchedEffect(viewModel) {
    viewModel.effects.collect { effect ->
      when (effect) {
        PhotosEffect.RequestCameraPermission ->
            permissionLauncher.launch(Manifest.permission.CAMERA)
        is PhotosEffect.LaunchCamera ->
            try {
              cameraLauncher.launch(photoUri(effect.file))
            } catch (_: ActivityNotFoundException) {
              // The camera is optional in the manifest, so a device may have no app to take photos.
              viewModel.onCameraUnavailable()
              Toast.makeText(context, R.string.photos_no_camera, Toast.LENGTH_LONG).show()
            }
        PhotosEffect.OpenSettings ->
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                )
            )
      }
    }
  }

  LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
    viewModel.onScreenResumed(
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    )
  }

  BackHandler(enabled = state.preview != null) { viewModel.onPreviewClosed() }
  BackHandler(enabled = state.preview == null && state.viewing != null) {
    viewModel.onViewerClosed()
  }

  val preview = state.preview
  val viewing = state.viewing
  if (preview != null) {
    PhotoPreview(
        file = preview,
        isOnline = state.isOnline,
        saving = state.saving,
        saveFailed = state.saveFailed,
        onSave = viewModel::onSave,
        onRetake = viewModel::onRetake,
        onClose = viewModel::onPreviewClosed,
        modifier = modifier,
    )
  } else if (viewing != null) {
    PhotoViewer(
        photo = viewing,
        currentUserId = state.currentUserId,
        onClose = viewModel::onViewerClosed,
        modifier = modifier,
    )
  } else {
    PhotosScreen(
        state = state,
        tripName = tripName,
        fileOf = photoRepository::fileOf,
        onTakePhotoClick = {
          viewModel.onTakePhotoClick(
              ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                  PackageManager.PERMISSION_GRANTED
          )
        },
        onRetryClick = viewModel::onRetry,
        onPhotoClick = viewModel::onPhotoClick,
        onGalleryRetryClick = viewModel::onGalleryRetry,
        readOnly = readOnly,
        modifier = modifier,
    )
  }
  CameraPermissionSheet(
      step = state.cameraStep,
      onContinue = viewModel::onRationaleContinue,
      onOpenSettings = viewModel::onOpenSettings,
      onDismiss = viewModel::onStepDismissed,
  )
}

@Composable
private fun rememberPhotoUri(): (File) -> Uri {
  val context = LocalContext.current
  return remember(context) {
    { file -> FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file) }
  }
}
