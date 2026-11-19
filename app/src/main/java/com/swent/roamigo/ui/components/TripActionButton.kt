// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.swent.roamigo.ui.theme.Spacing

/** Minimum height shared by every trip tab's main action so switching tabs does not resize it. */
val TripActionButtonMinHeight: Dp = RoamigoButtonMinHeight

/** Space around a trip tab's main action, so it sits at the same place on every tab. */
val TripActionButtonPadding: PaddingValues =
    PaddingValues(horizontal = Spacing.screenPadding, vertical = Spacing.medium)

/**
 * Full-width main action of a trip tab. Every tab uses this so the button keeps the same height and
 * shape; [outlined] only changes emphasis for secondary actions. See [RoamigoButton] for [loading].
 */
@Composable
fun TripActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outlined: Boolean = false,
    loading: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) =
    RoamigoButton(
        onClick = onClick,
        modifier = modifier,
        variant = if (outlined) ButtonVariant.Secondary else ButtonVariant.Primary,
        loading = loading,
        content = content,
    )
