// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.photos

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.Spacing

/**
 * The sheet matching [step], or nothing for [CameraStep.NONE].
 *
 * @param onContinue the user agreed to be asked for the camera permission.
 * @param onOpenSettings the user wants to allow the camera in the app's settings.
 * @param onDismiss the user closed the sheet without choosing anything.
 */
@Composable
fun CameraPermissionSheet(
    step: CameraStep,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
  when (step) {
    CameraStep.NONE -> Unit
    CameraStep.RATIONALE ->
        PermissionSheet(
            sheetTag = C.Tag.camera_rationale_sheet,
            title = R.string.camera_rationale_title,
            description = R.string.camera_rationale_description,
            primaryText = R.string.camera_rationale_continue,
            primaryTag = C.Tag.camera_rationale_continue,
            onPrimary = onContinue,
            secondaryText = R.string.camera_rationale_not_now,
            secondaryTag = C.Tag.camera_rationale_not_now,
            onDismiss = onDismiss,
        )
    CameraStep.DENIED ->
        PermissionSheet(
            sheetTag = C.Tag.camera_denied_sheet,
            title = R.string.camera_denied_title,
            description = R.string.camera_denied_description,
            primaryText = R.string.camera_denied_got_it,
            primaryTag = C.Tag.camera_denied_got_it,
            onPrimary = onDismiss,
            onDismiss = onDismiss,
        )
    CameraStep.BLOCKED ->
        PermissionSheet(
            sheetTag = C.Tag.camera_blocked_sheet,
            title = R.string.camera_blocked_title,
            description = R.string.camera_blocked_description,
            primaryText = R.string.camera_blocked_open_settings,
            primaryTag = C.Tag.camera_blocked_open_settings,
            onPrimary = onOpenSettings,
            secondaryText = R.string.camera_rationale_not_now,
            secondaryTag = C.Tag.camera_blocked_not_now,
            onDismiss = onDismiss,
        )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionSheet(
    sheetTag: String,
    @StringRes title: Int,
    @StringRes description: Int,
    @StringRes primaryText: Int,
    primaryTag: String,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    @StringRes secondaryText: Int? = null,
    secondaryTag: String? = null,
) {
  ModalBottomSheet(
      onDismissRequest = onDismiss,
      sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
      containerColor = MaterialTheme.colorScheme.background,
  ) {
    Column(
        Modifier.fillMaxWidth()
            .testTag(sheetTag)
            .padding(horizontal = Spacing.screenPadding)
            .padding(bottom = Spacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
      Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
      Text(
          stringResource(description),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Button(
          onClick = onPrimary,
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag(primaryTag),
          shape = MaterialTheme.shapes.medium,
      ) {
        Text(stringResource(primaryText))
      }
      if (secondaryText != null && secondaryTag != null) {
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag(secondaryTag),
            shape = MaterialTheme.shapes.medium,
        ) {
          Text(stringResource(secondaryText))
        }
      }
    }
  }
}
