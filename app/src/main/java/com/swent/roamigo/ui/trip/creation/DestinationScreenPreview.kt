// Author : Constantin Dunoyer de Segonzac
// Co-author : ChatGPT-Codex
package com.swent.roamigo.ui.trip.creation

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.ui.theme.RoamigoTheme

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun DestinationScreenPreview() {
  RoamigoTheme {
    DestinationScreenContent(
        uiState = CreateTripUiState(),
        onBack = {},
        onClose = {},
        onQueryChange = {},
        onToggleDestination = {},
        onRemoveDestination = {},
        onRetry = {},
        onRequestPhoto = {},
        onContinue = {},
    )
  }
}
