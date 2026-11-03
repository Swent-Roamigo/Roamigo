// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.ui.theme.RoamigoTheme

// The previews show the sheets' content only: previews do not render the bottom sheet's window.
class LocationPermissionSheetPreviews {
  @Preview(showBackground = true, widthDp = 390)
  @Composable
  private fun LocationRationaleSheetPreview() {
    LocationPermissionSheetPreview(sheet = LocationPermissionSheet.Rationale)
  }

  @Preview(showBackground = true, widthDp = 390)
  @Composable
  private fun LocationDeniedSheetPreview() {
    LocationPermissionSheetPreview(sheet = LocationPermissionSheet.Denied)
  }

  @Preview(showBackground = true, widthDp = 390)
  @Composable
  private fun LocationBlockedSheetPreview() {
    LocationPermissionSheetPreview(sheet = LocationPermissionSheet.Blocked)
  }

  @Composable
  private fun LocationPermissionSheetPreview(sheet: LocationPermissionSheet) {
    RoamigoTheme {
      // Same color as the bottom sheet's container.
      Surface(color = MaterialTheme.colorScheme.background) {
        LocationPermissionSheetContent(
            sheet = sheet,
            onContinue = {},
            onOpenSettings = {},
            onDismiss = {},
        )
      }
    }
  }
}
