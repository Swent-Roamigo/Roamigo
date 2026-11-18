// Implemented with assistance from Claude (Anthropic) and OpenAI Codex.
package com.swent.roamigo.ui.photos

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.R
import com.swent.roamigo.model.photo.TripPhoto
import com.swent.roamigo.model.photo.tripPhoto
import com.swent.roamigo.resources.C
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "en-rUS-w360dp-h640dp")
class PhotoViewerTest {
  @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

  private var closes = 0
  private val photos =
      listOf(
          tripPhoto(
              "newest",
              uploadedBy = "alice",
              takenAtMillis = 1_724_000_000_000L,
              image = jpeg(Color.RED),
          ),
          tripPhoto(
              "middle",
              uploadedBy = "bob",
              takenAtMillis = 1_723_000_000_000L,
              image = jpeg(Color.BLUE),
          ),
          tripPhoto(
              "oldest",
              uploadedBy = "sam",
              takenAtMillis = 1_722_000_000_000L,
              image = jpeg(Color.GREEN),
          ),
      )
  private val names = mapOf("alice" to "Alice", "bob" to "Bob", "sam" to "Sam")

  private fun jpeg(color: Int): ByteArray {
    val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
    return try {
      bitmap.eraseColor(color)
      ByteArrayOutputStream().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        output.toByteArray()
      }
    } finally {
      bitmap.recycle()
    }
  }

  private fun showSingle(
      photo: TripPhoto = photos.first(),
      currentUserId: String? = null,
      photographerName: String? = null,
  ) {
    composeRule.setContent {
      RoamigoTheme {
        PhotoViewer(
            photo = photo,
            currentUserId = currentUserId,
            photographerName = photographerName,
            onClose = { closes++ },
        )
      }
    }
  }

  private fun showGroup(
      group: List<TripPhoto> = photos,
      photographerNames: Map<String, String> = names,
      fontScale: Float = 1f,
  ) {
    composeRule.setContent {
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
        RoamigoTheme {
          PhotoViewer(
              photos = group,
              photographerNames = photographerNames,
              onClose = { closes++ },
          )
        }
      }
    }
  }

  @Test
  fun showsASinglePhotoWithoutGroupControls() {
    showSingle(currentUserId = "alice")

    composeRule.onNodeWithTag(C.Tag.photo_viewer).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.photo_viewer_image).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.photo_viewer_caption).assertTextContains("You")
    composeRule.onNodeWithTag(C.Tag.photo_viewer_previous).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.photo_viewer_next).assertDoesNotExist()
    composeRule.onNodeWithTag(C.Tag.photo_viewer_position).assertDoesNotExist()
  }

  @Test
  fun showsTheSuppliedPhotographersDisplayName() {
    showSingle(photographerName = "  Alice Smith  ")

    composeRule.onNodeWithTag(C.Tag.photo_viewer_caption).assertTextContains("Alice Smith")
  }

  @Test
  fun usesAClearFallbackForMissingAndBlankNames() {
    showGroup(photographerNames = mapOf("alice" to "   "))

    composeRule.onNodeWithTag(C.Tag.photo_viewer_caption).assertTextContains("Unknown photographer")
    composeRule.onNodeWithTag(C.Tag.photo_viewer_next).performClick()
    composeRule.onNodeWithTag(C.Tag.photo_viewer_caption).assertTextContains("Unknown photographer")
  }

  @Test
  @Config(qualifiers = "fr-rFR-w360dp-h640dp")
  fun showsTheLocalizedCaptureDateAndTime() {
    showSingle(photographerName = "Alice")
    val expected =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.FRANCE)
            .format(Date(photos.first().takenAtMillis))

    composeRule.onNodeWithTag(C.Tag.photo_viewer_caption).assertTextContains(expected)
    val imageDescription =
        composeRule.activity.getString(R.string.photo_viewer_image_description, expected)
    composeRule.onNodeWithContentDescription(imageDescription).assertIsDisplayed()
  }

  @Test
  fun browsesTheWholeGroupInTheProvidedOrderAndDisablesTheEnds() {
    showGroup()

    composeRule
        .onNodeWithTag(C.Tag.photo_viewer_caption)
        .assertTextContains("Alice")
        .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    composeRule.onNodeWithTag(C.Tag.photo_viewer_position).assertTextContains("Photo 1 of 3")
    composeRule.onNodeWithContentDescription("Previous photo").assertIsNotEnabled()
    composeRule.onNodeWithContentDescription("Next photo").assertIsEnabled().performClick()
    composeRule.onNodeWithTag(C.Tag.photo_viewer_caption).assertTextContains("Bob")
    composeRule.onNodeWithTag(C.Tag.photo_viewer_position).assertTextContains("Photo 2 of 3")
    composeRule.onNodeWithContentDescription("Next photo").performClick()
    composeRule.onNodeWithTag(C.Tag.photo_viewer_caption).assertTextContains("Sam")
    composeRule.onNodeWithTag(C.Tag.photo_viewer_position).assertTextContains("Photo 3 of 3")
    composeRule.onNodeWithContentDescription("Next photo").assertIsNotEnabled()
    composeRule.onNodeWithContentDescription("Previous photo").assertIsEnabled().performClick()
    composeRule.onNodeWithTag(C.Tag.photo_viewer_caption).assertTextContains("Bob")
  }

  @Test
  fun browsingChangesTheDisplayedImage() {
    showGroup()
    waitForCenterColor { it.red > 0.8f && it.blue < 0.2f }

    composeRule.onNodeWithTag(C.Tag.photo_viewer_next).performClick()

    waitForCenterColor { it.blue > 0.8f && it.red < 0.2f }
  }

  private fun waitForCenterColor(matches: (androidx.compose.ui.graphics.Color) -> Boolean) {
    composeRule.waitUntil(timeoutMillis = 10_000) {
      val image = composeRule.onNodeWithTag(C.Tag.photo_viewer_image).captureToImage().toPixelMap()
      matches(image[image.width / 2, image.height / 2])
    }
  }

  @Test
  fun keepsTheSelectedPhotoWhenTheGroupChangesAndClosesIfItBecomesEmpty() {
    var group by mutableStateOf(photos)
    composeRule.setContent { RoamigoTheme { PhotoViewer(group, names, onClose = { closes++ }) } }
    composeRule.onNodeWithTag(C.Tag.photo_viewer_next).performClick()
    val added =
        tripPhoto(
            "added",
            uploadedBy = "alice",
            takenAtMillis = 1_725_000_000_000L,
            image = jpeg(Color.RED),
        )

    composeRule.runOnIdle { group = listOf(added) + photos }
    composeRule.onNodeWithTag(C.Tag.photo_viewer_caption).assertTextContains("Bob")
    composeRule.onNodeWithTag(C.Tag.photo_viewer_position).assertTextContains("Photo 3 of 4")

    composeRule.runOnIdle { group = listOf(added, photos.first(), photos.last()) }
    composeRule.onNodeWithTag(C.Tag.photo_viewer_caption).assertTextContains("Alice")
    composeRule.onNodeWithTag(C.Tag.photo_viewer_position).assertTextContains("Photo 1 of 3")

    composeRule.runOnIdle { group = emptyList() }
    composeRule.waitForIdle()
    assertEquals(1, closes)
  }

  @Test
  fun retainsTheSelectedPhotoAfterStateRestoration() {
    val restoration = StateRestorationTester(composeRule)
    restoration.setContent { RoamigoTheme { PhotoViewer(photos, names, onClose = { closes++ }) } }
    composeRule.onNodeWithTag(C.Tag.photo_viewer_next).performClick()

    restoration.emulateSavedInstanceStateRestore()

    composeRule.onNodeWithTag(C.Tag.photo_viewer_caption).assertTextContains("Bob")
    composeRule.onNodeWithTag(C.Tag.photo_viewer_position).assertTextContains("Photo 2 of 3")
  }

  @Test
  fun closeControlAndSystemBackCloseTheViewer() {
    showGroup()

    composeRule.onNodeWithContentDescription("Close").assertHasClickAction().performClick()
    assertEquals(1, closes)
    composeRule.runOnUiThread { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    composeRule.waitForIdle()
    assertEquals(2, closes)
  }

  @Test
  @Config(qualifiers = "en-rUS-w320dp-h480dp-xhdpi")
  fun controlsRemainUsableOnASmallScreen() {
    assertCompactViewerIsUsable()
  }

  @Test
  @Config(qualifiers = "en-rUS-w640dp-h280dp-land-xhdpi")
  fun controlsRemainUsableInLandscape() {
    assertCompactViewerIsUsable()
  }

  private fun assertCompactViewerIsUsable() {
    val longName = "A very long photographer name that wraps on a small screen"
    showGroup(photographerNames = names + ("alice" to longName), fontScale = 1.5f)
    composeRule
        .onNodeWithTag(C.Tag.photo_viewer_close)
        .assertIsDisplayed()
        .assertWidthIsAtLeast(48.dp)
        .assertHeightIsAtLeast(48.dp)
    composeRule.onNodeWithTag(C.Tag.photo_viewer_previous).assertIsDisplayed().assertIsNotEnabled()
    composeRule
        .onNodeWithTag(C.Tag.photo_viewer_next)
        .assertIsDisplayed()
        .assertIsEnabled()
        .assertWidthIsAtLeast(48.dp)
        .assertHeightIsAtLeast(48.dp)
    composeRule.onNodeWithTag(C.Tag.photo_viewer_position).assertIsDisplayed()
    composeRule.onNodeWithTag(C.Tag.photo_viewer_caption).assertIsDisplayed()
    val dateTime =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.US)
            .format(Date(photos.first().takenAtMillis))
    val textLayouts = mutableListOf<TextLayoutResult>()
    composeRule
        .onNodeWithText(dateTime, useUnmergedTree = true)
        .assertIsDisplayed()
        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
    assertTrue(textLayouts.isNotEmpty())
    val dateLayout = textLayouts.single()
    assertFalse(dateLayout.didOverflowHeight)
    assertFalse(
        "Date layout: size=${dateLayout.size}, textWidth=${dateLayout.multiParagraph.width}, " +
            "right=${dateLayout.getLineRight(0)}, lines=${dateLayout.lineCount}, text=$dateTime",
        dateLayout.didOverflowWidth,
    )

    val closeBounds =
        composeRule.onNodeWithTag(C.Tag.photo_viewer_close).fetchSemanticsNode().boundsInRoot
    val navigationBounds =
        composeRule.onNodeWithTag(C.Tag.photo_viewer_next).fetchSemanticsNode().boundsInRoot
    val captionBounds =
        composeRule.onNodeWithTag(C.Tag.photo_viewer_caption).fetchSemanticsNode().boundsInRoot
    assertTrue(closeBounds.bottom <= navigationBounds.top)
    assertTrue(navigationBounds.bottom <= captionBounds.top)
    composeRule.onNodeWithTag(C.Tag.photo_viewer_next).performClick()
    composeRule.onNodeWithTag(C.Tag.photo_viewer_previous).assertIsEnabled().performClick()
    composeRule.onNodeWithTag(C.Tag.photo_viewer_close).performClick()
    assertEquals(1, closes)
  }
}
