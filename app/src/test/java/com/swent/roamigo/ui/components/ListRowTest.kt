// AI assistance was used for implementation support.
package com.swent.roamigo.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListRowTest {
  @get:Rule val composeRule = createComposeRule()

  @Test
  fun showsItsTextAndOpensOnClick() {
    var opened = 0
    composeRule.setContent {
      RoamigoTheme {
        ListRow(
            title = "London",
            onClick = { opened++ },
            modifier = Modifier.testTag("row"),
            subtitle = "24 – 26 Nov",
            details = "2 travelers",
            leading = { Text("lead") },
            afterTitle = { Text("live") },
            trailing = { Text("Shared") },
            showChevron = true,
        )
      }
    }

    listOf("London", "24 – 26 Nov", "2 travelers", "lead", "live", "Shared").forEach {
      composeRule.onNodeWithText(it).assertIsDisplayed()
    }
    composeRule.onNodeWithTag("row").performClick()

    assertEquals(1, opened)
  }

  @Test
  fun aRowWithOnlyATitleStillWorks() {
    composeRule.setContent { RoamigoTheme { ListRow("Only", onClick = {}) } }

    composeRule.onNodeWithText("Only").assertIsDisplayed()
  }

  @Test
  fun aRowIsAtLeastAsTallAsATouchTargetEvenWithTinyText() {
    composeRule.setContent {
      // Tiny text, so only the minimum height can make the row reach 48dp.
      val density = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 0.25f)) {
        RoamigoTheme { ListRow("Only", onClick = {}, modifier = Modifier.testTag("row")) }
      }
    }

    val height = composeRule.onNodeWithTag("row").getUnclippedBoundsInRoot().height
    assertTrue("Row is only $height tall", height >= 48.dp)
  }
}
