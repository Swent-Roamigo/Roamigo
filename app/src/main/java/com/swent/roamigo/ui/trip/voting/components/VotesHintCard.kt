// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing

/** One-time explanation of how a group decides together, dismissed with "Got it". */
@Composable
fun VotesHintCard(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
  Surface(
      modifier = modifier.fillMaxWidth().testTag(C.Tag.votes_hint),
      shape = RoundedCornerShape(16.dp),
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Column(
        modifier = Modifier.padding(horizontal = 14.dp, vertical = Spacing.small),
        verticalArrangement = Arrangement.spacedBy(Spacing.extraSmall),
    ) {
      Text(
          stringResource(R.string.votes_hint_title),
          style = MaterialTheme.typography.titleSmall,
      )
      Text(
          stringResource(R.string.votes_hint_steps),
          style = MaterialTheme.typography.bodySmall,
      )
      TextButton(
          onClick = onDismiss,
          modifier = Modifier.heightIn(min = 48.dp).testTag(C.Tag.votes_hint_dismiss),
      ) {
        Text(stringResource(R.string.votes_hint_got_it))
      }
    }
  }
}
