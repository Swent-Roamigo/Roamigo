// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trips.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C

/** Button that asks for an invitation link or code, to join someone else's trip. */
@Composable
internal fun JoinTripButton(onClick: () -> Unit) {
  TextButton(
      onClick = onClick,
      modifier = Modifier.semantics { testTag = C.Tag.trips_join_button },
  ) {
    Icon(imageVector = Icons.Outlined.GroupAdd, contentDescription = null)
    Spacer(modifier = Modifier.width(4.dp))
    Text(stringResource(R.string.join_with_code))
  }
}
