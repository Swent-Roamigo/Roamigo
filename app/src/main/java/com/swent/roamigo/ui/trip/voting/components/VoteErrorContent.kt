// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.Spacing

@Composable
fun VoteErrorContent(
    @StringRes messageResource: Int,
    onRetry: (() -> Unit)? = null,
    retryEnabled: Boolean = true,
) {
  Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
    Text(
        stringResource(messageResource),
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    if (onRetry != null) {
      OutlinedButton(onClick = onRetry, enabled = retryEnabled) {
        Text(stringResource(R.string.vote_retry))
      }
    }
  }
}
