// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.model.photo.TripPhoto
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing
import java.text.DateFormat
import java.util.Date

/**
 * Shows one gallery photo full screen.
 *
 * @param currentUserId the signed-in user, used for the fallback when their name is unavailable.
 * @param photographerName the photographer's display name, if available.
 */
@Composable
fun PhotoViewer(
    photo: TripPhoto,
    currentUserId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    photographerName: String? = null,
) {
  PhotoViewer(
      photos = listOf(photo),
      photographerNames = photographerName?.let { mapOf(photo.uploadedBy to it) }.orEmpty(),
      currentUserId = currentUserId,
      onClose = onClose,
      modifier = modifier,
  )
}

/**
 * Shows a gallery group in the order provided by its caller. The selected photo is retained when
 * possible if the list changes or the viewer is restored after a configuration change.
 *
 * @param photos the photos to browse, newest first.
 * @param photographerNames available display names, keyed by user ID.
 * @param currentUserId the signed-in user, used for the fallback when their name is unavailable.
 * @param onClose called by the close control, Back, or when the group becomes empty.
 */
@Composable
fun PhotoViewer(
    photos: List<TripPhoto>,
    photographerNames: Map<String, String>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    currentUserId: String? = null,
) {
  BackHandler(onBack = onClose)
  if (photos.isEmpty()) {
    LaunchedEffect(Unit) { onClose() }
    return
  }

  var selectedId by rememberSaveable { mutableStateOf(photos.first().id) }
  val index = photos.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: 0
  val photo = photos[index]
  LaunchedEffect(photo.id) { selectedId = photo.id }

  val photographer =
      photographerNames[photo.uploadedBy]?.trim()?.takeIf { it.isNotEmpty() }
          ?: stringResource(
              if (photo.uploadedBy == currentUserId) R.string.photo_viewer_you
              else R.string.photo_viewer_unknown_photographer
          )
  val locale = LocalConfiguration.current.locales[0]
  val dateTime =
      DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
          .format(Date(photo.takenAtMillis))

  Box(
      modifier
          .fillMaxSize()
          .background(Color.Black)
          // Callers may draw the viewer over a map; without this, gestures on the photo pass
          // through to the map and move it.
          .pointerInput(Unit) {
            awaitPointerEventScope {
              while (true) {
                awaitPointerEvent().changes.forEach { it.consume() }
              }
            }
          }
          .testTag(C.Tag.photo_viewer)
  ) {
    TripPhotoImage(
        photo = photo,
        contentDescription = stringResource(R.string.photo_viewer_image_description, dateTime),
        modifier = Modifier.fillMaxSize().testTag(C.Tag.photo_viewer_image),
        contentScale = ContentScale.Fit,
    )
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(Spacing.small),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Box(Modifier.fillMaxWidth()) {
        IconButton(
            onClick = onClose,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                    .testTag(C.Tag.photo_viewer_close),
        ) {
          Icon(
              Icons.Filled.Close,
              contentDescription = stringResource(R.string.photo_viewer_close),
              tint = Color.White,
          )
        }
      }
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (photos.size > 1) {
          Row(
              modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), CircleShape),
              verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
                onClick = { selectedId = photos[index - 1].id },
                enabled = index > 0,
                modifier = Modifier.size(48.dp).testTag(C.Tag.photo_viewer_previous),
            ) {
              Icon(
                  Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = stringResource(R.string.photo_viewer_previous),
                  tint = Color.White.copy(alpha = if (index > 0) 1f else 0.38f),
              )
            }
            Text(
                stringResource(R.string.photo_viewer_position, index + 1, photos.size),
                modifier = Modifier.testTag(C.Tag.photo_viewer_position),
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
            )
            IconButton(
                onClick = { selectedId = photos[index + 1].id },
                enabled = index < photos.lastIndex,
                modifier = Modifier.size(48.dp).testTag(C.Tag.photo_viewer_next),
            ) {
              Icon(
                  Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = stringResource(R.string.photo_viewer_next),
                  tint = Color.White.copy(alpha = if (index < photos.lastIndex) 1f else 0.38f),
              )
            }
          }
        }
        Column(
            modifier =
                Modifier.widthIn(max = 480.dp)
                    .testTag(C.Tag.photo_viewer_caption)
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = Spacing.large, vertical = Spacing.small),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
              photographer,
              modifier = Modifier.fillMaxWidth(),
              style = MaterialTheme.typography.bodyMedium,
              color = Color.White,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
          )
          Text(
              dateTime,
              modifier = Modifier.fillMaxWidth(),
              style = MaterialTheme.typography.bodySmall,
              color = Color.White,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/** Who took the photo, and when. */
@Composable
internal fun uploadedByText(photo: TripPhoto, currentUserId: String?): String =
    stringResource(
        if (photo.uploadedBy == currentUserId) R.string.photos_uploaded_by_you
        else R.string.photos_uploaded_by_member,
        photo.takenAtText(),
    )
