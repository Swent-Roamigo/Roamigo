// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.roamigo.model.photo.PendingPhoto
import java.io.File

/**
 * Connects [PhotosViewModel] to the Android parts it cannot touch: the permission dialog, the
 * camera and the system settings.
 *
 * @param fileOf the file holding a waiting photo, to show its thumbnail.
 * @param onTripsClick invoked when the Trips tab of the bottom bar is selected.
 */
@Composable
fun PhotosRoute(
    viewModel: PhotosViewModel,
    tripName: String,
    fileOf: (PendingPhoto) -> File,
    onTripsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            cameraLauncher.launch(
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    effect.file,
                )
            )
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

  BackHandler(enabled = state.preview != null) { viewModel.onPreviewClosed() }

  val preview = state.preview
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
  } else {
    PhotosScreen(
        state = state,
        tripName = tripName,
        fileOf = fileOf,
        onTakePhotoClick = {
          viewModel.onTakePhotoClick(
              ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                  PackageManager.PERMISSION_GRANTED
          )
        },
        onRetryClick = viewModel::onRetry,
        onTripsClick = onTripsClick,
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
