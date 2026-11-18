// AI assistance was used for implementation support.
package com.swent.roamigo.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ContentWidthTest {
  @get:Rule val composeRule = createComposeRule()

  private fun show(content: @Composable () -> Unit) {
    composeRule.setContent { content() }
  }

  @Test
  @Config(qualifiers = "w1000dp-h800dp")
  fun aWideScreenGetsAReadableCentredColumn() {
    // Tight constraints: the content fills its parent first.
    show { Box(Modifier.fillMaxSize().contentWidth().testTag("content")) }

    val node = composeRule.onNodeWithTag("content")
    node.assertWidthIsEqualTo(ContentMaxWidth)
    val bounds = node.getUnclippedBoundsInRoot()
    assertEquals(200f, bounds.left.value, 0.5f)
    assertEquals(800f, bounds.right.value, 0.5f)
  }

  @Test
  @Config(qualifiers = "w1000dp-h800dp")
  fun itCentresWhenItsParentPassesLooseConstraints() {
    // A weighted child of a Column gets loose width constraints, the case that used to hug the
    // left.
    show {
      Column(Modifier.fillMaxSize()) { Box(Modifier.weight(1f).contentWidth().testTag("content")) }
    }

    val bounds = composeRule.onNodeWithTag("content").getUnclippedBoundsInRoot()
    assertEquals(ContentMaxWidth.value, bounds.width.value, 0.5f)
    assertEquals(200f, bounds.left.value, 0.5f)
  }

  @Test
  @Config(qualifiers = "w360dp-h640dp")
  fun aPhoneKeepsTheFullWidth() {
    show { Box(Modifier.fillMaxSize().contentWidth().testTag("content")) }

    composeRule.onNodeWithTag("content").assertWidthIsEqualTo(360.dp)
  }
}
