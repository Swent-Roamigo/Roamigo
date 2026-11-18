// AI assistance was used for implementation support.
package com.swent.roamigo.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ContentWidthTest {
  @get:Rule val composeRule = createComposeRule()

  private fun show(max: androidx.compose.ui.unit.Dp = ContentMaxWidth) {
    composeRule.setContent {
      // The screens apply it after fillMaxSize, which is what lets it centre the capped column.
      Box(Modifier.fillMaxSize().contentWidth(max).testTag("content"))
    }
  }

  @Test
  @Config(qualifiers = "w1000dp-h800dp")
  fun aWideScreenGetsAReadableCentredColumn() {
    show()

    val node = composeRule.onNodeWithTag("content")
    node.assertWidthIsEqualTo(ContentMaxWidth)
    val bounds = node.getUnclippedBoundsInRoot()
    assertEquals(200f, bounds.left.value, 0.5f)
    assertEquals(800f, bounds.right.value, 0.5f)
  }

  @Test
  @Config(qualifiers = "w360dp-h640dp")
  fun aPhoneKeepsTheFullWidth() {
    show()

    composeRule.onNodeWithTag("content").assertWidthIsEqualTo(360.dp)
  }

  @Test
  @Config(qualifiers = "w1000dp-h800dp")
  fun theCapCanBeChanged() {
    show(max = 400.dp)

    composeRule.onNodeWithTag("content").assertWidthIsEqualTo(400.dp)
  }
}
