// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.ui.theme.RoamigoTheme

class LocationPermissionSheetPreviews {
  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun LocationRationaleSheetPreview() {
    LocationPermissionSheetPreview(sheet = LocationPermissionSheet.Rationale)
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun LocationDeniedSheetPreview() {
    LocationPermissionSheetPreview(sheet = LocationPermissionSheet.Denied)
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun LocationBlockedSheetPreview() {
    LocationPermissionSheetPreview(sheet = LocationPermissionSheet.Blocked)
  }

  @Composable
  private fun LocationPermissionSheetPreview(sheet: LocationPermissionSheet) {
    RoamigoTheme {
      LocationPermissionSheet(
          sheet = sheet,
          onContinue = {},
          onOpenSettings = {},
          onDismiss = {},
      )
    }
  }
}
