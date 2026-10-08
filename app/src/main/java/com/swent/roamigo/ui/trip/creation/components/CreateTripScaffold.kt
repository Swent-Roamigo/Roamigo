// AI assistance was used for implementation support and code review.
package com.swent.roamigo.ui.trip.creation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swent.roamigo.ui.theme.RoamigoTheme

internal object CreateTripScaffoldTestTags {
  const val PRIMARY_ACTION = "create_trip_primary_action"
}

@Composable
fun CreateTripScaffold(
    step: Int,
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    onClose: () -> Unit,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    primaryActionEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
  val colors = MaterialTheme.colorScheme
  Column(
      modifier =
          Modifier.fillMaxSize()
              .background(colors.background)
              .navigationBarsPadding()
              .padding(horizontal = 20.dp)
              .padding(top = 8.dp, bottom = 20.dp),
  ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
      HeaderAction(label = "Back", glyph = "‹", onClick = onBack)
      Text(
          "Step $step of 2",
          style = MaterialTheme.typography.labelMedium,
          color = colors.onSurfaceVariant,
      )
      HeaderAction(label = "Close", glyph = "×", onClick = onClose)
    }
    Spacer(Modifier.height(14.dp))
    Box(
        modifier =
            Modifier.fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(colors.outlineVariant)
    ) {
      Box(
          Modifier.fillMaxWidth((step / 2f).coerceIn(0f, 1f))
              .height(6.dp)
              .clip(RoundedCornerShape(3.dp))
              .background(colors.primary)
      )
    }
    Spacer(Modifier.height(14.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, color = colors.onBackground)
    if (subtitle != null) {
      Spacer(Modifier.height(6.dp))
      Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
    }
    Spacer(Modifier.height(16.dp))
    Column(modifier = Modifier.fillMaxWidth().weight(1f), content = content)
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = onPrimaryAction,
        modifier =
            Modifier.fillMaxWidth().height(50.dp).semantics {
              testTag = CreateTripScaffoldTestTags.PRIMARY_ACTION
            },
        enabled = primaryActionEnabled,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
      Text(primaryActionLabel, style = MaterialTheme.typography.labelLarge)
    }
  }
}

@Composable
private fun HeaderAction(label: String, glyph: String, onClick: () -> Unit) {
  Box(
      modifier =
          Modifier.size(36.dp)
              .clip(RoundedCornerShape(18.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
              .clickable(onClick = onClick)
              .semantics { contentDescription = label },
      contentAlignment = Alignment.Center,
  ) {
    Text(
        glyph,
        color = MaterialTheme.colorScheme.onBackground,
        fontSize = 24.sp,
        lineHeight = 24.sp,
    )
  }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun CreateTripScaffoldPreview() {
  RoamigoTheme {
    CreateTripScaffold(
        step = 1,
        title = "Where to?",
        subtitle = "Add one city or a whole route. You can change it later.",
        onBack = {},
        onClose = {},
        primaryActionLabel = "Continue · 2 stops",
        onPrimaryAction = {},
    ) {
      Text("Step content", modifier = Modifier.padding(top = 8.dp))
    }
  }
}
