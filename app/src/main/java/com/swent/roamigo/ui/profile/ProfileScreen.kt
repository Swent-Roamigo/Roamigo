// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.components.InfoRow
import com.swent.roamigo.ui.theme.Ochre
import com.swent.roamigo.ui.theme.Paper
import com.swent.roamigo.ui.theme.Spacing

// The footer is only drawn here; none of its destinations exist yet, so none is selected.
private val footerTabs =
    listOf(
        R.drawable.ic_calendar to "Trips",
        R.drawable.ic_map to "Map",
        R.drawable.ic_camera to "Photos",
    )

@Composable
fun ProfileScreen(state: ProfileUiState, modifier: Modifier = Modifier) {
  Scaffold(
      modifier = modifier,
      bottomBar = {
        NavigationBar {
          footerTabs.forEach { (icon, label) ->
            NavigationBarItem(
                selected = false,
                modifier = Modifier.testTag(C.Tag.profile_footer_tab),
                onClick = {},
                icon = { Icon(painterResource(icon), contentDescription = null) },
                label = { Text(label) },
            )
          }
        }
      },
  ) { padding ->
    // Scaffold reports the footer height so the content is not drawn underneath it.
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(
          "Profile",
          modifier = Modifier.testTag(C.Tag.profile_title),
          style = MaterialTheme.typography.headlineMedium,
      )
      Text(
          "The account you signed in with",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Column(
          modifier = Modifier.fillMaxWidth(),
          horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Box(
            modifier = Modifier.size(88.dp).background(Ochre, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
          Text(
              state.user.displayName.take(1),
              modifier = Modifier.testTag(C.Tag.profile_avatar),
              style = MaterialTheme.typography.displaySmall,
              color = Paper,
          )
        }
        Text(
            state.user.displayName,
            modifier = Modifier.testTag(C.Tag.profile_name),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "${state.email} · Signed in with Google",
            modifier = Modifier.testTag(C.Tag.profile_email),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
      }
      InfoRow(R.drawable.ic_user, "Display name", state.user.displayName) { EditAction("Edit") }
      InfoRow(R.drawable.ic_camera, "Profile picture", "Shown to your trip members") {
        EditAction("Change")
      }
      InfoRow(
          R.drawable.ic_users,
          "Friends",
          "${state.friendCount} friends · heatmaps visible to friends only",
          Modifier.testTag(C.Tag.profile_friends_row),
      ) {
        EditAction("View")
      }
      Spacer(Modifier.weight(1f))
      // No backend yet: these actions are intentionally inert.
      OutlinedButton(
          onClick = {},
          modifier = Modifier.fillMaxWidth().testTag(C.Tag.profile_log_out),
      ) {
        Text("Log out")
      }
    }
  }
}

@Composable
private fun EditAction(label: String) =
    TextButton(onClick = {}, modifier = Modifier.testTag(C.Tag.profile_action)) { Text(label) }
