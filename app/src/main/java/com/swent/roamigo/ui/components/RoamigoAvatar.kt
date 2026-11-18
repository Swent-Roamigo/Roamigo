// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.FriendAvatarBlue
import com.swent.roamigo.ui.theme.FriendAvatarGreen
import com.swent.roamigo.ui.theme.FriendAvatarRose
import com.swent.roamigo.ui.theme.Ink
import com.swent.roamigo.ui.theme.Ochre

/** The three avatar sizes of the design; the text inside scales with the circle. */
enum class AvatarSize(val diameter: Dp) {
  Small(30.dp),
  Medium(40.dp),
  Large(48.dp),
}

/** Figma's avatar colours, in the order a stack of people uses them (ochre, blue, rose, green). */
private val AvatarPalette = listOf(Ochre, FriendAvatarBlue, FriendAvatarRose, FriendAvatarGreen)

/** A stable colour for the person at [index] in a list; the same person keeps the same colour. */
fun avatarColor(index: Int): Color = AvatarPalette[Math.floorMod(index, AvatarPalette.size)]

/** A person's circle: a [photoUrl] when there is one, else the [initial], else a user icon. */
@Composable
fun RoamigoAvatar(
    initial: String,
    color: Color,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.Medium,
    overflowLabel: String? = null,
    outlined: Boolean = false,
    photoUrl: String? = null,
    photoModifier: Modifier = Modifier,
) {
  // Ochre is light, so it takes the dark ink; the other colours take white.
  val contentColor = if (color == Ochre) Ink else Color.White
  Surface(
      modifier = modifier.size(size.diameter),
      shape = CircleShape,
      color = color,
      contentColor = contentColor,
      border = if (outlined) BorderStroke(2.dp, MaterialTheme.colorScheme.surface) else null,
  ) {
    Box(contentAlignment = Alignment.Center) {
      when {
        overflowLabel != null -> Text(overflowLabel, style = MaterialTheme.typography.labelSmall)
        initial.isNotBlank() -> Text(initial, style = initialStyle(size))
        else ->
            Icon(
                painter = painterResource(R.drawable.ic_user),
                contentDescription = null,
                modifier = Modifier.size(size.diameter / 2),
            )
      }
      if (photoUrl != null) {
        AsyncImage(
            model = photoUrl,
            contentDescription = null,
            modifier = photoModifier.matchParentSize().clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
      }
    }
  }
}

@Composable
private fun initialStyle(size: AvatarSize): TextStyle =
    when (size) {
      AvatarSize.Small -> MaterialTheme.typography.labelLarge
      AvatarSize.Medium -> MaterialTheme.typography.bodyMedium
      AvatarSize.Large -> MaterialTheme.typography.titleMedium
    }

/** One person in an [AvatarStack]. */
data class AvatarItem(val initial: String, val color: Color, val overflowLabel: String? = null)

/** Overlapping avatars, the first one on the left. */
@Composable
fun AvatarStack(
    items: List<AvatarItem>,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.Medium,
    overlap: Dp = size.diameter / 4,
) {
  Row(modifier, horizontalArrangement = Arrangement.spacedBy(-overlap)) {
    items.forEach {
      RoamigoAvatar(
          it.initial,
          it.color,
          size = size,
          overflowLabel = it.overflowLabel,
          outlined = true,
      )
    }
  }
}
