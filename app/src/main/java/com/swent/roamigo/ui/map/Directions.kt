// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.ui.map

import android.content.ActivityNotFoundException
import android.net.Uri
import java.math.BigDecimal

/**
 * Opens directions to [destination] in an external app, so that Roamigo needs no navigation of its
 * own.
 *
 * A `geo:` URI is tried first, so that the user can choose any installed maps app. Without one, the
 * Google Maps directions page is opened in the browser instead.
 *
 * @return false if no app could open either URI, so that the caller can tell the user.
 */
fun UriNavigator.openDirectionsTo(destination: TripMarkerUiState): Boolean =
    listOf(geoUriOf(destination), googleMapsDirectionsUriOf(destination)).any { uri ->
      try {
        openUri(uri)
        true
      } catch (_: ActivityNotFoundException) {
        false
      }
    }

/** A `geo:` URI dropping a pin labelled with the destination's name at its coordinate. */
internal fun geoUriOf(destination: TripMarkerUiState): Uri {
  // Uri.encode leaves parentheses as they are, but they delimit the label in a geo URI.
  val label = Uri.encode(destination.title).replace("(", "%28").replace(")", "%29")
  return Uri.parse("geo:0,0?q=${coordinatesOf(destination)}($label)")
}

/** The Google Maps page giving directions to the destination, which any browser can open. */
internal fun googleMapsDirectionsUriOf(destination: TripMarkerUiState): Uri =
    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${coordinatesOf(destination)}")

// Double.toString switches to scientific notation near zero (1.0E-5), which maps apps do not read.
private fun coordinatesOf(destination: TripMarkerUiState): String =
    "${plain(destination.latitude)},${plain(destination.longitude)}"

private fun plain(degrees: Double): String =
    BigDecimal.valueOf(degrees).stripTrailingZeros().toPlainString()
