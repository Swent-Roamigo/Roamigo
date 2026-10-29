// AI assistance was used for implementation support.
package com.swent.roamigo.ui.trip.creation

import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.Timestamp
import com.swent.roamigo.R
import com.swent.roamigo.model.TripLocation
import com.swent.roamigo.model.trip.*
import com.swent.roamigo.ui.isNotImplemented
import com.swent.roamigo.ui.theme.RoamigoTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w800dp-h1600dp")
class TripReadyScreenTest {
  @get:Rule val rule = createComposeRule()

  private fun trip(): Trip {
    val start = LocalDate.of(2027, 1, 10).atStartOfDay(ZoneId.systemDefault()).toInstant()
    val end = LocalDate.of(2027, 1, 17).atStartOfDay(ZoneId.systemDefault()).toInstant()
    return Trip(
        "trip",
        "Lisbon → Porto",
        null,
        listOf(TripLocation(1.0, 2.0, "Lisbon"), TripLocation(3.0, 4.0, "Porto")),
        Timestamp(start.epochSecond, 0),
        Timestamp(end.epochSecond, 0),
        false,
        TravelMode.SOLO,
        1,
        "owner",
        listOf("owner"),
        TripStatus.PLANNED,
        Timestamp(start.epochSecond, 0),
    )
  }

  @Test
  fun summaryAndUnavailableActionsAreAccessible() {
    rule.setContent { RoamigoTheme { TripReadyContent(TripReadyState.Ready(trip(), 8), null) } }
    rule
        .onNodeWithTag(TripReadyTestTags.TITLE)
        .assertIsDisplayed()
        .assertTextEquals("Your trip is ready!")
    rule.onNodeWithTag(TripReadyTestTags.ROUTE).assertTextEquals("Lisbon → Porto")
    rule.onNodeWithTag(TripReadyTestTags.SUMMARY).assertTextEquals("2 stops · 8 days")
    rule
        .onNodeWithTag(TripReadyTestTags.LOCATION, useUnmergedTree = true)
        .assertTextEquals("Share your location (optional)")
    listOf(
            TripReadyTestTags.INVITE_CARD,
            TripReadyTestTags.SUGGEST_CARD,
            TripReadyTestTags.LOCATION_CARD,
        )
        .forEach { tag ->
          rule
              .onNodeWithTag(tag)
              .performScrollTo()
              .assertHasClickAction()
              .assert(isNotImplemented)
              .performClick()
          assertEquals(
              RuntimeEnvironment.getApplication().getString(R.string.feature_not_implemented),
              ShadowToast.getTextOfLatestToast(),
          )
        }
    rule
        .onNodeWithTag(TripReadyTestTags.ITINERARY)
        .performScrollTo()
        .assertTextEquals("Open itinerary")
        .assertHasClickAction()
        .assert(isNotImplemented)
        .performClick()
    assertEquals(
        RuntimeEnvironment.getApplication().getString(R.string.feature_not_implemented),
        ShadowToast.getTextOfLatestToast(),
    )
    rule
        .onNodeWithTag(TripReadyTestTags.SHARE)
        .performScrollTo()
        .assertTextEquals("Share trip")
        .assertHasClickAction()
        .assert(isNotImplemented)
        .performClick()
    assertEquals(
        RuntimeEnvironment.getApplication().getString(R.string.feature_not_implemented),
        ShadowToast.getTextOfLatestToast(),
    )
  }

