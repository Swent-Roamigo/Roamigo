// AI assistance was used for implementation support and code review.
package com.swent.roamigo.model.country

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.swent.roamigo.model.validCountryCodeOrNull
import kotlinx.coroutines.flow.first

/** Persistent lookup cache. Keys use exact coordinate bits to avoid mixing border locations. */
class DataStoreCountryCodeCache(
    private val store: DataStore<Preferences>,
) : CountryCodeCache {

  constructor(context: Context) : this(context.applicationContext.countryCodeDataStore)

  override suspend fun get(latitude: Double, longitude: Double): String? =
      store.data.first()[key(latitude, longitude)].validCountryCodeOrNull()

  override suspend fun put(latitude: Double, longitude: Double, countryCode: String) {
    require(countryCode.validCountryCodeOrNull() != null) { "Invalid country code" }

    store.edit { it[key(latitude, longitude)] = countryCode }
  }

  private fun key(latitude: Double, longitude: Double) =
      stringPreferencesKey("country_${latitude.toBits()}_${longitude.toBits()}")
}

// The same singleton DataStore file is reused by every instance created with a Context.
private val Context.countryCodeDataStore by preferencesDataStore(name = "country_codes")
