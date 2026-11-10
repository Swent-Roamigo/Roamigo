// AI assistance was used for implementation support.
package com.swent.roamigo.ui.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.ui.theme.RoamigoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripMarkerIconTest {
  @get:Rule val composeRule = createComposeRule()

  private fun show(kind: TripMarkerKind = TripMarkerKind.ACTIVITY) {
    composeRule.setContent {
      RoamigoTheme {
        // The map wraps each pin in a tagged, clickable annotation, as here.
        Box(Modifier.testTag("pin").clickable {}) { TripMarkerIcon("London", kind) }
      }
    }
  }

  @Test
  fun theWrappingAnnotationIsNamedAfterThePlaceItMarks() {
    show()

    composeRule.onNodeWithTag("pin").assertContentDescriptionEquals("London")
  }

  @Test
  fun aSuggestionPinIsNamedToo() {
    show(TripMarkerKind.SUGGESTION)

    composeRule.onNodeWithTag("pin").assertContentDescriptionEquals("London")
  }
}
