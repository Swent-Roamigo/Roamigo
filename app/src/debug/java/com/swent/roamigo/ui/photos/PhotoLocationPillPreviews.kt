// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.theme.Spacing

@Preview(showBackground = true)
@Composable
private fun PhotoLocationPillStatesPreview() {
  RoamigoTheme {
    Column(
        Modifier.padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      PhotoLocationPill(PhotoLocationStatus.Locating, onOpenMap = {}, onRetake = {})
      PhotoLocationPill(PhotoLocationStatus.Attached(46.52, 6.63), onOpenMap = {}, onRetake = {})
      PhotoLocationPill(PhotoLocationStatus.Unavailable, onOpenMap = {}, onRetake = {})
      PhotoLocationPill(PhotoLocationStatus.Off, onOpenMap = {}, onRetake = {})
      PhotoLocationPill(PhotoLocationStatus.NextPhotoOnly, onOpenMap = {}, onRetake = {})
      // Disabled, as while a photo is saving.
      PhotoLocationPill(
          PhotoLocationStatus.Off,
          onOpenMap = {},
          onRetake = {},
          enabled = false,
      )
      PhotoLocationPill(
          PhotoLocationStatus.NextPhotoOnly,
          onOpenMap = {},
          onRetake = {},
          enabled = false,
      )
    }
  }
}
