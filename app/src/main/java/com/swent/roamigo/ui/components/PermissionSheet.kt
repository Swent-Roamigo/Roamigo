// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.components

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
import com.swent.roamigo.ui.theme.Spacing

/**
 * A fully expanded bottom sheet explaining a permission: a title, a description, a primary action,
 * and an optional secondary action that closes the sheet.
 *
 * @param sheetTag test tag of the sheet content.
 * @param title the sheet's title.
 * @param description why the permission is needed, or what the user can do about it.
 * @param primaryText label of the primary button.
 * @param primaryTag test tag of the primary button.
 * @param onPrimary called when the primary button is clicked.
 * @param onDismiss called when the user closes the sheet, by swiping it away, tapping outside it,
 *   or clicking the secondary button.
 * @param secondaryText label of the secondary button. The button is only shown when both
 *   [secondaryText] and [secondaryTag] are given.
 * @param secondaryTag test tag of the secondary button.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionSheet(
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
