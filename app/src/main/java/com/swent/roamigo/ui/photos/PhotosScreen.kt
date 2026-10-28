// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.swent.roamigo.R
import com.swent.roamigo.model.photo.PendingPhoto
import com.swent.roamigo.model.photo.PhotoUploadStatus
import com.swent.roamigo.model.photo.TripPhoto
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.AppBottomBar
import com.swent.roamigo.ui.components.rememberNotImplementedAction
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.theme.Success
import com.swent.roamigo.ui.theme.Warning
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * The photos of a trip: the ones saved on this device and not uploaded yet, then the ones the trip
 * already has, and the way to take a new one.
 *
 * @param fileOf the file holding a waiting photo, to show its thumbnail.
 * @param onPhotoClick invoked with the id of an uploaded photo the user wants to see full screen.
 * @param onGalleryRetryClick invoked when the user asks to read the uploaded photos again.
 * @param onTripsClick invoked when the Trips tab of the bottom bar is selected.
 */
@Composable
fun PhotosScreen(
    state: PhotosUiState,
    tripName: String,
    fileOf: (PendingPhoto) -> File,
    onTakePhotoClick: () -> Unit,
    onRetryClick: (String) -> Unit,
    onPhotoClick: (String) -> Unit,
    onGalleryRetryClick: () -> Unit,
    onTripsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  Scaffold(
      modifier = modifier.testTag(C.Tag.photos_screen),
      containerColor = MaterialTheme.colorScheme.background,
      bottomBar = {
        AppBottomBar(
            onTripsClick = onTripsClick,
            onMapClick = rememberNotImplementedAction(),
            onPhotosClick = {},
            tripsSelected = false,
            photosSelected = true,
        )
      },
  ) { padding ->
    Column(
        Modifier.fillMaxSize()
            .padding(padding)
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        Text(
            stringResource(R.string.photos_title),
            modifier = Modifier.testTag(C.Tag.photos_title),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            tripName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      PhotosBanner(state)
      PhotosContent(
          state,
          fileOf,
          onRetryClick,
          onPhotoClick,
          onGalleryRetryClick,
          Modifier.weight(1f).fillMaxWidth(),
      )
      Button(
          onClick = onTakePhotoClick,
          modifier = Modifier.fillMaxWidth().height(58.dp).testTag(C.Tag.photos_take_photo),
          shape = MaterialTheme.shapes.medium,
      ) {
        Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
        Text(
            stringResource(R.string.photos_take_photo),
            modifier = Modifier.padding(start = Spacing.small),
        )
      }
    }
  }
}

/** Tells whether the photos are waiting for a connection, being uploaded, or nothing is pending. */
@Composable
private fun PhotosBanner(state: PhotosUiState) {
  val count = state.photos.size
  val uploading = state.photos.any { it.status == PhotoUploadStatus.UPLOADING }
  val (text, color) =
      when {
        count == 0 && !state.isOnline -> stringResource(R.string.photos_offline_empty) to Warning
        count == 0 -> return
        !state.isOnline ->
            pluralStringResource(R.plurals.photos_waiting_offline, count, count) to Warning
        uploading -> pluralStringResource(R.plurals.photos_uploading, count, count) to Success
        else -> pluralStringResource(R.plurals.photos_waiting_online, count, count) to Warning
      }
  Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
        text,
        modifier = Modifier.testTag(C.Tag.photos_banner),
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
    if (uploading && state.isOnline) {
      LinearProgressIndicator(Modifier.fillMaxWidth().testTag(C.Tag.photos_progress))
    }
  }
}

@Composable
private fun EmptyPhotos() {
  Column(
      Modifier.testTag(C.Tag.photos_empty),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    Text(
        stringResource(R.string.photos_empty_title),
        style = MaterialTheme.typography.titleLarge,
    )
    Text(
        stringResource(R.string.photos_empty_description),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun PhotosContent(
    state: PhotosUiState,
    fileOf: (PendingPhoto) -> File,
    onRetryClick: (String) -> Unit,
    onPhotoClick: (String) -> Unit,
    onGalleryRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  if (state.photos.isEmpty() && state.uploaded.isEmpty() && !state.galleryFailed) {
    Box(modifier, contentAlignment = Alignment.Center) { EmptyPhotos() }
    return
  }
  LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      modifier = modifier,
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    items(state.photos, key = { "pending-${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { photo
      ->
      PendingPhotoRow(photo, fileOf(photo), onRetryClick)
    }
    if (state.galleryFailed) {
      item(key = "gallery-error", span = { GridItemSpan(maxLineSpan) }) {
        GalleryError(onGalleryRetryClick)
      }
    }
    if (state.uploaded.isNotEmpty()) {
      item(key = "uploaded-title", span = { GridItemSpan(maxLineSpan) }) {
        Text(
            stringResource(R.string.photos_uploaded_title),
            modifier = Modifier.testTag(C.Tag.photos_uploaded_title),
            style = MaterialTheme.typography.titleMedium,
        )
      }
      items(state.uploaded, key = { "uploaded-${it.id}" }) { photo ->
        UploadedTile(photo, state.currentUserId, onPhotoClick)
      }
    }
  }
}

@Composable
private fun GalleryError(onRetryClick: () -> Unit) {
  Row(
      Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
        stringResource(R.string.photos_gallery_error),
        modifier = Modifier.weight(1f).testTag(C.Tag.photos_gallery_error),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
    TextButton(onClick = onRetryClick, modifier = Modifier.testTag(C.Tag.photos_gallery_retry)) {
      Text(stringResource(R.string.photos_retry))
    }
  }
}

@Composable
private fun UploadedTile(photo: TripPhoto, currentUserId: String?, onClick: (String) -> Unit) {
  Column(
      Modifier.clickable { onClick(photo.id) }.testTag(C.Tag.photos_uploaded_prefix + photo.id),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    TripPhotoImage(
        photo = photo,
        contentDescription =
            stringResource(R.string.photos_uploaded_description, photo.takenAtText()),
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)),
    )
    Text(
        uploadedByText(photo, currentUserId),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun PendingPhotoRow(photo: PendingPhoto, file: File, onRetryClick: (String) -> Unit) {
  val time =
      remember(photo.takenAtMillis) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(photo.takenAtMillis))
      }
  val (statusRes, color) =
      when (photo.status) {
        PhotoUploadStatus.WAITING -> R.string.photos_status_waiting to Warning
        PhotoUploadStatus.UPLOADING -> R.string.photos_status_uploading to Success
        PhotoUploadStatus.FAILED -> R.string.photos_status_failed to MaterialTheme.colorScheme.error
      }
  Row(
      Modifier.fillMaxWidth().testTag(C.Tag.photos_item_prefix + photo.id),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    AsyncImage(
        model = file,
        contentDescription = stringResource(R.string.photos_item_description, time),
        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
        contentScale = ContentScale.Crop,
    )
    Text(
        stringResource(statusRes, time),
        modifier = Modifier.weight(1f).testTag(C.Tag.photos_status_prefix + photo.id),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
    )
    if (photo.status == PhotoUploadStatus.FAILED) {
      TextButton(
          onClick = { onRetryClick(photo.id) },
          modifier = Modifier.testTag(C.Tag.photos_retry_prefix + photo.id),
      ) {
        Text(stringResource(R.string.photos_retry))
      }
    }
  }
}
