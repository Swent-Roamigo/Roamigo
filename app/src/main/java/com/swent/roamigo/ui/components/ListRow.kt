// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.Spacing

/**
 * A tappable card for one item of a list: a [leading] icon, a [title], an optional [afterTitle] (a
 * status), up to two lines of text, and a [trailing] chip or a chevron. Trips, history and the
 * all-trips map use it so their rows have the same size, border and spacing.
 */
@Composable
fun ListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    details: String? = null,
    leading: (@Composable () -> Unit)? = null,
    afterTitle: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    showChevron: Boolean = false,
) {
  Surface(
      onClick = onClick,
      modifier = modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.medium,
      color = MaterialTheme.colorScheme.surface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
  ) {
    Row(
        Modifier.padding(horizontal = Spacing.cardPadding, vertical = Spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      leading?.invoke()
      Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.compact)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        afterTitle?.invoke()
        // The subtitle stays on one line; the details may wrap so dates and counts are never cut.
        listOf(
                subtitle to MaterialTheme.typography.bodyMedium,
                details to MaterialTheme.typography.bodySmall,
            )
            .forEach { (text, style) ->
              if (text != null) {
                Text(
                    text,
                    style = style,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
              }
            }
      }
      trailing?.invoke()
      if (showChevron) {
        Icon(
            painter = painterResource(R.drawable.ic_c2_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
