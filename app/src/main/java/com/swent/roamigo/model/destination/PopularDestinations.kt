// Implemented with assistance from Claude (Anthropic).
package com.swent.roamigo.model.destination

/**
 * Well-known tourist cities offered before the user types anything, roughly from most to least
 * visited. They use GeoNames ids and coordinates, so they behave exactly like search results: the
 * same photo lookup, the same selection and the same stored trip location.
 */
object PopularDestinations {
  val all: List<Destination> =
      listOf(
          city(5128581, "New York", UNITED_STATES, "US", 40.71427, -74.00597),
          city(2643743, "London", "United Kingdom", "GB", 51.50853, -0.12574),
          city(2988507, "Paris", "France", "FR", 48.85341, 2.3488),
          city(1850147, "Tokyo", "Japan", "JP", 35.6895, 139.69171),
          city(3169070, "Rome", "Italy", "IT", 41.89193, 12.51133),
          city(3128760, "Barcelona", "Spain", "ES", 41.38879, 2.15899),
          city(745044, "Istanbul", "Türkiye", "TR", 41.01384, 28.94966),
          city(292223, "Dubai", "United Arab Emirates", "AE", 25.07725, 55.30927),
          city(1609350, "Bangkok", "Thailand", "TH", 13.75398, 100.50144),
          city(1880252, "Singapore", "Singapore", "SG", 1.28967, 103.85007),
          city(2759794, "Amsterdam", "The Netherlands", "NL", 52.37403, 4.88969),
          city(3067696, "Prague", "Czechia", "CZ", 50.08804, 14.42076),
          city(2761369, "Vienna", "Austria", "AT", 48.20849, 16.37208),
          city(2267057, "Lisbon", "Portugal", "PT", 38.72509, -9.1498),
          city(3117735, "Madrid", "Spain", "ES", 40.4165, -3.70256),
          city(2950159, "Berlin", "Germany", "DE", 52.52437, 13.41053),
          city(2147714, "Sydney", "Australia", "AU", -33.86785, 151.20732),
          city(5368361, "Los Angeles", UNITED_STATES, "US", 34.05223, -118.24368),
          city(5391959, "San Francisco", UNITED_STATES, "US", 37.77493, -122.41942),
          city(5506956, "Las Vegas", UNITED_STATES, "US", 36.17497, -115.13722),
          city(4164138, "Miami", UNITED_STATES, "US", 25.77427, -80.19366),
          city(1819729, "Hong Kong", "Hong Kong", "HK", 22.27832, 114.17469),
          city(1835848, "Seoul", "South Korea", "KR", 37.566, 126.9784),
          city(1857910, "Kyoto", "Japan", "JP", 35.02107, 135.75385),
          city(360630, "Cairo", "Egypt", "EG", 30.06263, 31.24967),
          city(2542997, "Marrakesh", "Morocco", "MA", 31.63416, -7.99994),
          city(3369157, "Cape Town", "South Africa", "ZA", -33.92584, 18.42322),
          city(3451190, "Rio de Janeiro", "Brazil", "BR", -22.90642, -43.18223),
          city(3435910, "Buenos Aires", "Argentina", "AR", -34.61315, -58.37723),
          city(3530597, "Mexico City", "Mexico", "MX", 19.42847, -99.12766),
          city(3531673, "Cancún", "Mexico", "MX", 21.17429, -86.84656),
          city(264371, "Athens", "Greece", "GR", 37.98376, 23.72784),
          city(3164603, "Venice", "Italy", "IT", 45.43713, 12.33265),
          city(3176959, "Florence", "Italy", "IT", 43.77925, 11.24626),
          city(3054643, "Budapest", "Hungary", "HU", 47.49835, 19.04045),
          city(2618425, "Copenhagen", "Denmark", "DK", 55.67594, 12.56553),
          city(2673730, "Stockholm", "Sweden", "SE", 59.32938, 18.06871),
          city(2964574, "Dublin", "Ireland", "IE", 53.33306, -6.24889),
          city(2650225, "Edinburgh", "United Kingdom", "GB", 55.95206, -3.19648),
          city(3413829, "Reykjavik", "Iceland", "IS", 64.13548, -21.89541),
          city(2657896, "Zurich", "Switzerland", "CH", 47.36667, 8.55),
          city(756135, "Warsaw", "Poland", "PL", 52.22977, 21.01178),
          city(3094802, "Krakow", "Poland", "PL", 50.06143, 19.93658),
          city(2867714, "Munich", "Germany", "DE", 48.13743, 11.57549),
          city(3173435, "Milan", "Italy", "IT", 45.46427, 9.18951),
          city(2510911, "Seville", "Spain", "ES", 37.38283, -5.97317),
          city(1581130, "Hanoi", "Vietnam", "VN", 21.0245, 105.84117),
          city(6167865, "Toronto", "Canada", "CA", 43.70643, -79.39864),
          city(3553478, "Havana", "Cuba", "CU", 23.13302, -82.38304),
          city(281184, "Jerusalem", "Israel", "IL", 31.76904, 35.21633),
      )

  private const val UNITED_STATES = "United States"

  private fun city(
      geonameId: Int,
      name: String,
      countryName: String,
      countryCode: String,
      latitude: Double,
      longitude: Double,
  ) =
      Destination(
          stableId = "geonames:$geonameId",
          kind = DestinationKind.CITY,
          name = name,
          countryName = countryName,
          countryCode = countryCode,
          latitude = latitude,
          longitude = longitude,
          geonameId = geonameId,
      )
}
