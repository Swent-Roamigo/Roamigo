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
import androidx.compose.ui.res.stringResource
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.InfoRow
import com.swent.roamigo.ui.theme.Spacing

// Informational only: Roamigo requests each permission in context, never up front.
private val permissionRows =
    listOf(
        Triple(
            R.drawable.ic_pin,
            R.string.permissions_location_title,
            R.string.permissions_location_description,
        ),
        Triple(
            R.drawable.ic_camera,
            R.string.permissions_camera_title,
            R.string.permissions_camera_description,
        ),
        Triple(
            R.drawable.ic_bell,
            R.string.permissions_notifications_title,
            R.string.permissions_notifications_description,
        ),
        Triple(
            R.drawable.ic_users,
            R.string.permissions_members_title,
            R.string.permissions_members_description,
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
          stringResource(R.string.permissions_title),
          modifier = Modifier.testTag(C.Tag.permissions_title),
          style = MaterialTheme.typography.headlineMedium,
      )
      Text(
          stringResource(R.string.permissions_description),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      permissionRows.forEach { (icon, title, subtitle) ->
        InfoRow(
            icon,
            stringResource(title),
            stringResource(subtitle),
            Modifier.testTag(C.Tag.permissions_row),
        )
      }
    }
    Button(
        onClick = onContinue,
        modifier = Modifier.fillMaxWidth().testTag(C.Tag.permissions_continue),
    ) {
      Text(stringResource(R.string.permissions_continue))
    }
  }
}
