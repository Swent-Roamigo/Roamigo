// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.RoamigoTheme

// One preview per screen of the location permission flow (Figma flow E), each drawn over the dimmed
// trip map so that the S23 -> S24 -> S25/S26 sequence can be checked in the Preview pane. Previews
// do not render the bottom sheet's window, so S23, S25 and S26 redraw the sheet in its place.
class LocationPermissionSheetPreviews {
  @Preview(name = "S23 – Rationale sheet over the trip map", widthDp = 390, heightDp = 844)
  @Composable
  private fun S23LocationRationaleSheetPreview() {
    PermissionSheetOverMap(sheet = LocationPermissionSheet.Rationale)
  }

  // Stand-in for the Android 12+ system location dialog, which the system draws after "Continue"
  // on S23. The app never draws this dialog: this replica only exists so that S24 has a preview.
  @Preview(
      name = "S24 – System location dialog stand-in over the trip map",
      widthDp = 390,
      heightDp = 844,
  )
  @Composable
  private fun S24SystemLocationDialogStandInPreview() {
    TripMapBackground {
      Surface(
          modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
          shape = RoundedCornerShape(28.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
      ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Icon(
              painter = painterResource(R.drawable.ic_pin),
              contentDescription = null,
              modifier = Modifier.size(24.dp),
              tint = MaterialTheme.colorScheme.primary,
          )
          Text(
              text = "Allow Roamigo to access this device's location?",
              style = MaterialTheme.typography.titleLarge,
              textAlign = TextAlign.Center,
          )
          Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
              horizontalArrangement = Arrangement.SpaceEvenly,
          ) {
            AccuracyChoice(label = "Precise", isPrecise = true, isSelected = true)
            AccuracyChoice(label = "Approximate", isPrecise = false, isSelected = false)
          }
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("While using the app", "Only this time", "Don't allow").forEach { action ->
              FilledTonalButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text(action) }
            }
          }
        }
      }
    }
  }

  @Preview(name = "S25 – Denied sheet over the trip map", widthDp = 390, heightDp = 844)
  @Composable
  private fun S25LocationDeniedSheetPreview() {
    PermissionSheetOverMap(sheet = LocationPermissionSheet.Denied)
  }

  @Preview(name = "S26 – Blocked sheet over the trip map", widthDp = 390, heightDp = 844)
  @Composable
  private fun S26LocationBlockedSheetPreview() {
    PermissionSheetOverMap(sheet = LocationPermissionSheet.Blocked)
  }

  /** [sheet]'s content at the bottom of the dimmed trip map, shaped like the real bottom sheet. */
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun PermissionSheetOverMap(sheet: LocationPermissionSheet) {
    TripMapBackground {
      Surface(
          modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
          shape = BottomSheetDefaults.ExpandedShape,
          // Same color as the bottom sheet's container.
          color = MaterialTheme.colorScheme.background,
      ) {
        LocationPermissionSheetContent(
            sheet = sheet,
            onContinue = {},
            onOpenSettings = {},
            onDismiss = {},
        )
      }
    }
  }

  /** One of the dialog's accuracy choices, drawn as a map thumbnail like the system's. */
  @Composable
  private fun AccuracyChoice(label: String, isPrecise: Boolean, isSelected: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(
          modifier =
              Modifier.size(96.dp)
                  .border(
                      BorderStroke(
                          width = 3.dp,
                          color =
                              if (isSelected) MaterialTheme.colorScheme.primary
                              else Color.Transparent,
                      ),
                      shape = CircleShape,
                  )
                  .padding(6.dp)
                  .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
          contentAlignment = Alignment.Center,
      ) {
        if (isPrecise) {
          Icon(
              painter = painterResource(R.drawable.ic_pin),
              contentDescription = null,
              modifier = Modifier.size(28.dp),
              tint = MaterialTheme.colorScheme.primary,
          )
        } else {
          Box(
              Modifier.size(56.dp)
                  .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
          )
        }
      }
      Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
  }

  /** The loaded trip map with sharing off, dimmed like behind a modal, under [overlay]. */
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun TripMapBackground(overlay: @Composable BoxScope.() -> Unit) {
    RoamigoTheme {
      Box(modifier = Modifier.fillMaxSize()) {
        TripMapScreen(
            uiState = previewTripMapLoadedState,
            isSharing = false,
            onRetry = {},
            onTurnOnSharing = {},
            onPauseSharing = {},
        )
        Box(Modifier.fillMaxSize().background(BottomSheetDefaults.ScrimColor))
        overlay()
      }
    }
  }
}
