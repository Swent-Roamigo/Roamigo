// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.ui.theme.RoamigoTheme

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CreateVotePreview() {
  RoamigoTheme {
    CreateVoteScreen(
        state = CreateVoteUiState(),
        onQuestionChange = {},
        onAddOption = {},
        onOptionChange = { _, _ -> },
        onRemoveOption = {},
        onSubmit = {},
        onBack = {},
    )
  }
}
