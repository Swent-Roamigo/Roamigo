// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.LocalDate
import java.time.LocalTime

private val previewTrip =
    ActivityFormTrip("Lisbon → Porto", LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 15))

@Composable
private fun ActivityFormPreview(state: ActivityFormUiState) {
  RoamigoTheme {
    ActivityFormScreen(
        state = state,
        onNameChange = {},
        onTimeChange = {},
        onPlaceClick = {},
        onDescriptionChange = {},
        onSave = {},
        onClose = {},
        onDelete = {},
    )
  }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ActivityFormCreatePreview() {
  ActivityFormPreview(
      ActivityFormUiState(
          isEdit = false,
          trip = previewTrip,
          fields = ActivityFormFields(date = LocalDate.of(2026, 10, 13)),
      )
  )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ActivityFormEditPreview() {
  ActivityFormPreview(
      ActivityFormUiState(
          isEdit = true,
          trip = previewTrip,
          fields =
              ActivityFormFields(
                  name = "Dinner at Time Out Market",
                  date = LocalDate.of(2026, 10, 13),
                  time = LocalTime.of(19, 30),
                  place = TripLocation(38.7071, -9.1456, "Time Out Market, Lisbon"),
              ),
      )
  )
}

@Preview(showBackground = true, widthDp = 640, heightDp = 360)
@Composable
private fun ActivityFormErrorsLandscapePreview() {
  ActivityFormPreview(
      ActivityFormUiState(
          isEdit = false,
          trip = previewTrip,
          fields = ActivityFormFields(date = LocalDate.of(2026, 10, 18)),
          errors = listOf(ActivityFormError.BLANK_NAME, ActivityFormError.MISSING_PLACE),
          failure = ActivityFormFailure.NETWORK,
      )
  )
}
