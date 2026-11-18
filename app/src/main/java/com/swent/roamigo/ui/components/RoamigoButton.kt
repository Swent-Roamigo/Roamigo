// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.BorderStroke
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

/** Minimum height shared by every main action, so switching tabs or screens does not resize it. */
val RoamigoButtonMinHeight: Dp = 52.dp

/** Emphasis of a [RoamigoButton]: the main action, a calmer one, or one that destroys something. */
enum class ButtonVariant {
  Primary,
  Secondary,
  Danger,
}

/**
 * The app's button. [ButtonVariant.Primary] is the dark main action, [ButtonVariant.Secondary] an
 * outline for a second action, and [ButtonVariant.Danger] a red one for something that cannot be
 * undone. While [loading] the button ignores clicks and reports itself as not enabled, but keeps
 * its normal colours so the action does not look different while it works.
 */
@Composable
fun RoamigoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    fillWidth: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  val sized =
      modifier
          .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
          .heightIn(min = RoamigoButtonMinHeight)
  val active = enabled && !loading
  when (variant) {
    ButtonVariant.Secondary ->
        OutlinedButton(
            onClick = onClick,
            modifier = sized,
            enabled = active,
            shape = MaterialTheme.shapes.small,
            border = BorderStroke(1.5.dp, colors.outlineVariant),
            colors =
                ButtonDefaults.outlinedButtonColors(
                    containerColor = colors.surface,
                    contentColor = colors.onSurface,
                    disabledContainerColor = colors.surface,
                    disabledContentColor =
                        if (loading) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
                ),
            content = content,
        )
    ButtonVariant.Primary,
    ButtonVariant.Danger -> {
      val base =
          if (variant == ButtonVariant.Danger) colors.error to colors.onError
          else colors.primary to colors.onPrimary
      Button(
          onClick = onClick,
          modifier = sized,
          enabled = active,
          shape = MaterialTheme.shapes.small,
          colors =
              ButtonDefaults.buttonColors(
                  containerColor = base.first,
                  contentColor = base.second,
                  disabledContainerColor =
                      if (loading) base.first else colors.onSurface.copy(alpha = 0.12f),
                  disabledContentColor =
                      if (loading) base.second else colors.onSurface.copy(alpha = 0.38f),
              ),
          content = content,
      )
    }
  }
}
