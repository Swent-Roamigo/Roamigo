package com.swent.roamigo.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme =
    lightColorScheme(
        primary = Slate,
        onPrimary = Paper,
        primaryContainer = SlateMist,
        onPrimaryContainer = Slate,
        // Figma calls ochre an accent, rather than defining a secondary brand color.
        secondary = Ochre,
        onSecondary = Ink,
        secondaryContainer = PaperSubtle,
        onSecondaryContainer = Ink,
        tertiary = Slate,
        onTertiary = Paper,
        tertiaryContainer = SlateMist,
        onTertiaryContainer = Slate,
        background = Paper,
        onBackground = Ink,
        surface = Paper,
        onSurface = Ink,
        surfaceVariant = PaperSubtle,
        onSurfaceVariant = InkMuted,
        surfaceTint = Slate,
        surfaceDim = Canvas,
        surfaceBright = Paper,
        surfaceContainerLowest = Paper,
        surfaceContainerLow = Paper,
        surfaceContainer = PaperSubtle,
        surfaceContainerHigh = Canvas,
        surfaceContainerHighest = Canvas,
        outline = InkMuted,
        outlineVariant = Border,
        inverseSurface = Slate,
        inverseOnSurface = Paper,
        inversePrimary = Paper,
    )

// Use only the Figma-defined light palette until a dark design is provided.
@Composable
fun RoamigoTheme(content: @Composable () -> Unit) {
  val colorScheme = LightColorScheme
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = true
    }
  }

  MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      shapes = Shapes,
      content = content,
  )
}
