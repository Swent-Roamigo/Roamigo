// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.ui.trip.creation.TripType

internal object TripTypeSelectorTestTags {
  const val SOLO = "c2_trip_type_solo"
  const val GROUP = "c2_trip_type_group"
}

@Composable
internal fun TripTypeSelector(type: TripType, onChange: (TripType) -> Unit) {
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .height(46.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .padding(4.dp),
      horizontalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    TripTypeOption(
        label = stringResource(R.string.c2_solo),
        selected = type == TripType.SOLO,
        tag = TripTypeSelectorTestTags.SOLO,
        modifier = Modifier.weight(1f),
        onClick = { onChange(TripType.SOLO) },
    )
    TripTypeOption(
        label = stringResource(R.string.c2_group),
        selected = type == TripType.GROUP,
        tag = TripTypeSelectorTestTags.GROUP,
        modifier = Modifier.weight(1f),
        onClick = { onChange(TripType.GROUP) },
    )
  }
}

@Composable
private fun TripTypeOption(
    label: String,
    selected: Boolean,
    tag: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
  Box(
      modifier =
          modifier
              .fillMaxSize()
              .clip(RoundedCornerShape(11.dp))
              .background(
                  if (selected) MaterialTheme.colorScheme.surface
                  else MaterialTheme.colorScheme.surfaceVariant
              )
              .clickable(role = Role.RadioButton, onClick = onClick)
              .semantics {
                this.selected = selected
                testTag = tag
              },
      contentAlignment = Alignment.Center,
  ) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
  }
}
