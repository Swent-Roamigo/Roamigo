// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.swent.roamigo.ui.theme.Spacing

/**
 * The top of a screen: an optional back button, a small [eyebrow] line above the [title] (a
 * greeting), a [subtitle] below it, and an action or avatar at the end ([trailing]). [below] adds
 * more lines under the subtitle. Every screen uses this so titles keep one size, weight and place.
 *
 * @param onBack shows a back button when set; [backLabel] is its accessible name and [backModifier]
 *   lets the caller tag it.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    subtitle: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    onBack: (() -> Unit)? = null,
    backLabel: String? = null,
    backModifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
  check(onBack == null || backLabel != null) { "A back button needs a label for screen readers" }
  Row(
      modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
      if (onBack != null) {
        IconButton(onClick = onBack, modifier = backModifier) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = backLabel)
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall)) {
        if (eyebrow != null) {
          Text(
              eyebrow,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(bottom = Spacing.compact),
          )
        }
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = titleStyle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle != null) {
          Text(
              subtitle,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        below?.invoke(this)
      }
    }
    trailing?.invoke()
  }
}