  @Test
  fun loadingThenErrorCanRetryAndShowTrip() {
    val gate = CompletableDeferred<Unit>()
    var fail = true
    val delegate = FakeTripRepository(trips = listOf(trip()))
    val repository =
        object : TripRepository by delegate {
          override suspend fun getTrip(tripId: String): Trip? {
            gate.await()
            if (fail) throw IllegalStateException("offline")
            return delegate.getTrip(tripId)
          }
        }
    val vm = TripReadyViewModel(repository, "trip")
    rule.setContent { RoamigoTheme { TripReadyScreen(vm, null) } }
    rule.onNodeWithTag(TripReadyTestTags.LOADING).assertExists()
    gate.complete(Unit)
    rule.waitUntil(5_000) {
      rule.onAllNodesWithTag(TripReadyTestTags.ERROR).fetchSemanticsNodes().isNotEmpty()
    }
    val screenCenterX =
        rule.onRoot().fetchSemanticsNode().boundsInRoot.let { (it.left + it.right) / 2f }
    listOf(TripReadyTestTags.ERROR, TripReadyTestTags.RETRY).forEach { tag ->
      val bounds = rule.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
      assertEquals(screenCenterX, (bounds.left + bounds.right) / 2f, 1f)
    }
    fail = false
    rule.onNodeWithTag(TripReadyTestTags.RETRY).performClick()
    rule.waitUntil(5_000) {
      rule.onAllNodesWithTag("trip_ready").fetchSemanticsNodes().isNotEmpty()
    }
    rule.onNodeWithTag(TripReadyTestTags.ROUTE).assertTextEquals("Lisbon → Porto")
  }

  @Test
  fun loadedPhotoShowsAttribution() {
    val photo =
        Bundle().apply {
          putString(
              "url",
              "android.resource://com.swent.roamigo/${R.drawable.destination_placeholder}",
          )
          putString("creator", "Photographer")
          putString("title", "City skyline")
          putString("credit", "Credit line")
          putString("license", "CC BY")
          putString("source", "https://example.com/photo")
          putString("licenseUrl", "https://example.com/license")
        }
    rule.setContent { RoamigoTheme { TripReadyContent(TripReadyState.Ready(trip(), 8), photo) } }
    rule.waitUntil(5_000) {
      rule.onAllNodesWithTag(TripReadyTestTags.PHOTO_CREDITS).fetchSemanticsNodes().isNotEmpty()
    }
    rule.onNodeWithTag(TripReadyTestTags.PHOTO_CREDITS).performClick()
    rule.onNodeWithTag(TripReadyTestTags.PHOTO_TITLE).assertTextEquals("City skyline")
    rule.onNodeWithTag(TripReadyTestTags.PHOTO_CREATOR).assertTextEquals("Photo: Photographer")
    rule.onNodeWithTag(TripReadyTestTags.PHOTO_CREDIT).assertTextEquals("Credit line")
    rule.onNodeWithTag(TripReadyTestTags.PHOTO_CLOSE).performClick()
    rule.onNodeWithTag(TripReadyTestTags.PHOTO_TITLE).assertDoesNotExist()
  }

  @Test
  fun photoWithoutCreatorUsesPublicDomainCredit() {
    val photo =
        Bundle().apply {
          putString(
              "url",
              "android.resource://com.swent.roamigo/${R.drawable.destination_placeholder}",
          )
          putString("license", "Public domain")
        }
    rule.setContent { RoamigoTheme { TripReadyContent(TripReadyState.Ready(trip(), 8), photo) } }
    rule.waitUntil(5_000) {
      rule.onAllNodesWithTag(TripReadyTestTags.PHOTO_CREDITS).fetchSemanticsNodes().isNotEmpty()
    }
    rule.onNodeWithTag(TripReadyTestTags.PHOTO_CREDITS).performClick()
    val creator =
        RuntimeEnvironment.getApplication()
            .getString(R.string.destination_photo_public_domain_creator)
    rule
        .onNodeWithTag(TripReadyTestTags.PHOTO_CREATOR)
        .assertTextEquals(
            RuntimeEnvironment.getApplication()
                .getString(R.string.destination_photo_creator, creator)
        )
    rule.onNodeWithTag(TripReadyTestTags.PHOTO_CLOSE).performClick()
  }

  @Test
  fun invalidPhotoUsesPlaceholderWithoutCredits() {
    val photo = Bundle().apply { putString("url", "android.resource://com.swent.roamigo/0") }
    rule.setContent { RoamigoTheme { TripReadyContent(TripReadyState.Ready(trip(), 8), photo) } }
    rule.waitForIdle()
    rule.onNodeWithTag(TripReadyTestTags.TITLE).assertTextEquals("Your trip is ready!")
    rule.onNodeWithTag(TripReadyTestTags.PHOTO_CREDITS).assertDoesNotExist()
  }
}
