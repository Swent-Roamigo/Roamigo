package com.swent.roamigo.ui.heatmap

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment

@RunWith(AndroidJUnit4::class)
class DottedWorldMapTest {

  @Test
  fun parsesValidCoordinatesAndCountryCodes() {
    val dots =
        parseWorldDots(
            sequenceOf(
                "0.25,0.5,PT",
                "0.75,0.8,FR",
            )
        )

    assertEquals(
        listOf(
            WorldDot(0.25f, 0.5f, "PT"),
            WorldDot(0.75f, 0.8f, "FR"),
        ),
        dots,
    )
  }

  @Test
  fun rejectsInvalidCoordinateValues() {
    val dots =
        parseWorldDots(
            sequenceOf(
                "-0.1,0.5,PT",
                "1.1,0.5,FR",
                "0.5,-0.1,ES",
                "0.5,1.1,IT",
                "abc,0.5,CH",
                "0.5,abc,DE",
                "NaN,0.5,US",
                "0.5,Infinity,CA",
            )
        )

    assertTrue(dots.isEmpty())
  }

  @Test
  fun rejectsInvalidCountryCodesAndMalformedRows() {
    val dots =
        parseWorldDots(
            sequenceOf(
                "0.5,0.5,pt",
                "0.5,0.5,ABC",
                "0.5,0.5,P",
                "0.5,0.5,1T",
                "0.5,0.5",
                "invalid",
                "0.5,0.5,PT,extra",
            )
        )

    assertTrue(dots.isEmpty())
  }

  @Test
  fun acceptsBoundaryCoordinatesAndWhitespace() {
    val dots =
        parseWorldDots(
            sequenceOf(
                "0,0,PT",
                "1,1,FR",
                " 0.5 , 0.25 , CH ",
            )
        )

    assertEquals(3, dots.size)
    assertEquals(WorldDot(0f, 0f, "PT"), dots[0])
    assertEquals(WorldDot(1f, 1f, "FR"), dots[1])
    assertEquals(WorldDot(0.5f, 0.25f, "CH"), dots[2])
  }

  @Test
  fun emptyInputProducesEmptyList() {
    assertTrue(parseWorldDots(emptySequence()).isEmpty())
  }

  @Test
  fun mapsVisitCountsToCorrectColors() {
    assertEquals(HeatmapUnvisited, colorForVisits(0))
    assertEquals(HeatmapUnvisited, colorForVisits(-1))
    assertEquals(HeatmapLight, colorForVisits(1))
    assertEquals(HeatmapMedium, colorForVisits(2))
    assertEquals(HeatmapDark, colorForVisits(3))
    assertEquals(HeatmapDark, colorForVisits(100))
  }

  @Test
  fun visitedCountriesAreDrawnAfterUnvisitedCountries() {
    val dots =
        listOf(
            WorldDot(0.1f, 0.1f, "PT"),
            WorldDot(0.2f, 0.2f, "ES"),
            WorldDot(0.3f, 0.3f, "FR"),
        )

    val ordered = orderWorldDots(dots, mapOf("PT" to 2))

    assertEquals(
        listOf("ES", "FR", "PT"),
        ordered.map { it.countryCode },
    )
  }

  @Test
  fun countriesWithMoreVisitsAreDrawnLast() {
    val dots =
        listOf(
            WorldDot(0.1f, 0.1f, "PT"),
            WorldDot(0.2f, 0.2f, "FR"),
            WorldDot(0.3f, 0.3f, "ES"),
            WorldDot(0.4f, 0.4f, "CH"),
        )

    val counts =
        mapOf(
            "PT" to 3,
            "FR" to 1,
            "ES" to 2,
        )

    val ordered = orderWorldDots(dots, counts)

    assertEquals(
        listOf("CH", "FR", "ES", "PT"),
        ordered.map { it.countryCode },
    )
  }

  @Test
  fun preservesOrderForCountriesWithEqualVisitCounts() {
    val dots =
        listOf(
            WorldDot(0.1f, 0.1f, "FR"),
            WorldDot(0.2f, 0.2f, "PT"),
            WorldDot(0.3f, 0.3f, "ES"),
        )

    val ordered =
        orderWorldDots(
            dots,
            mapOf("FR" to 2, "PT" to 2, "ES" to 2),
        )

    assertEquals(dots, ordered)
  }

  @Test
  fun loadingMapIsNotConsideredUnavailable() {
    assertFalse(isWorldMapUnavailable(null))
  }

  @Test
  fun validMapIsNotConsideredUnavailable() {
    val result = Result.success(listOf(WorldDot(0.5f, 0.5f, "PT")))

    assertFalse(isWorldMapUnavailable(result))
  }

  @Test
  fun emptyMapDataIsConsideredUnavailable() {
    assertTrue(isWorldMapUnavailable(Result.success(emptyList())))
  }

  @Test
  fun failedMapLoadingIsConsideredUnavailable() {
    val result = Result.failure<List<WorldDot>>(IllegalStateException("Missing asset"))

    assertTrue(isWorldMapUnavailable(result))
  }

  @Test
  fun bundledWorldDataIsValidAndContainsCountries() {
    val context = RuntimeEnvironment.getApplication()

    val dots =
        context.assets.open("world_dots.csv").bufferedReader().use { reader ->
          parseWorldDots(reader.lineSequence())
        }

    assertTrue(dots.isNotEmpty())

    val countryCodes = dots.map { it.countryCode }.toSet()

    // Ensure countries mentioned in the review are represented.
    assertTrue(countryCodes.containsAll(setOf("HK", "MU", "SC", "BH")))

    // Every supported ISO country must have at least one map dot.
    val supportedCodes = Locale.getISOCountries().toSet() + "XK"
    val missingCodes = supportedCodes - countryCodes

    assertTrue(
        "Missing map points for: $missingCodes",
        missingCodes.isEmpty(),
    )

    assertFalse(dots.any { it.x !in 0f..1f || it.y !in 0f..1f })
  }
}
