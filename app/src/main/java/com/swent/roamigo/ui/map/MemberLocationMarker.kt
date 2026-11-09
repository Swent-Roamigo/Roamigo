// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.swent.roamigo.R
import com.swent.roamigo.model.location.MemberLocation
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing

private val AvatarSize = 40.dp
private val AvatarBorderWidth = 2.dp
private const val STALE_ALPHA = 0.5f

/**
 * Marker of a trip member on the map: their avatar above a label with their name and how long ago
 * their location was updated.
 *
 * The avatar shows the member's photo, otherwise their initial, otherwise a default avatar. The
 * whole marker is dimmed while the location is stale, and announced as outdated.
 *
 * @param location last known location of the member.
 * @param member display information of the member.
 * @param nowMillis current time, in milliseconds since the epoch, used to describe the location's
 *   age.
 * @param isStale whether the location is outdated.
 * @param modifier modifier applied to the marker.
 */
@Composable
fun MemberLocationMarker(
    location: MemberLocation,
    member: MemberDisplayInfo,
    nowMillis: Long,
    isStale: Boolean,
    modifier: Modifier = Modifier,
) {
  val userId = location.userId
  val freshness =
      formatLocationFreshness(
          updatedAtMillis = location.updatedAt,
          nowMillis = nowMillis,
          resources = LocalResources.current,
      )
  val staleDescription = stringResource(R.string.trip_map_member_location_stale)

  Column(
      modifier =
          modifier.alpha(if (isStale) STALE_ALPHA else 1f).semantics(mergeDescendants = true) {
            testTag = C.Tag.trip_map_member_marker_prefix + userId
            if (isStale) stateDescription = staleDescription
          },
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
  ) {
    MemberAvatar(userId = userId, member = member)
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Spacing.extraSmall,
    ) {
      Text(
          text =
              stringResource(R.string.trip_map_member_marker_label, member.displayName, freshness),
          modifier =
              Modifier.padding(horizontal = Spacing.small, vertical = Spacing.extraSmall)
                  .testTag(C.Tag.trip_map_member_label_prefix + userId),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurface,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * The avatar is decorative: the label already names the member, so it adds nothing to the marker's
 * description.
 */
@Composable
private fun MemberAvatar(userId: String, member: MemberDisplayInfo) {
  Surface(
      modifier = Modifier.size(AvatarSize),
      shape = CircleShape,
      color = MaterialTheme.colorScheme.secondary,
      contentColor = MaterialTheme.colorScheme.onSecondary,
      border = BorderStroke(AvatarBorderWidth, MaterialTheme.colorScheme.surface),
      shadowElevation = Spacing.extraSmall,
  ) {
    Box(contentAlignment = Alignment.Center) {
      if (member.initial.isNotBlank()) {
        Text(
            text = member.initial,
            modifier =
                Modifier.clearAndSetSemantics {
                  testTag = C.Tag.trip_map_member_initial_prefix + userId
                },
            style = MaterialTheme.typography.titleMedium,
        )
      } else {
        Icon(
            painter = painterResource(R.drawable.ic_user),
            contentDescription = null,
            modifier = Modifier.testTag(C.Tag.trip_map_member_default_avatar_prefix + userId),
        )
      }
      // Drawn over the fallback, which stays visible while the photo loads or if it fails.
      if (member.avatarUrl != null) {
        AsyncImage(
            model = member.avatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier.fillMaxSize()
                    .clip(CircleShape)
                    .testTag(C.Tag.trip_map_member_photo_prefix + userId),
        )
      }
    }
  }
}
