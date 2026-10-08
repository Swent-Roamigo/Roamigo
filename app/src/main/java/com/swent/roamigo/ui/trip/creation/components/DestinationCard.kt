// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.roamigo.R
import com.swent.roamigo.ui.theme.RoamigoTheme

internal object DestinationCardTestTags {
  const val PREFIX = "destination_card_"

  fun forDestination(destination: String): String = "$PREFIX${destination.lowercase()}"
}

@Composable
fun DestinationCard(destination: String, selected: Boolean, onClick: () -> Unit) {
  val colors = MaterialTheme.colorScheme
  Box(
      modifier =
          Modifier.fillMaxWidth()
              .aspectRatio(169f / 128f)
              .clip(MaterialTheme.shapes.medium)
              .then(
                  if (selected) Modifier.border(3.dp, colors.primary, MaterialTheme.shapes.medium)
                  else Modifier
              )
              .clickable(onClick = onClick)
              .semantics {
                contentDescription = "$destination${if (selected) ", selected" else ""}"
                testTag = DestinationCardTestTags.forDestination(destination)
              },
  ) {
    Image(
        painter = painterResource(R.drawable.destination_placeholder),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f)))
    if (selected) {
      Box(
          Modifier.align(Alignment.TopEnd)
              .padding(8.dp)
              .size(24.dp)
              .clip(CircleShape)
              .background(colors.primary),
          contentAlignment = Alignment.Center,
      ) {
        Text("✓", color = colors.onPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
      }
    }
    Text(
        destination,
        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
        color = colors.onPrimary,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
  }
}

@Preview(showBackground = true, widthDp = 169)
@Composable
private fun DestinationCardPreview() {
  RoamigoTheme { DestinationCard("Lisbon", selected = true, onClick = {}) }
}
