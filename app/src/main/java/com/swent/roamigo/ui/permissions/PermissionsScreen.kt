// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.InfoRow
import com.swent.roamigo.ui.theme.Spacing

// Informational only: Roamigo requests each permission in context, never up front.
private val permissionRows =
    listOf(
        Triple(
            R.drawable.ic_pin,
            "Location sharing",
            "Off until you turn it on · only trip members see it",
        ),
        Triple(R.drawable.ic_camera, "Camera", "Asked when you take a trip photo"),
        Triple(R.drawable.ic_bell, "Notifications", "Asked when you turn on vote alerts"),
        Triple(
            R.drawable.ic_users,
            "Trip members only",
            "Only invited friends can see or edit a trip",
        ),
    )

@Composable
fun PermissionsScreen(onContinue: () -> Unit, modifier: Modifier = Modifier) {
  Column(
      modifier = modifier.fillMaxSize().padding(Spacing.screenPadding),
      verticalArrangement = Arrangement.spacedBy(Spacing.medium),
  ) {
    // Only the content scrolls, so the button stays reachable with large fonts or small screens.
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(
          "We ask only when needed",
          modifier = Modifier.testTag(C.Tag.permissions_title),
          style = MaterialTheme.typography.headlineMedium,
      )
      Text(
          "No permissions up front. Roamigo asks in the moment, and you can review them any time in Settings.",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      permissionRows.forEach { (icon, title, subtitle) ->
        InfoRow(icon, title, subtitle, Modifier.testTag(C.Tag.permissions_row))
      }
    }
    Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth().testTag(C.Tag.permissions_continue),
    ) {
      Text("Continue")
    }
  }
}
