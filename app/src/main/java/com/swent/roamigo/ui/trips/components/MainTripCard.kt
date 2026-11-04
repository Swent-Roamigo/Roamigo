package com.swent.roamigo.ui.trips.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.swent.roamigo.model.destination.DestinationPhoto
import com.swent.roamigo.ui.components.PhotoCreditsDialog
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.markNotImplemented
import com.swent.roamigo.ui.trips.MainTripUiModel

/** Displays the featured trip, its details, and invite action. */
@Composable
internal fun MainTripCard(
    trip: MainTripUiModel,
    @DrawableRes imageRes: Int?,
    photo: DestinationPhoto? = null,
    onInviteClick: () -> Unit,
    onOpenTrip: () -> Unit,
) {
  var imageLoaded by remember(photo?.imageUrl) { mutableStateOf(false) }
  var showCredits by remember(photo?.imageUrl) { mutableStateOf(false) }
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .height(190.dp)
              .clip(MaterialTheme.shapes.large)
              .clickable(onClick = onOpenTrip)
              .semantics { testTag = C.Tag.trips_main_trip_card },
  ) {
    TripImage(imageRes = imageRes, modifier = Modifier.fillMaxSize())
    if (photo != null) {
      AsyncImage(
          model = photo.imageUrl,
          contentDescription = null,
          modifier = Modifier.fillMaxSize(),
          contentScale = ContentScale.Crop,
          onSuccess = { imageLoaded = true },
          onError = { imageLoaded = false },
      )
    }

    Box(
        modifier =
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
    )

    if (imageLoaded && photo != null) {
      TextButton(onClick = { showCredits = true }, modifier = Modifier.align(Alignment.TopEnd)) {
        Text(stringResource(R.string.trip_ready_photo_credit), color = MaterialTheme.colorScheme.onPrimary)
      }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
      trip.badge?.let { badge ->
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
          Text(
              text = badge,
              modifier =
                  Modifier.padding(
                      horizontal = 10.dp,
                      vertical = 6.dp,
                  ),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
      }

      Column {
        Text(
            text = trip.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(4.dp))

        val durationText =
            pluralStringResource(
                R.plurals.trips_duration_days,
                trip.durationDays,
                trip.durationDays,
            )

        val stopsText =
            pluralStringResource(
                R.plurals.trips_stop_count,
                trip.stopCount,
                trip.stopCount,
            )

        Text(
            text = "${trip.dateRange} · $durationText · $stopsText",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimary,
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
          MemberAvatars(trip.memberInitials)

          Spacer(modifier = Modifier.width(14.dp))

          Surface(
              onClick = onInviteClick,
              modifier =
                  Modifier.markNotImplemented(onInviteClick).semantics {
                    testTag = C.Tag.trips_invite_button
                  },
              shape = CircleShape,
              color = MaterialTheme.colorScheme.surface,
          ) {
            Row(
                modifier =
                    Modifier.padding(
                        horizontal = 14.dp,
                        vertical = 8.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
              Icon(
                  imageVector = Icons.Default.Add,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                  tint = MaterialTheme.colorScheme.primary,
              )

              Spacer(modifier = Modifier.width(4.dp))

              Text(
                  text = stringResource(R.string.trips_invite),
                  style = MaterialTheme.typography.bodyLarge,
                  color = MaterialTheme.colorScheme.onSurface,
              )
            }
          }
        }
      }
    }
  }
  if (showCredits && photo != null && imageLoaded) {
    PhotoCreditsDialog(
        title = photo.title, creator = photo.creator, credit = photo.credit,
        license = photo.license, sourceUrl = photo.sourceUrl, licenseUrl = photo.licenseUrl,
        closeLabel = stringResource(android.R.string.ok), onDismiss = { showCredits = false },
    )
  }
}

@Composable
private fun MemberAvatars(initials: List<String>) {
  if (initials.isEmpty()) {
    return
  }

  val avatarSize = 40.dp
  val avatarSpacing = 30.dp

  Box(
      modifier = Modifier.width(avatarSize + avatarSpacing * (initials.size - 1)).height(avatarSize)
  ) {
    initials.forEachIndexed { index, initial ->
      val avatarColors =
          when (index % 3) {
            0 -> MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.onSecondary
            1 -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.onTertiary
          }

      Box(
          modifier =
              Modifier.offset(x = avatarSpacing * index)
                  .size(avatarSize)
                  .clip(CircleShape)
                  .background(avatarColors.first)
                  .border(
                      width = 2.dp,
                      color = MaterialTheme.colorScheme.surface,
                      shape = CircleShape,
                  ),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.bodyMedium,
            color = avatarColors.second,
        )
      }
    }
  }
}
