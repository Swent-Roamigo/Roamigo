// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.ui.theme.RoamigoTheme

class TripMapScreenPreviews {
  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapLoadingPreview() {
    RoamigoTheme { TripMapContent(uiState = TripMapUiState.Loading, onRetry = {}) }
  }

  @Preview(showBackground = true, widthDp = 390, heightDp = 844)
  @Composable
  private fun TripMapErrorPreview() {
    RoamigoTheme {
      TripMapContent(
          uiState = TripMapUiState.Error(TripMapViewModel.LOAD_FAILED_MESSAGE),
          onRetry = {},
      )
    }
  }
}
