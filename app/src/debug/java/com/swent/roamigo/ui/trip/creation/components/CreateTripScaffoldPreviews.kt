// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.RoamigoTheme

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CreateTripScaffoldPreview() {
  RoamigoTheme {
    CreateTripScaffold(
        step = 1,
        title = stringResource(R.string.create_trip_destination_title),
        subtitle = stringResource(R.string.create_trip_destination_subtitle),
        onBack = {},
        onClose = {},
        primaryActionLabel = pluralStringResource(R.plurals.create_trip_destination_continue, 2, 2),
        onPrimaryAction = {},
    ) {
      Text(stringResource(R.string.preview_step_content), modifier = Modifier.padding(top = 8.dp))
    }
  }
}
