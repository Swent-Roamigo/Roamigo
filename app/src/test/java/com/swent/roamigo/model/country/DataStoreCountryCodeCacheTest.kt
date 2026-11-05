// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.country

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreCountryCodeCacheTest {
  @get:Rule val folder = TemporaryFolder()

  private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private lateinit var file: File
  private lateinit var store: DataStore<Preferences>
  private lateinit var cache: DataStoreCountryCodeCache

  @Before
  fun setUp() {
    file = File(folder.newFolder(), "country_codes.preferences_pb")
    store = PreferenceDataStoreFactory.create(scope = scope) { file }
    cache = DataStoreCountryCodeCache(store)
  }

  @After
  fun tearDown() {
    scope.cancel()
  }

  @Test fun absentCoordinatesReturnNull() = runTest { assertNull(cache.get(46.52, 6.57)) }

  @Test
  fun savingOnePointDoesNotOverrideAnother() = runTest {
    cache.put(46.52, 6.57, "CH")
    cache.put(38.72, -9.14, "PT")

    assertEquals("CH", cache.get(46.52, 6.57))
    assertEquals("PT", cache.get(38.72, -9.14))
    assertNull(cache.get(46.52, 6.58))
  }

  @Test
  fun usesExactCoordinatesRatherThanRoundingAcrossBorders() = runTest {
    cache.put(46.00000001, 6.0, "CH")

    assertNull(cache.get(46.00000002, 6.0))
  }

  @Test
  fun rejectsMalformedCodesBeforeWriting() = runTest {
    try {
      cache.put(46.52, 6.57, "ch")
      fail("Expected an invalid country code to be rejected")
    } catch (_: IllegalArgumentException) {
      assertNull(cache.get(46.52, 6.57))
    }
  }

  @Test
  fun corruptedCachedCodeIsTreatedAsMissing() = runTest {
    val latitude = 46.52
    val longitude = 6.57
    val key = stringPreferencesKey("country_${latitude.toBits()}_${longitude.toBits()}")

    store.edit { it[key] = "ch" }

    assertNull(cache.get(latitude, longitude))
  }

  @Test
  fun valuesSurviveReopeningDataStore() = runTest {
    cache.put(46.52, 6.57, "CH")
    scope.cancel()

    val reopenScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    try {
      val reopened =
          DataStoreCountryCodeCache(PreferenceDataStoreFactory.create(scope = reopenScope) { file })

      assertEquals("CH", reopened.get(46.52, 6.57))
    } finally {
      reopenScope.cancel()
    }
  }
}
