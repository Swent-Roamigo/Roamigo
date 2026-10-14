// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.Spacing

@Composable
fun VoteActions(
    canSubmit: Boolean,
    isSubmitting: Boolean,
    hasPersistedBallot: Boolean,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
    Button(
        onClick = onSubmit,
        enabled = canSubmit,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
    ) {
      if (isSubmitting) {
        CircularProgressIndicator(Modifier.size(Spacing.extraLarge), strokeWidth = 2.dp)
        Spacer(Modifier.width(Spacing.small))
      }
      Text(
          stringResource(
              when {
                isSubmitting -> R.string.vote_submitting
                hasPersistedBallot -> R.string.vote_update
                else -> R.string.vote_submit
              }
          )
      )
    }
    OutlinedButton(
        onClick = onBack,
        enabled = !isSubmitting,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
    ) {
      Text(stringResource(R.string.vote_back))
    }
  }
}
