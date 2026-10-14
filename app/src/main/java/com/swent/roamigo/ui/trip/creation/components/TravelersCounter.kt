// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.ui.trip.creation.MIN_GROUP_TRAVELERS

internal object TravelersCounterTestTags {
  const val DECREMENT = "c2_travelers_decrement"
  const val COUNT = "c2_traveler_count"
  const val INCREMENT = "c2_travelers_increment"
}

@Composable
internal fun TravelersCounter(
    count: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
  Row(
      modifier = Modifier.fillMaxWidth().height(44.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
        painter = painterResource(R.drawable.ic_users),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
    Text(
        text = stringResource(R.string.c2_travelers),
        modifier = Modifier.padding(start = 12.dp).weight(1f),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    TravelerButton(
        description = stringResource(R.string.c2_decrease_travelers),
        tag = TravelersCounterTestTags.DECREMENT,
        icon = R.drawable.ic_c2_minus,
        enabled = count > MIN_GROUP_TRAVELERS,
        onClick = onDecrement,
    )
    Text(
        text = count.toString(),
        modifier =
            Modifier.padding(horizontal = 14.dp).semantics {
              testTag = TravelersCounterTestTags.COUNT
            },
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    TravelerButton(
        description = stringResource(R.string.c2_increase_travelers),
        tag = TravelersCounterTestTags.INCREMENT,
        icon = R.drawable.ic_c2_plus,
        enabled = count < 10,
        onClick = onIncrement,
    )
  }
}

@Composable
private fun TravelerButton(
    description: String,
    tag: String,
    icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
  Box(
      modifier =
          Modifier.size(36.dp)
              .clip(CircleShape)
              .background(
                  if (enabled) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.surfaceVariant
              )
              .clickable(enabled = enabled, onClick = onClick)
              .semantics {
                contentDescription = description
                testTag = tag
              },
      contentAlignment = Alignment.Center,
  ) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint =
            if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
  }
}
