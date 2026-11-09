package com.swent.roamigo.ui.trips.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Displays a compact status label for trip cards. */
@Composable
internal fun StatusPill(text: String) {
  Surface(
      shape = MaterialTheme.shapes.small,
      color = MaterialTheme.colorScheme.surfaceVariant,
  ) {
    Text(
        text = text,
        modifier =
            Modifier.padding(
                horizontal = 10.dp,
                vertical = 6.dp,
            ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** Displays a section heading on the Trips Home screen. */
@Composable
internal fun SectionTitle(text: String) {
  Text(
      text = text,
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onBackground,
  )
}

/**
 * Displays a trip image or a theme-based placeholder when no image resource is available.
 *
 * @param imageRes the optional drawable resource to display.
 * @param modifier the modifier applied to the image or placeholder.
 */
@Composable
internal fun TripImage(
    @DrawableRes imageRes: Int?,
    modifier: Modifier = Modifier,
) {
  if (imageRes != null) {
    Image(
        painter = painterResource(imageRes),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
    )
  } else {
    Box(
        modifier =
            modifier.background(
                Brush.linearGradient(
                    colors =
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer,
                        )
                )
            )
    )
  }
}
