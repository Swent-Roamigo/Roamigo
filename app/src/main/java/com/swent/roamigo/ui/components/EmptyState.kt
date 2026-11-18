// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swent.roamigo.ui.theme.Spacing

/**
 * What a list shows when it has nothing to show: a short [title], one sentence saying what the list
 * is for ([description]), and an optional [icon] above them. The next step belongs in the screen's
 * main action, so it is not part of this component.
 */
@Composable
fun EmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    icon: Int? = null,
) {
  Column(
      modifier,
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    // On a short screen (a phone in landscape) the icon would push the text out of view.
    if (icon != null && LocalConfiguration.current.screenHeightDp >= MIN_HEIGHT_FOR_ICON_DP) {
      Box(
          Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
          contentAlignment = Alignment.Center,
      ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Text(
        description,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

private const val MIN_HEIGHT_FOR_ICON_DP = 480
