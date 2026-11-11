// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.swent.roamigo.ui.theme.Spacing

/** Minimum height shared by every trip tab's main action so switching tabs does not resize it. */
val TripActionButtonMinHeight: Dp = 52.dp

/** Space around a trip tab's main action, so it sits at the same place on every tab. */
val TripActionButtonPadding: PaddingValues =
    PaddingValues(horizontal = Spacing.screenPadding, vertical = Spacing.medium)

/**
 * Full-width main action of a trip tab. Every tab uses this so the button keeps the same height and
 * shape; [outlined] only changes emphasis for secondary actions.
 */
@Composable
fun TripActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
  val sizedModifier = modifier.fillMaxWidth().heightIn(min = TripActionButtonMinHeight)
  if (outlined) {
    OutlinedButton(
        onClick = onClick,
        modifier = sizedModifier,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        content = content,
    )
  } else {
    Button(
        onClick = onClick,
        modifier = sizedModifier,
        shape = MaterialTheme.shapes.small,
        content = content,
    )
  }
}
