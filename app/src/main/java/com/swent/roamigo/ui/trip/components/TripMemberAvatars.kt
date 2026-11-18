// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.AvatarItem
import com.swent.roamigo.ui.components.AvatarSize
import com.swent.roamigo.ui.components.AvatarStack
import com.swent.roamigo.ui.components.avatarColor
import com.swent.roamigo.ui.map.MemberDisplayInfo
import com.swent.roamigo.ui.theme.Spacing

private const val MAX_SHOWN_AVATARS = 3

/**
 * The people on a trip as a short stack of avatars followed by a chevron, which opens the Members
 * screen. At most three avatars are shown, and the last one reads "+N" when the trip has more.
 *
 * A member whose profile is not known yet is shown with the default avatar, so the stack always has
 * as many avatars as the trip has members, up to three.
 *
 * @param members display information of the members that could be resolved, in trip order.
 * @param memberCount how many distinct members the trip has.
 * @param onClick called when the user opens the member list.
 */
@Composable
internal fun TripMemberAvatars(
    members: List<MemberDisplayInfo>,
    memberCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
  val description = stringResource(R.string.trip_details_members)
  Row(
      modifier =
          modifier
              .minimumInteractiveComponentSize()
              .semantics(mergeDescendants = true) { contentDescription = description }
              .clickable(role = Role.Button, onClick = onClick)
              .testTag(C.Tag.trip_member_avatars),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    val shown = minOf(memberCount, MAX_SHOWN_AVATARS)
    AvatarStack(
        items =
            List(shown) { index ->
              val isOverflow = memberCount > MAX_SHOWN_AVATARS && index == MAX_SHOWN_AVATARS - 1
              AvatarItem(
                  initial = members.getOrNull(index)?.initial.orEmpty(),
                  color = avatarColor(index),
                  overflowLabel =
                      if (isOverflow) "+${memberCount - (MAX_SHOWN_AVATARS - 1)}" else null,
              )
            },
        size = AvatarSize.Small,
        overlap = 8.dp,
        ringColor = MaterialTheme.colorScheme.background,
    )
    Icon(
        painter = painterResource(R.drawable.ic_c2_chevron_right),
        contentDescription = null,
        modifier = Modifier.padding(start = Spacing.small).size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
