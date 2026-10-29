// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.swent.roamigo.ui.components.markNotImplemented

@Composable
internal fun TripReadyCard(
    title: String,
    detail: String,
    tag: String,
    onClick: (() -> Unit)?,
) {
  val actionModifier =
      onClick?.let {
        Modifier.markNotImplemented(it).clickable(role = Role.Button, onClick = it).semantics {
          testTag = "trip_ready_$tag"
        }
      } ?: Modifier
  Column(
      Modifier.fillMaxWidth()
          .then(actionModifier)
          .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
          .padding(14.dp)
  ) {
    Text(
        title,
        Modifier.semantics { testTag = "trip_ready_${tag}_title" },
        style = MaterialTheme.typography.titleSmall,
    )
    Text(
        detail,
        Modifier.semantics { testTag = "trip_ready_${tag}_detail" },
        style = MaterialTheme.typography.bodySmall,
    )
  }
}
