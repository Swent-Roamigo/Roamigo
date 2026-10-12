// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.RoamigoTheme
import com.swent.roamigo.ui.theme.Spacing

/** Standalone creation flow; the caller owns the ViewModel and navigation. */
@Composable
fun CreateVoteScreen(
    viewModel: CreateVoteViewModel,
    onBack: () -> Unit,
    onVoteCreated: (String) -> Unit,
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val currentOnVoteCreated by rememberUpdatedState(onVoteCreated)
  LaunchedEffect(state.pendingVoteCreatedId) {
    viewModel.consumeVoteCreated()?.let { currentOnVoteCreated(it) }
  }
  CreateVoteScreenContent(
      state = state,
      onQuestionChange = viewModel::updateQuestion,
      onAddOption = viewModel::addOption,
      onOptionChange = viewModel::updateOption,
      onRemoveOption = viewModel::removeOption,
      onSubmit = viewModel::submitVote,
      onBack = onBack,
  )
}

@Composable
fun CreateVoteScreenContent(
    state: CreateVoteUiState,
    onQuestionChange: (String) -> Unit,
    onAddOption: () -> Unit,
    onOptionChange: (Int, String) -> Unit,
    onRemoveOption: (Int) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
  val editable = !state.isSubmitting && state.createdVoteId == null
  Column(
      Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.background)
          .safeDrawingPadding()
          .imePadding()
          .padding(horizontal = Spacing.screenPadding, vertical = Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(Spacing.large),
  ) {
    Text(stringResource(R.string.vote_new), style = MaterialTheme.typography.headlineLarge)
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.large),
    ) {
      VoteTextField(
          value = state.question,
          onValueChange = onQuestionChange,
          label = stringResource(R.string.vote_question),
          enabled = editable,
          modifier = Modifier.testTag("vote_question"),
      )
      Text(
          stringResource(R.string.vote_options_hint),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      state.options.forEachIndexed { index, option ->
        VoteTextField(
            value = option,
            onValueChange = { onOptionChange(index, it) },
            label = stringResource(R.string.vote_option, index + 1),
            enabled = editable,
            modifier = Modifier.testTag("vote_option_$index"),
            trailingIcon = {
              IconButton(onClick = { onRemoveOption(index) }, enabled = editable) {
                Icon(Icons.Default.Close, stringResource(R.string.vote_remove_option, index + 1))
              }
            },
        )
      }
      FilledTonalButton(onClick = onAddOption, enabled = editable) {
        Text(stringResource(R.string.vote_add_option))
      }
    }
    state.error?.let { error ->
      Text(
          stringResource(
              if (error == CreateVoteError.INVALID_INPUT) R.string.vote_invalid
              else R.string.vote_submission_error
          ),
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      )
    }
    Button(
        onClick = onSubmit,
        enabled = state.canSubmit,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("vote_submit"),
    ) {
      if (state.isSubmitting) {
        CircularProgressIndicator(Modifier.size(Spacing.extraLarge), strokeWidth = 2.dp)
        Spacer(Modifier.width(Spacing.small))
      }
      Text(stringResource(if (state.isSubmitting) R.string.vote_creating else R.string.vote_start))
    }
    OutlinedButton(
        onClick = onBack,
        enabled = !state.isSubmitting,
        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
    ) {
      Text(stringResource(R.string.vote_cancel))
    }
  }
}

@Composable
private fun VoteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
  OutlinedTextField(
      value = value,
      onValueChange = onValueChange,
      label = { Text(label) },
      enabled = enabled,
      modifier = modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.small,
      textStyle = MaterialTheme.typography.bodyLarge,
      trailingIcon = trailingIcon,
      colors =
          OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
          ),
  )
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CreateVotePreview() {
  RoamigoTheme {
    CreateVoteScreenContent(
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
