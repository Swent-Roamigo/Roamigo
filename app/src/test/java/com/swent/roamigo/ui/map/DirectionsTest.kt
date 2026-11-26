// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import com.swent.roamigo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DirectionsTest {
  private val context: Context = RuntimeEnvironment.getApplication()

  @Test
  fun opensTheGeoUriWhenAMapsAppIsInstalled() {
    val navigator = FakeUriNavigator(installed = { it.scheme == "geo" })

    assertTrue(navigator.openDirectionsTo(LOUVRE))

    assertEquals(listOf("geo:0,0?q=48.8606,2.3376(Louvre%20Museum)"), navigator.opened())
  }

  @Test
  fun fallsBackToGoogleMapsInTheBrowserWithoutAMapsApp() {
    val navigator = FakeUriNavigator(installed = { it.scheme == "https" })

    assertTrue(navigator.openDirectionsTo(LOUVRE))

    assertEquals(
        listOf(
            "geo:0,0?q=48.8606,2.3376(Louvre%20Museum)",
            "https://www.google.com/maps/dir/?api=1&destination=48.8606,2.3376",
        ),
        navigator.attempted(),
    )
    assertEquals(
        listOf("https://www.google.com/maps/dir/?api=1&destination=48.8606,2.3376"),
        navigator.opened(),
    )
  }

  @Test
  fun reportsFailureWhenNeitherUriCanBeOpened() {
    val navigator = FakeUriNavigator(installed = { false })

    assertFalse(navigator.openDirectionsTo(LOUVRE))

    assertEquals(2, navigator.attempted().size)
    assertEquals(emptyList<String>(), navigator.opened())
  }

  @Test
  fun aMapsAppThatRefusesToStartFallsBackToTheBrowser() {
    val opened = mutableListOf<String>()
    val navigator = UriNavigator { uri ->
      if (uri.scheme == "geo") throw SecurityException("Not exported")
      opened += uri.toString()
    }

    assertTrue(navigator.openDirectionsTo(LOUVRE))

    assertEquals(
        listOf("https://www.google.com/maps/dir/?api=1&destination=48.8606,2.3376"),
        opened,
    )
  }

  @Test
  fun unexpectedFailuresAreNotMistakenForAMissingApp() {
    val navigator = UriNavigator { throw IllegalStateException("Broken navigator") }

    val failure = runCatching { navigator.openDirectionsTo(LOUVRE) }.exceptionOrNull()

    assertTrue(failure is IllegalStateException)
  }

  @Test
  fun theUserIsToldWhenNoAppCanShowDirections() {
    ShadowToast.reset()

    openDirectionsOrTellUser(context, FakeUriNavigator(installed = { false }), LOUVRE)

    assertEquals(
        context.getString(R.string.trip_map_directions_no_app),
        ShadowToast.getTextOfLatestToast(),
    )
  }

  @Test
  fun unexpectedFailuresAreToldToTheUserInsteadOfCrashing() {
    ShadowToast.reset()
    val navigator = UriNavigator { throw IllegalStateException("Broken navigator") }

    openDirectionsOrTellUser(context, navigator, LOUVRE)

    assertEquals(
        context.getString(R.string.trip_map_directions_no_app),
        ShadowToast.getTextOfLatestToast(),
    )
  }

  @Test
  fun noToastIsShownWhenDirectionsOpen() {
    ShadowToast.reset()

    openDirectionsOrTellUser(context, FakeUriNavigator(installed = { true }), LOUVRE)

    assertEquals(0, ShadowToast.shownToastCount())
  }

  @Test
  fun labelEscapesCharactersThatWouldEndOrSplitIt() {
    val uri = geoUriOf(LOUVRE.copy(title = "Café (rooftop) & bar"))

    assertEquals("geo:0,0?q=48.8606,2.3376(Caf%C3%A9%20%28rooftop%29%20%26%20bar)", uri.toString())
  }

  @Test
  fun coordinatesNearZeroAreWrittenWithoutScientificNotation() {
    val nearNullIsland = LOUVRE.copy(latitude = 0.00001, longitude = -0.00002)

    assertEquals(
        "https://www.google.com/maps/dir/?api=1&destination=0.00001,-0.00002",
        googleMapsDirectionsUriOf(nearNullIsland).toString(),
    )
    assertTrue(geoUriOf(nearNullIsland).toString().startsWith("geo:0,0?q=0.00001,-0.00002("))
  }

  /** Records every URI it is asked to open, and opens only those an [installed] app accepts. */
  private class FakeUriNavigator(private val installed: (Uri) -> Boolean) : UriNavigator {
    private val attempts = mutableListOf<Pair<Uri, Boolean>>()

    override fun openUri(uri: Uri) {
      val opened = installed(uri)
      attempts += uri to opened
      if (!opened) throw ActivityNotFoundException("No app for $uri")
    }

    fun attempted(): List<String> = attempts.map { it.first.toString() }

    fun opened(): List<String> = attempts.filter { it.second }.map { it.first.toString() }
  }

  private companion object {
    val LOUVRE =
        TripMarkerUiState(
            id = "trip-1_activity_louvre",
            tripId = "trip-1",
            title = "Louvre Museum",
            locationName = "Paris",
            latitude = 48.8606,
            longitude = 2.3376,
            kind = TripMarkerKind.ACTIVITY,
        )
  }
}
