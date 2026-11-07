// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing

/**
 * The single entry point for adding something to a trip. Each choice closes the sheet first, so
 * Back and tapping outside return to the plan the sheet opened from.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToTripSheet(
    onDismiss: () -> Unit,
    onActivity: () -> Unit,
    onSuggestion: () -> Unit,
    onVote: () -> Unit,
    onPhoto: () -> Unit,
) {
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      modifier = Modifier.testTag(C.Tag.trip_add_sheet),
  ) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = Spacing.large)) {
      Text(
          text = stringResource(R.string.trip_add_menu),
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(horizontal = Spacing.screenPadding),
      )
      AddOption(R.string.trip_add_activity, R.string.trip_add_activity_hint) {
        onDismiss()
        onActivity()
      }
      AddOption(R.string.trip_add_suggestion, R.string.trip_add_suggestion_hint) {
        onDismiss()
        onSuggestion()
      }
      AddOption(R.string.trip_add_vote, R.string.trip_add_vote_hint) {
        onDismiss()
        onVote()
      }
      AddOption(R.string.trip_add_photo, R.string.trip_add_photo_hint) {
        onDismiss()
        onPhoto()
      }
    }
  }
}

@Composable
private fun AddOption(title: Int, hint: Int, onClick: () -> Unit) {
  Column(
      Modifier.fillMaxWidth()
          .heightIn(min = 56.dp)
          .clickable(onClick = onClick)
          .padding(horizontal = Spacing.screenPadding, vertical = Spacing.small)
  ) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
    Text(
        stringResource(hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
