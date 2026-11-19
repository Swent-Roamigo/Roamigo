// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import com.swent.roamigo.R
import com.swent.roamigo.model.place.MIN_PLACE_QUERY_LENGTH
import com.swent.roamigo.model.place.PlaceCandidate
import com.swent.roamigo.ui.theme.Spacing

/** Renders picker state and forwards actions without owning the caller's draft. */
@Composable
fun PlacePickerScreen(
    state: PlacePickerUiState,
    onQueryChange: (String) -> Unit,
    onCandidateClick: (PlaceCandidate) -> Unit,
    onRetry: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    onChooseOnMap: () -> Unit = {},
) {
  Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Column(
        Modifier.safeDrawingPadding().imePadding().padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
      Column(
          Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(Spacing.medium),
      ) {
        Row {
          IconButton(
              onClick = onCancel,
              enabled = !state.finished,
              modifier = Modifier.testTag(PlacePickerTestTags.CANCEL),
          ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.place_picker_cancel),
            )
          }
          Text(
              stringResource(R.string.place_picker_title),
              style = MaterialTheme.typography.headlineMedium,
          )
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onRetry() }),
            label = { Text(stringResource(R.string.place_picker_search)) },
            enabled = !state.finished,
            modifier = Modifier.fillMaxWidth().testTag(PlacePickerTestTags.QUERY),
        )
        state.selectedLocation?.let {
          Text(
              stringResource(R.string.place_picker_selected, it.name),
              style = MaterialTheme.typography.titleMedium,
              modifier =
                  Modifier.testTag(PlacePickerTestTags.SELECTED).semantics {
                    liveRegion = LiveRegionMode.Polite
                  },
          )
        }
        if (state.resolvingCandidateId != null) {
          Column(Modifier.testTag(PlacePickerTestTags.RESOLVING)) {
            CircularProgressIndicator()
            Text(stringResource(R.string.place_picker_resolving))
          }
        } else
            when (state.searchStatus) {
              PlaceSearchStatus.INITIAL ->
                  if (
                      state.query.isNotBlank() && state.query.trim().length < MIN_PLACE_QUERY_LENGTH
                  ) {
                    Text(
                        pluralStringResource(
                            R.plurals.place_picker_short_query,
                            MIN_PLACE_QUERY_LENGTH,
                            MIN_PLACE_QUERY_LENGTH,
                        )
                    )
                  } else if (state.selectedLocation == null && state.resolutionError == null) {
                    Text(stringResource(R.string.place_picker_initial))
                  }
              PlaceSearchStatus.LOADING -> {
                CircularProgressIndicator(Modifier.testTag(PlacePickerTestTags.LOADING))
                Text(stringResource(R.string.place_picker_searching))
              }
              PlaceSearchStatus.EMPTY ->
                  Text(
                      stringResource(R.string.place_picker_empty),
                      Modifier.testTag(PlacePickerTestTags.EMPTY),
                  )
              PlaceSearchStatus.ERROR -> Unit
              PlaceSearchStatus.RESULTS ->
                  state.candidates.forEach { candidate ->
                    OutlinedButton(
                        onClick = { onCandidateClick(candidate) },
                        enabled = !state.finished,
                        modifier =
                            Modifier.fillMaxWidth()
                                .testTag(PlacePickerTestTags.candidate(candidate.id)),
                    ) {
                      Column(Modifier.fillMaxWidth()) {
                        Text(candidate.name, style = MaterialTheme.typography.titleSmall)
                        candidate.address?.takeIf(String::isNotBlank)?.let {
                          Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                      }
                    }
                  }
            }
        (state.resolutionError ?: state.searchError)?.let { error ->
          Text(
              stringResource(error.messageResource()),
              color = MaterialTheme.colorScheme.error,
              modifier =
                  Modifier.testTag(PlacePickerTestTags.ERROR).semantics {
                    liveRegion = LiveRegionMode.Polite
                  },
          )
          if (error == PlacePickerError.UNAVAILABLE || error == PlacePickerError.INVALID_PLACE) {
            TextButton(
                onClick = onRetry,
                enabled = !state.finished,
                modifier = Modifier.testTag(PlacePickerTestTags.RETRY),
            ) {
              Text(stringResource(R.string.place_picker_retry))
            }
          }
        }
        if (
            state.searchStatus == PlaceSearchStatus.INITIAL &&
                state.resolvingCandidateId == null &&
                state.query.trim().length >= MIN_PLACE_QUERY_LENGTH &&
                state.resolutionError == null
        ) {
          TextButton(
              onClick = onRetry,
              enabled = !state.finished,
              modifier = Modifier.testTag(PlacePickerTestTags.RETRY),
          ) {
            Text(stringResource(R.string.place_picker_retry))
          }
        }
        // Search Box supplies a plain-text notice. Never interpret provider text as markup or URLs.
        state.attribution?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
      }
      OutlinedButton(
          onClick = onChooseOnMap,
          enabled = !state.finished,
          modifier =
              Modifier.fillMaxWidth()
                  .testTag(
                      if (state.selectedLocation == null) PlacePickerTestTags.CHOOSE_ON_MAP
                      else PlacePickerTestTags.VIEW_ON_MAP
                  ),
      ) {
        Text(
            stringResource(
                if (state.selectedLocation == null) R.string.place_picker_choose_on_map
                else R.string.place_picker_view_on_map
            )
        )
      }
      val uriHandler = LocalUriHandler.current
      TextButton(
          onClick = { uriHandler.openUri("https://www.mapbox.com/about/maps/") },
          modifier = Modifier.testTag(PlacePickerTestTags.ATTRIBUTION),
      ) {
        Text(stringResource(R.string.place_picker_attribution))
      }
      Button(
          onClick = onConfirm,
          enabled = state.canConfirm,
          modifier = Modifier.fillMaxWidth().testTag(PlacePickerTestTags.CONFIRM),
      ) {
        Text(
            text =
                state.selectedLocation?.let {
                  stringResource(R.string.place_picker_confirm_named, it.name)
                } ?: stringResource(R.string.place_picker_confirm),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@StringRes
private fun PlacePickerError.messageResource(): Int =
    when (this) {
      PlacePickerError.CONFIGURATION -> R.string.place_picker_configuration
      PlacePickerError.INVALID_TOKEN -> R.string.place_picker_invalid_token
      PlacePickerError.QUOTA -> R.string.place_picker_quota
      PlacePickerError.INVALID_PLACE -> R.string.place_picker_invalid_place
      PlacePickerError.UNAVAILABLE -> R.string.place_picker_unavailable
    }
