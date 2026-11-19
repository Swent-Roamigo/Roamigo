// Implemented with assistance from OpenAI Codex.
package com.swent.roamigo.ui.placepicker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.Spacing

/** Map and editable name share a scroll area; confirmation stays reachable above the keyboard. */
@Composable
fun PlacePickerMapScreen(
    state: PlacePickerUiState,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
    onRetryMap: () -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable (Modifier) -> Unit,
) {
  Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    BoxWithConstraints(Modifier.safeDrawingPadding().imePadding().padding(Spacing.screenPadding)) {
      val mapHeight = (maxHeight * 0.45f).coerceIn(120.dp, 360.dp)
      Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
          Row {
            IconButton(
                onClick = onBack,
                enabled = !state.finished,
                modifier = Modifier.testTag(PlacePickerTestTags.MAP_BACK),
            ) {
              Icon(
                  Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = stringResource(R.string.place_picker_map_back),
              )
            }
            Text(
                stringResource(R.string.place_picker_map_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
          }
          Text(stringResource(R.string.place_picker_map_instruction))
          mapContent(Modifier.fillMaxWidth().height(mapHeight).clip(MaterialTheme.shapes.large))
          if (state.mapError) {
            Text(
                stringResource(R.string.place_picker_map_error),
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier.testTag(PlacePickerTestTags.MAP_ERROR).semantics {
                      liveRegion = LiveRegionMode.Polite
                    },
            )
            TextButton(
                onClick = onRetryMap,
                enabled = !state.finished,
                modifier = Modifier.testTag(PlacePickerTestTags.MAP_RETRY),
            ) {
              Text(stringResource(R.string.place_picker_map_retry))
            }
          }
          OutlinedTextField(
              value = state.manualDraft.name,
              onValueChange = onNameChange,
              enabled = !state.finished && state.manualDraft.point != null,
              label = { Text(stringResource(R.string.place_picker_map_name)) },
              supportingText = { Text(stringResource(R.string.place_picker_map_name_hint)) },
              modifier = Modifier.fillMaxWidth().testTag(PlacePickerTestTags.MAP_NAME),
          )
        }
        Button(
            onClick = onConfirm,
            enabled = state.canConfirm,
            modifier = Modifier.fillMaxWidth().testTag(PlacePickerTestTags.CONFIRM),
        ) {
          Text(
              state.manualDraft.location?.let {
                stringResource(R.string.place_picker_confirm_named, it.name)
              } ?: stringResource(R.string.place_picker_confirm)
          )
        }
      }
    }
  }
}

@Composable
internal fun PlacePickerMapMarker() {
  Icon(
      painterResource(R.drawable.ic_map_marker),
      contentDescription = stringResource(R.string.place_picker_map_marker),
      tint = Color.Unspecified,
  )
}
