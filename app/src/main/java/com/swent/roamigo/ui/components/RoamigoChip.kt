// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.swent.roamigo.ui.theme.RoamigoShapes
import com.swent.roamigo.ui.theme.Spacing
import com.swent.roamigo.ui.theme.Warning
import com.swent.roamigo.ui.theme.WarningBorder
import com.swent.roamigo.ui.theme.WarningSurface
import com.swent.roamigo.ui.theme.badge

/**
 * What a chip says about its content: neutral, a chosen item, something to act on, or a caption.
 */
enum class ChipTone {
  Neutral,
  Brand,
  Warning,
  Overlay,
}

/**
 * A pill with a short label: a status, a chosen item or a caption on a photo. [leading] and
 * [trailing] hold an icon or an action; [contentColor] overrides the label colour of the tone.
 */
@Composable
fun RoamigoChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.Neutral,
    contentColor: Color? = null,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
  val colors = MaterialTheme.colorScheme
  val container: Color
  val content: Color
  var border: BorderStroke? = null
  when (tone) {
    ChipTone.Neutral -> {
      container = colors.surface
      content = colors.onSurface
    }
    ChipTone.Brand -> {
      container = colors.primary
      content = colors.onPrimary
    }
    ChipTone.Warning -> {
      container = WarningSurface
      content = Warning
      border = BorderStroke(1.dp, WarningBorder)
    }
    ChipTone.Overlay -> {
      container = Color.Black.copy(alpha = 0.6f)
      content = Color.White
    }
  }
  // A chip is one thing to a screen reader, unless it holds an action of its own.
  val merged = if (trailing == null) modifier.semantics(mergeDescendants = true) {} else modifier
  Surface(modifier = merged, shape = RoamigoShapes.pill, color = container, border = border) {
    Row(
        Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      leading?.invoke()
      Text(text, style = textStyle, color = contentColor ?: content)
      trailing?.invoke()
    }
  }
}

/** A small count or label on a card, such as "Invite" or the number of suggestions. */
@Composable
fun RoamigoBadge(text: String, modifier: Modifier = Modifier) {
  Surface(
      modifier = modifier,
      shape = RoamigoShapes.chip,
      color = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.primary,
  ) {
    Text(
        text,
        Modifier.padding(horizontal = Spacing.small, vertical = 3.dp),
        style = MaterialTheme.typography.badge,
    )
  }
}
