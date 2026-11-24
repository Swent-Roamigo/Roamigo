// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.swent.roamigo.model.destination.DESTINATION_USER_AGENT
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenMeteoWeatherRepositoryTest {
  private val now = Instant.parse("2026-11-23T10:00:00Z")
  private val clock = Clock.fixed(now, ZoneOffset.UTC)
  private val today = LocalDate.of(2026, 11, 23)

  private fun daily(
      times: String = """["2026-11-28","2026-11-29"]""",
      codes: String = "[0,3]",
      highs: String = "[16.4,15.5]",
      lows: String = "[9.6,9.0]",
      rain: String = "[5,80]",
  ) =
      """{"daily":{"time":$times,"weather_code":$codes,"temperature_2m_max":$highs,"temperature_2m_min":$lows,"precipitation_probability_max":$rain}}"""

  private fun repository(
      json: String?,
      status: Int = 200,
      onRequest: (Request) -> Unit = {},
  ) = OpenMeteoWeatherRepository(client = stubClient(json, status, onRequest), clock = clock)

  private suspend fun OpenMeteoWeatherRepository.trip() =
      forecast(38.7223, -9.1393, LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 29))

  @Test
  fun asksForTheDailyForecastOfTheTripDates() = runTest {
    var request: Request? = null

    repository(daily(), onRequest = { request = it }).trip()

    val url = requireNotNull(request).url
    assertEquals("https", url.scheme)
    assertEquals("api.open-meteo.com", url.host)
    assertEquals("/v1/forecast", url.encodedPath)
    assertEquals("38.722", url.queryParameter("latitude"))
    assertEquals("-9.139", url.queryParameter("longitude"))
    assertEquals(
        "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max",
        url.queryParameter("daily"),
    )
    assertEquals("auto", url.queryParameter("timezone"))
    assertEquals("2026-11-28", url.queryParameter("start_date"))
    assertEquals("2026-11-29", url.queryParameter("end_date"))
    assertEquals(DESTINATION_USER_AGENT, requireNotNull(request).header("User-Agent"))
  }

  @Test
  fun readsTheDaysWithWholeDegreesAndTheRainChance() = runTest {
    val forecast = repository(daily()).trip()

    assertEquals(now, forecast.fetchedAt)
    assertEquals(false, forecast.fromCache)
    assertEquals(
        listOf(
            DailyForecast(LocalDate.of(2026, 11, 28), WeatherCondition.SUNNY, 16, 10, 5),
            DailyForecast(LocalDate.of(2026, 11, 29), WeatherCondition.CLOUDY, 16, 9, 80),
        ),
        forecast.days,
    )
  }

  @Test
  fun turnsWeatherCodesIntoConditions() = runTest {
    val expected =
        mapOf(
            0 to WeatherCondition.SUNNY,
            1 to WeatherCondition.SUNNY,
            2 to WeatherCondition.PARTLY_CLOUDY,
            3 to WeatherCondition.CLOUDY,
            45 to WeatherCondition.CLOUDY,
            48 to WeatherCondition.CLOUDY,
            53 to WeatherCondition.RAIN,
            63 to WeatherCondition.RAIN,
            81 to WeatherCondition.RAIN,
            95 to WeatherCondition.RAIN,
            51 to WeatherCondition.RAIN,
            67 to WeatherCondition.RAIN,
            80 to WeatherCondition.RAIN,
            82 to WeatherCondition.RAIN,
            99 to WeatherCondition.RAIN,
            71 to WeatherCondition.SNOW,
            77 to WeatherCondition.SNOW,
            85 to WeatherCondition.SNOW,
            73 to WeatherCondition.SNOW,
            86 to WeatherCondition.SNOW,
        )
    expected.forEach { (code, condition) ->
      val json =
          daily(
              times = """["2026-11-28"]""",
              codes = "[$code]",
              highs = "[10]",
              lows = "[5]",
              rain = "[0]",
          )
      val forecast =
          repository(json)
              .forecast(1.0, 1.0, LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 28))
      assertEquals("code $code", condition, forecast.days.single().condition)
    }
  }

  @Test
  fun keepsADayWhoseRainChanceIsMissing() = runTest {
    val json =
        """{"daily":{"time":["2026-11-28"],"weather_code":[0],"temperature_2m_max":[16],"temperature_2m_min":[10]}}"""

    val forecast = repository(json).trip()

    assertNull(forecast.days.single().rainChancePercent)
  }

  @Test
  fun skipsDaysWithAMissingOrInvalidValue() = runTest {
    val json =
        daily(
            times = """["2026-11-28","not a date","2026-11-30","2026-12-01"]""",
            codes = "[0,0,null,0]",
            highs = "[16,16,16,null]",
            lows = "[10,10,10,10]",
            rain = "[5,5,5,5]",
        )

    val forecast =
        repository(json).forecast(1.0, 1.0, LocalDate.of(2026, 11, 28), LocalDate.of(2026, 12, 1))

    assertEquals(listOf(LocalDate.of(2026, 11, 28)), forecast.days.map { it.date })
  }

  @Test
  fun onlyAsksForDaysInsideTheForecastWindowAndItsMargin() = runTest {
    var request: Request? = null

    repository(daily(), onRequest = { request = it })
        .forecast(1.0, 1.0, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 30))

    val url = requireNotNull(request).url
    // One day more than the window on each side, for a destination whose date differs.
    assertEquals("2026-11-22", url.queryParameter("start_date"))
    assertEquals("2026-12-08", url.queryParameter("end_date"))
  }

  @Test
  fun doesNotCallTheServiceForATripBeyondTheWindow() = runTest {
    var calls = 0

    val forecast =
        repository(daily(), onRequest = { calls++ })
            .forecast(1.0, 1.0, LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 27))

    assertEquals(emptyList<DailyForecast>(), forecast.days)
    assertEquals(0, calls)
  }

  @Test
  fun reportsAnErrorAnswerWithItsReason() = runTest {
    val error =
        assertThrows(WeatherUnavailableException::class.java) {
          kotlinx.coroutines.runBlocking {
            repository("""{"error":true,"reason":"Too many requests"}""", status = 429).trip()
          }
        }

    assertTrue(error.message.orEmpty().contains("Too many requests"))
  }

  @Test
  fun reportsAnAnswerItCannotRead() = runTest {
    listOf("not json", "{}", """{"daily":{}}""").forEach { body ->
      assertThrows(WeatherUnavailableException::class.java) {
        kotlinx.coroutines.runBlocking { repository(body).trip() }
      }
    }
  }

  @Test
  fun reportsAFailedConnection() = runTest {
    val client = OkHttpClient.Builder().addInterceptor { throw IOException("offline") }.build()

    val error =
        assertThrows(WeatherUnavailableException::class.java) {
          kotlinx.coroutines.runBlocking {
            OpenMeteoWeatherRepository(client = client, clock = clock).trip()
          }
        }

    assertTrue(error.cause is IOException)
  }

  @Test
  fun refusesCoordinatesThatAreNotOnEarth() = runTest {
    assertThrows(IllegalArgumentException::class.java) {
      kotlinx.coroutines.runBlocking { repository(daily()).forecast(95.0, 0.0, today, today) }
    }
    assertThrows(IllegalArgumentException::class.java) {
      kotlinx.coroutines.runBlocking { repository(daily()).forecast(0.0, Double.NaN, today, today) }
    }
  }

  @Test
  fun skipsADayWhoseWeatherCodeIsUnknown() = runTest {
    val json = daily(codes = "[4,3]")

    val forecast = repository(json).trip()

    assertEquals(listOf(LocalDate.of(2026, 11, 29)), forecast.days.map { it.date })
  }

  @Test
  fun skipsADayWhoseLowIsMissingAndKeepsTheOthers() = runTest {
    val json = daily(lows = "[null,9.0]")

    val forecast = repository(json).trip()

    assertEquals(listOf(LocalDate.of(2026, 11, 29)), forecast.days.map { it.date })
  }

  @Test
  fun keepsADayWhoseRainChanceIsNullAndClampsOneOutOfRange() = runTest {
    val forecast = repository(daily(rain = "[null,180]")).trip()

    assertNull(forecast.days[0].rainChancePercent)
    assertEquals(100, forecast.days[1].rainChancePercent)
  }

  @Test
  fun reportsAnAnswerThatLacksOneOfTheRequiredLists() = runTest {
    val full =
        mapOf(
            "time" to """["2026-11-28"]""",
            "weather_code" to "[0]",
            "temperature_2m_max" to "[16]",
            "temperature_2m_min" to "[10]",
        )
    full.keys.forEach { missing ->
      val body =
          full
              .filterKeys { it != missing }
              .entries
              .joinToString(",", prefix = """{"daily":{""", postfix = "}}") {
                "\"${it.key}\":${it.value}"
              }
      assertThrows(WeatherUnavailableException::class.java) {
        kotlinx.coroutines.runBlocking { repository(body).trip() }
      }
    }
  }

  @Test
  fun reportsAnErrorThatComesWithoutAReason() = runTest {
    listOf("<html>Bad gateway</html>", """{"error":true}""").forEach { body ->
      val error =
          assertThrows(WeatherUnavailableException::class.java) {
            kotlinx.coroutines.runBlocking { repository(body, status = 502).trip() }
          }
      assertTrue("$body -> ${error.message}", error.message.orEmpty().contains("502"))
      assertTrue("$body -> ${error.message}", error.message.orEmpty().contains("no reason given"))
    }
  }

  @Test
  fun refusesEachCoordinateThatIsNotOnEarth() = runTest {
    listOf(-91.0 to 0.0, 0.0 to 181.0, 0.0 to -181.0, Double.NaN to 0.0).forEach { (lat, lon) ->
      assertThrows(IllegalArgumentException::class.java) {
        kotlinx.coroutines.runBlocking { repository(daily()).forecast(lat, lon, today, today) }
      }
    }
  }

  @Test
  fun cutsTheAnswerToTheWindowOfTheDestinationNotOfTheDevice() = runTest {
    // At 10:00Z it is already 24 Nov on the Line Islands (UTC+14) but still 23 Nov in Lisbon.
    val lineIslands =
        """{"utc_offset_seconds":50400,"daily":{"time":["2026-11-22","2026-11-23","2026-11-24"],"weather_code":[0,0,0],"temperature_2m_max":[10,10,10],"temperature_2m_min":[5,5,5]}}"""
    val lisbon = lineIslands.replace("50400", "0")
    val trip: suspend (String) -> List<LocalDate> = { json ->
      repository(json)
          .forecast(1.0, 1.0, LocalDate.of(2026, 11, 22), LocalDate.of(2026, 11, 24))
          .days
          .map { it.date }
    }

    assertEquals(listOf(LocalDate.of(2026, 11, 24)), trip(lineIslands))
    assertEquals(
        listOf(LocalDate.of(2026, 11, 23), LocalDate.of(2026, 11, 24)),
        trip(lisbon),
    )
  }

  @Test
  fun returnsEachDayOnceAndOldestFirstAndOnlyInsideTheRequestedDates() = runTest {
    val json =
        daily(
            times = """["2026-11-29","2026-11-28","2026-11-29","2026-12-05"]""",
            codes = "[3,0,0,0]",
            highs = "[15,16,17,18]",
            lows = "[9,10,11,12]",
            rain = "[1,2,3,4]",
        )

    val forecast = repository(json).trip()

    assertEquals(
        listOf(LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 29)),
        forecast.days.map { it.date },
    )
    assertEquals(WeatherCondition.CLOUDY, forecast.days[1].condition)
  }

  @Test
  fun skipsADayWithTemperaturesThatCannotBeRight() = runTest {
    val json = daily(highs = "[1e30,5]", lows = "[9.6,9.0]")

    val forecast = repository(json).trip()

    // 1e30 is absurd, and a low above the high is a swapped pair.
    assertEquals(emptyList<DailyForecast>(), forecast.days)
  }

  @Test
  fun doesNotCallTheServiceForADatesRangeThatIsBackwardsOrInThePast() = runTest {
    var calls = 0
    val repository = repository(daily(), onRequest = { calls++ })

    val backwards =
        repository.forecast(1.0, 1.0, LocalDate.of(2026, 11, 29), LocalDate.of(2026, 11, 28))
    val past = repository.forecast(1.0, 1.0, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10))

    assertEquals(emptyList<DailyForecast>(), backwards.days)
    assertEquals(emptyList<DailyForecast>(), past.days)
    assertEquals(0, calls)
  }

  @Test
  fun asksAgainOnceWhenTheProviderFailsForAMoment() = runTest {
    var calls = 0
    val client =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
              calls++
              val ok = calls > 1
              Response.Builder()
                  .request(chain.request())
                  .protocol(Protocol.HTTP_1_1)
                  .code(if (ok) 200 else 503)
                  .message("Test response")
                  .body((if (ok) daily() else "").toResponseBody("application/json".toMediaType()))
                  .build()
            }
            .build()

    val forecast = OpenMeteoWeatherRepository(client = client, clock = clock).trip()

    assertEquals(2, calls)
    assertEquals(2, forecast.days.size)
  }

  @Test
  fun givesUpAfterOneRetryButNeverRetriesARefusal() = runTest {
    var calls = 0
    val failing = repository("", status = 503, onRequest = { calls++ })
    val error =
        assertThrows(WeatherUnavailableException::class.java) {
          kotlinx.coroutines.runBlocking { failing.trip() }
        }
    assertEquals(2, calls)
    assertTrue(error.message.orEmpty().contains("503"))

    calls = 0
    val limited =
        repository("""{"reason":"Too many requests"}""", status = 429, onRequest = { calls++ })
    assertThrows(WeatherUnavailableException::class.java) {
      kotlinx.coroutines.runBlocking { limited.trip() }
    }
    assertEquals(1, calls)
  }

  @Test
  fun sendsCoordinatesInPlainDecimalNotationEvenNearZero() = runTest {
    var request: Request? = null

    repository(daily(), onRequest = { request = it })
        .forecast(0.0001, -0.0004, LocalDate.of(2026, 11, 28), LocalDate.of(2026, 11, 29))

    assertEquals("0.000", requireNotNull(request).url.queryParameter("latitude"))
    assertEquals("0.000", requireNotNull(request).url.queryParameter("longitude"))
  }

  @Test
  fun canBeCreatedWithItsDefaultClientAndClock() {
    OpenMeteoWeatherRepository()
  }

  private fun stubClient(
      json: String?,
      status: Int,
      onRequest: (Request) -> Unit,
  ): OkHttpClient =
      OkHttpClient.Builder()
          .addInterceptor { chain ->
            onRequest(chain.request())
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("Test response")
                .apply { json?.let { body(it.toResponseBody("application/json".toMediaType())) } }
                .build()
          }
          .build()
}
