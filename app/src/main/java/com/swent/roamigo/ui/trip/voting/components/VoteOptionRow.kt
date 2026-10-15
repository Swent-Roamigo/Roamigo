// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.Spacing

@Composable
fun VoteOptionRow(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    voteCount: Int,
    onClick: () -> Unit,
) {
  Row(
      Modifier.fillMaxWidth()
          .heightIn(min = 56.dp)
          .background(
              if (selected) MaterialTheme.colorScheme.secondaryContainer
              else MaterialTheme.colorScheme.surfaceVariant,
              MaterialTheme.shapes.small,
          )
          .selectable(
              selected = selected,
              enabled = enabled,
              role = Role.RadioButton,
              onClick = onClick,
          )
          .padding(Spacing.medium),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    RadioButton(selected, onClick = null, enabled = enabled)
    Spacer(Modifier.width(Spacing.medium))
    Text(
        name,
        modifier = Modifier.weight(1f),
        style = MaterialTheme.typography.bodyLarge,
        color =
            if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.width(Spacing.small))
    Text(
        pluralStringResource(R.plurals.vote_count, voteCount, voteCount),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color =
            if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
