// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swent.roamigo.ui.theme.RoamigoTheme

@Composable
fun DestinationSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Row(
      modifier =
          Modifier.fillMaxWidth()
              .height(46.dp)
              .background(colors.surfaceVariant, RoundedCornerShape(14.dp))
              .padding(horizontal = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
  ) {
    SearchGlyph(Modifier.size(20.dp), colors.onSurfaceVariant)
    Spacer(Modifier.width(10.dp))
    Box(Modifier.fillMaxWidth()) {
      if (query.isEmpty()) {
        Text(
            "Search cities or countries",
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
      }
      BasicTextField(
          value = query,
          onValueChange = onQueryChange,
          modifier =
              Modifier.fillMaxWidth().semantics { contentDescription = "Search destinations" },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onBackground),
      )
    }
  }
}

/** Draws a search icon (magnifying glass) using a Canvas. */
@Composable
private fun SearchGlyph(modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color) {
  Canvas(modifier) {
    val center = Offset(size.width * 0.42f, size.height * 0.42f)
    drawCircle(
        color,
        radius = size.minDimension * 0.31f,
        center = center,
        style = Stroke(2.dp.toPx()),
    )
    drawLine(
        color,
        start = Offset(size.width * 0.64f, size.height * 0.64f),
        end = Offset(size.width * 0.9f, size.height * 0.9f),
        strokeWidth = 2.dp.toPx(),
        cap = StrokeCap.Round,
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun DestinationSearchBarPreview() {
  RoamigoTheme {
    DestinationSearchBar(
        query = "",
        onQueryChange = {},
    )
  }
}
