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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
  Scaffold(
      modifier = modifier.testTag(C.Tag.photos_screen),
      containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    Column(
        Modifier.fillMaxSize()
            .padding(padding)
            .padding(horizontal = Spacing.screenPadding, vertical = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      if (!readOnly) PhotosBanner(state)
      if (!readOnly) FailedUploadsBanner(state, onRetryClick)
      PhotosContent(
          if (readOnly) state.copy(photos = emptyList()) else state,
          fileOf,
          onRetryClick,
          onPhotoClick,
          onGalleryRetryClick,
          Modifier.weight(1f).fillMaxWidth(),
      )
      if (!readOnly) {
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
}

/** Summarises the photos that failed to upload, with one action to retry them all. */
@Composable
private fun FailedUploadsBanner(state: PhotosUiState, onRetryClick: (String) -> Unit) {
  val failed = state.photos.filter { it.status == PhotoUploadStatus.FAILED }
  if (failed.isEmpty()) return
  Surface(
      modifier = Modifier.fillMaxWidth().testTag(C.Tag.photos_failed_banner),
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            pluralStringResource(R.plurals.photos_failed_summary, failed.size, failed.size),
            style = MaterialTheme.typography.titleSmall,
        )
        if (!state.isOnline) {
          Text(
              stringResource(R.string.photos_failed_offline_hint),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
      OutlinedButton(
          onClick = { failed.forEach { onRetryClick(it.id) } },
          modifier = Modifier.heightIn(min = 48.dp).testTag(C.Tag.photos_retry_all),
      ) {
        Text(stringResource(R.string.photos_retry_all))
      }
    }
  }
}

/**
 * Tells whether the photos are waiting for a connection, being uploaded, or nothing is pending.
 * Failed photos are not counted: the list shows them with their own retry.
 */
@Composable
private fun PhotosBanner(state: PhotosUiState) {
  val pending = state.photos.filter { it.status != PhotoUploadStatus.FAILED }
  val count = pending.size
  val uploadingCount = pending.count { it.status == PhotoUploadStatus.UPLOADING }
  val uploading = uploadingCount > 0
  val (text, color) =
      when {
        count == 0 && !state.isOnline -> stringResource(R.string.photos_offline_empty) to Warning
        count == 0 -> return
        !state.isOnline ->
            pluralStringResource(R.plurals.photos_waiting_offline, count, count) to Warning
        uploading ->
            pluralStringResource(R.plurals.photos_uploading, count, uploadingCount, count) to
                Success
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
private fun GalleryLoading() {
  Column(
      Modifier.testTag(C.Tag.photos_gallery_loading),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    CircularProgressIndicator()
    Text(
        stringResource(R.string.photos_gallery_loading),
        style = MaterialTheme.typography.bodyMedium,
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
    Box(modifier, contentAlignment = Alignment.Center) {
      if (state.galleryLoading) GalleryLoading() else EmptyPhotos()
    }
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
    if (state.galleryLoading && state.uploaded.isEmpty()) {
      item(key = "gallery-loading", span = { GridItemSpan(maxLineSpan) }) { GalleryLoading() }
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
