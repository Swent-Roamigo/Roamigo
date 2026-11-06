package com.swent.roamigo.ui.heatmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.PaperSubtle
import com.swent.roamigo.ui.theme.Spacing
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class WorldDot(
    val x: Float,
    val y: Float,
    val countryCode: String,
)

internal fun parseWorldDots(lines: Sequence<String>): List<WorldDot> =
    lines
        .mapNotNull { line ->
          val parts = line.trim().split(',')
          if (parts.size != 3) return@mapNotNull null

          val x = parts[0].toFloatOrNull() ?: return@mapNotNull null
          val y = parts[1].toFloatOrNull() ?: return@mapNotNull null
          val code = parts[2].trim()

          if (x !in 0f..1f || y !in 0f..1f || code.length != 2 || !code.all { it in 'A'..'Z' }) {
            null
          } else {
            WorldDot(x, y, code)
          }
        }
        .toList()

internal val HeatmapUnvisited = Color(0xFFD7DFDC)
internal val HeatmapLight = Color(0xFFB7E9DF)
internal val HeatmapMedium = Color(0xFF66C9B8)
internal val HeatmapDark = Color(0xFF078C79)

internal fun colorForVisits(count: Int): Color =
    when {
      count <= 0 -> HeatmapUnvisited
      count == 1 -> HeatmapLight
      count == 2 -> HeatmapMedium
      else -> HeatmapDark
    }

/**
 * Draw countries with fewer visits first, so countries with more visits remain visible when
 * neighboring dots overlap.
 */
internal fun orderWorldDots(
    dots: List<WorldDot>,
    visitCounts: Map<String, Int>,
): List<WorldDot> = dots.sortedBy { dot -> visitCounts[dot.countryCode] ?: 0 }

/**
 * Null means the asset is still loading. A failed or empty result means that the map cannot be
 * displayed.
 */
internal fun isWorldMapUnavailable(
    result: Result<List<WorldDot>>?,
): Boolean = result != null && result.getOrNull().isNullOrEmpty()

@Composable
internal fun DottedWorldMap(
    visitCounts: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
  val context = LocalContext.current

  val dotsResult by
      produceState<Result<List<WorldDot>>?>(
          initialValue = null,
          key1 = context,
      ) {
        value =
            try {
              val loaded =
                  withContext(Dispatchers.IO) {
                    context.assets.open("world_dots.csv").bufferedReader().use { reader ->
                      parseWorldDots(reader.lineSequence())
                    }
                  }
              Result.success(loaded)
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              Result.failure(e)
            }
      }

  val dots = dotsResult?.getOrNull().orEmpty()
  val mapUnavailable = isWorldMapUnavailable(dotsResult)

  val drawingDots = remember(dots, visitCounts) { orderWorldDots(dots, visitCounts) }

  val description = stringResource(R.string.heatmap_map_description)

  Column(
      modifier =
          modifier
              .fillMaxWidth()
              .aspectRatio(1.75f)
              .clip(MaterialTheme.shapes.large)
              .background(PaperSubtle)
              .padding(Spacing.medium),
      verticalArrangement = Arrangement.spacedBy(Spacing.small),
  ) {
    if (mapUnavailable) {
      Box(
          modifier = Modifier.fillMaxWidth().weight(1f),
          contentAlignment = Alignment.Center,
      ) {
        Text(
            text = stringResource(R.string.heatmap_map_unavailable),
            color = MaterialTheme.colorScheme.error,
        )
      }
    } else {
      Canvas(
          modifier =
              Modifier.fillMaxWidth().weight(1f).semantics { contentDescription = description },
      ) {
        val radius = min(size.width / 110f, size.height / 30f)

        drawingDots.forEach { dot ->
          drawCircle(
              color = colorForVisits(visitCounts[dot.countryCode] ?: 0),
              radius = radius,
              center =
                  Offset(
                      dot.x * size.width,
                      dot.y * size.height,
                  ),
          )
        }
      }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
          text = stringResource(R.string.heatmap_less),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      listOf(
              HeatmapUnvisited,
              HeatmapLight,
              HeatmapMedium,
              HeatmapDark,
          )
          .forEach { color ->
            Spacer(
                modifier = Modifier.size(11.dp).clip(CircleShape).background(color),
            )
          }

      Text(
          text = stringResource(R.string.heatmap_more),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
