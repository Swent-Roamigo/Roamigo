// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.weather

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
}
