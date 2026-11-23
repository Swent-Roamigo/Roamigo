// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreWeatherCacheTest {
  @get:Rule val folder = TemporaryFolder()

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private lateinit var store: DataStore<Preferences>
  private lateinit var cache: DataStoreWeatherCache

  @Before
  fun setUp() {
    store =
        PreferenceDataStoreFactory.create(scope = scope) {
          File(folder.newFolder(), "weather.preferences_pb")
        }
    cache = DataStoreWeatherCache(store)
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  private fun forecast(at: String = "2026-11-23T10:00:00Z") =
      Forecast(
          listOf(
              DailyForecast(LocalDate.of(2026, 11, 28), WeatherCondition.PARTLY_CLOUDY, 15, 9, 20),
              DailyForecast(LocalDate.of(2026, 11, 29), WeatherCondition.SNOW, -1, -6, null),
          ),
          Instant.parse(at),
      )

  @Test fun nothingSavedGivesNull() = runTest { assertNull(cache.get(38.72, -9.14)) }

  @Test
  fun givesBackWhatWasSaved() = runTest {
    cache.put(38.72, -9.14, forecast())

    assertEquals(forecast(), cache.get(38.72, -9.14))
  }

  @Test
  fun pointsAboutAKilometreApartShareAForecastButFarOnesDoNot() = runTest {
    cache.put(38.7223, -9.1393, forecast())

    assertNotNull(cache.get(38.7249, -9.1351))
    assertNull(cache.get(38.80, -9.14))
    assertNull(cache.get(41.15, -8.61))
  }

  @Test
  fun savingAgainReplacesTheOldForecast() = runTest {
    cache.put(38.72, -9.14, forecast("2026-11-22T10:00:00Z"))
    cache.put(38.72, -9.14, forecast("2026-11-23T10:00:00Z"))

    assertEquals(Instant.parse("2026-11-23T10:00:00Z"), cache.get(38.72, -9.14)?.fetchedAt)
  }

  @Test
  fun keepsOnlyTheMostRecentPlaces() = runTest {
    repeat(DataStoreWeatherCache.MAX_PLACES + 2) { index ->
      cache.put(index.toDouble(), 0.0, forecast("2026-11-%02dT10:00:00Z".format(index + 1)))
    }

    assertNull(cache.get(0.0, 0.0))
    assertNull(cache.get(1.0, 0.0))
    assertNotNull(cache.get(2.0, 0.0))
    assertNotNull(cache.get((DataStoreWeatherCache.MAX_PLACES + 1).toDouble(), 0.0))
  }

  @Test
  fun anUnreadableEntryIsTreatedAsMissing() = runTest {
    cache.put(38.72, -9.14, forecast())
    val key = store.data.first().asMap().keys.single()
    store.edit { it[stringPreferencesKey(key.name)] = "{not json" }

    assertNull(cache.get(38.72, -9.14))
  }

  private suspend fun saveRaw(latitude: Double, longitude: Double, text: String) {
    cache.put(latitude, longitude, forecast())
    val key = store.data.first().asMap().keys.first { it.name.contains("%.2f".format(latitude)) }
    store.edit { it[stringPreferencesKey(key.name)] = text }
  }

  @Test
  fun anEntryWithAnUnknownConditionIsTreatedAsMissing() = runTest {
    saveRaw(
        38.72,
        -9.14,
        """{"t":1,"d":[{"d":"2026-11-28","c":"FOG","h":15,"l":9}]}""",
    )

    assertNull(cache.get(38.72, -9.14))
  }

  @Test
  fun anEntryWithAnInvalidDateIsTreatedAsMissing() = runTest {
    saveRaw(
        38.72,
        -9.14,
        """{"t":1,"d":[{"d":"yesterday","c":"SUNNY","h":15,"l":9}]}""",
    )

    assertNull(cache.get(38.72, -9.14))
  }

  @Test
  fun anUnreadableEntryIsTheFirstToGoWhenThereAreTooManyPlaces() = runTest {
    saveRaw(60.0, 10.0, "{not json")
    repeat(DataStoreWeatherCache.MAX_PLACES) { index ->
      cache.put(index.toDouble(), 0.0, forecast("2026-11-%02dT10:00:00Z".format(index + 1)))
    }

    assertNotNull(cache.get(0.0, 0.0))
    assertNotNull(cache.get((DataStoreWeatherCache.MAX_PLACES - 1).toDouble(), 0.0))
    assertEquals(DataStoreWeatherCache.MAX_PLACES, store.data.first().asMap().size)
  }

  @Test
  fun keepsAForecastInTheAppsOwnStoreWhenCreatedFromAContext() = runTest {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val appCache = DataStoreWeatherCache(context)

    appCache.put(12.34, 56.78, forecast())

    assertEquals(forecast(), appCache.get(12.34, 56.78))
  }
}
