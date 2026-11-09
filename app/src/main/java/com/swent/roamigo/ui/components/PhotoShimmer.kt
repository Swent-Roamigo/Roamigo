// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag

/**
 * Soft moving highlight shown where a photo is still on its way, so the card looks busy rather than
 * empty. It only changes how loading looks: it does not make the photo arrive sooner.
 */
@Composable
fun PhotoShimmer(modifier: Modifier = Modifier) {
  val base = MaterialTheme.colorScheme.surfaceVariant
  val highlight = MaterialTheme.colorScheme.surface.copy(alpha = HIGHLIGHT_ALPHA)
  val offset by
      rememberInfiniteTransition(label = "photoShimmer")
          .animateFloat(
              initialValue = -SWEEP_PX,
              targetValue = SWEEP_PX * 2,
              animationSpec =
                  infiniteRepeatable(
                      animation = tween(SWEEP_MILLIS, easing = LinearEasing),
                      repeatMode = RepeatMode.Restart,
                  ),
              label = "photoShimmerOffset",
          )
  Box(
      modifier
          .fillMaxSize()
          .semantics { testTag = PHOTO_SHIMMER_TAG }
          .background(base)
          .background(
              Brush.linearGradient(
                  colors = listOf(Color.Transparent, highlight, Color.Transparent),
                  start = androidx.compose.ui.geometry.Offset(offset, 0f),
                  end = androidx.compose.ui.geometry.Offset(offset + SWEEP_PX, SWEEP_PX / 2),
              )
          )
  )
}

/** Test tag of the shimmer. */
const val PHOTO_SHIMMER_TAG = "photo_shimmer"

/** Longest time the shimmer is shown for a photo that may never exist. */
const val PHOTO_SHIMMER_MAX_MILLIS = 6_000L

/** Fade-in time once a photo has arrived. */
const val PHOTO_FADE_IN_MILLIS = 300

private const val SWEEP_PX = 600f
private const val SWEEP_MILLIS = 1_200
private const val HIGHLIGHT_ALPHA = 0.6f
