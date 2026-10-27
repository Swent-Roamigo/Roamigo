// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.swent.roamigo.R
import com.swent.roamigo.model.destination.Destination
import com.swent.roamigo.model.destination.DestinationPhoto

internal object DestinationCardTestTags {
  const val PREFIX = "destination_card_"
  private const val PHOTO_CREDITS_PREFIX = "photo_attribution_"

  fun forDestination(destinationId: String): String = "$PREFIX${destinationId.lowercase()}"

  fun photoCreditsFor(destinationId: String): String = "$PHOTO_CREDITS_PREFIX$destinationId"
}

@Composable
fun DestinationCard(
    destination: Destination,
    selected: Boolean,
    photo: DestinationPhoto?,
    onRequestPhoto: () -> Unit,
    onClick: () -> Unit,
) {
  val uriHandler = LocalUriHandler.current
  var photoLoaded by remember(photo?.imageUrl) { mutableStateOf(false) }
  var showPhotoCredits by remember(photo?.imageUrl) { mutableStateOf(false) }
  androidx.compose.runtime.LaunchedEffect(destination.stableId) { onRequestPhoto() }
  val colors = MaterialTheme.colorScheme
  val cardDescription =
      stringResource(
          if (selected) R.string.destination_card_description_selected
          else R.string.destination_card_description,
          destination.name,
          destination.countryName,
      )
  val photoInfoDescription =
      stringResource(R.string.destination_photo_info_description, destination.name)
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .aspectRatio(CARD_ASPECT_RATIO)
              .clip(MaterialTheme.shapes.medium)
              .then(
                  if (selected) Modifier.border(3.dp, colors.primary, MaterialTheme.shapes.medium)
                  else Modifier
              )
              .selectable(selected = selected, role = Role.Checkbox, onClick = onClick)
              .semantics {
                contentDescription = cardDescription
                testTag = DestinationCardTestTags.forDestination(destination.stableId)
              },
  ) {
    AsyncImage(
        model = photo?.imageUrl,
        placeholder = painterResource(R.drawable.destination_placeholder),
        error = painterResource(R.drawable.destination_placeholder),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
        onSuccess = { photoLoaded = true },
        onError = { photoLoaded = false },
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = PHOTO_OVERLAY_ALPHA)))
    if (selected) {
      Box(
          Modifier.align(Alignment.TopEnd)
              .padding(8.dp)
              .size(24.dp)
              .clip(CircleShape)
              .background(colors.primary),
          contentAlignment = Alignment.Center,
      ) {
        Text("✓", color = colors.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
      }
    }
    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp)) {
      Text(
          destination.name,
          color = colors.onPrimary,
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
      Text(
          destination.countryName,
          color = colors.onPrimary,
          style = MaterialTheme.typography.labelSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
    if (photo != null && photoLoaded) {
      Box(
          modifier =
              Modifier.align(Alignment.TopStart)
                  .size(48.dp)
                  .clickable { showPhotoCredits = true }
                  .semantics {
                    contentDescription = photoInfoDescription
                    testTag = DestinationCardTestTags.photoCreditsFor(destination.stableId)
                  },
          contentAlignment = Alignment.Center,
      ) {
        Text(
            "ⓘ",
            modifier =
                Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 20.sp,
        )
      }
    }
  }
  if (showPhotoCredits && photo != null && photoLoaded) {
    AlertDialog(
        onDismissRequest = { showPhotoCredits = false },
        title = { Text(stringResource(R.string.destination_photo_credits_title)) },
        text = {
          Column(Modifier.verticalScroll(rememberScrollState())) {
            photo.title?.let { Text(it) }
            Text(
                stringResource(
                    R.string.destination_photo_creator,
                    photo.creator
                        ?: stringResource(R.string.destination_photo_public_domain_creator),
                )
            )
            photo.credit?.takeIf { it != photo.creator }?.let { Text(it) }
            Text(stringResource(R.string.destination_photo_license_cropped, photo.license))
            TextButton(onClick = { uriHandler.openUri(photo.sourceUrl) }) {
              Text(stringResource(R.string.destination_photo_view_original))
            }
            photo.licenseUrl?.let { licenseUrl ->
              TextButton(onClick = { uriHandler.openUri(licenseUrl) }) {
                Text(stringResource(R.string.destination_photo_view_license))
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showPhotoCredits = false }) {
            Text(stringResource(R.string.create_trip_close))
          }
        },
    )
  }
}

private const val CARD_ASPECT_RATIO = 169f / 128f
private const val PHOTO_OVERLAY_ALPHA = 0.28f
