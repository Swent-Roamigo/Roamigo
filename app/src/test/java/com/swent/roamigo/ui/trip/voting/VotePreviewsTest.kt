// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.voting

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.reflect.getDeclaredComposableMethod
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/** Smoke-test representative debug entry points; feature behavior is covered by the UI tests. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class VotePreviewsTest {
  @get:Rule val composeRule = createComposeRule()

  @OptIn(InternalComposeApi::class)
  private fun renderPreview(file: String, name: String, widthDp: Int = 390, fontScale: Float = 1f) {
    val entry =
        Class.forName("com.swent.roamigo.ui.trip.voting.${file}Kt")
            .getDeclaredComposableMethod(name)
    entry.asMethod().isAccessible = true
    composeRule.setContent {
      CompositionLocalProvider(
          LocalInspectionMode provides true,
          LocalDensity provides Density(LocalDensity.current.density, fontScale),
      ) {
        Box(Modifier.width(widthDp.dp)) { entry(currentComposer, null) }
      }
    }
    // Exercise drawing as well as composition without writing screenshot artifacts.
    val rendered = composeRule.onRoot().captureToImage()
    assertTrue(rendered.width > 0 && rendered.height > 0)
  }

  @Test fun openLiveCard() = renderPreview("VoteCardPreviews", "LiveVoteCastPreview")

  @Test fun decidedWinnerTab() = renderPreview("ClosedVoteCardPreviews", "DecidedWinnerTabPreview")

  @Test fun closedTieCard() = renderPreview("ClosedVoteCardPreviews", "ClosedTieCardPreview")

  @Test
  fun narrowLargeFontClosedTieCard() =
      renderPreview(
          "ClosedVoteCardPreviews",
          "ClosedNarrowCardPreview",
          widthDp = 280,
          fontScale = 1.5f,
      )

  @Test fun detailedWinnerScreen() = renderPreview("VoteResultPreviews", "ClosedWinnerPreview")

  @Test
  fun narrowDetailedComponent() =
      renderPreview(
          "VoteResultPreviews",
          "FinalResultComponentPreview",
          widthDp = 320,
          fontScale = 1.5f,
      )
}
