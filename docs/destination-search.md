<!-- Author : Constantin Dunoyer de Segonzac -->
<!-- Co-author : ChatGPT-Codex -->
# Online destination search

The destination picker queries GeoNames when the user enters at least two characters. It makes one city search, waits 300 ms after typing stops, and displays up to 20 results. Search and photo metadata remain in memory; the app does not bundle a destination catalog or persist provider data.

Set a registered GeoNames username before trying live search:

```properties
# ~/.gradle/gradle.properties (local only)
GEONAMES_USERNAME=your_geonames_username
```

The same value can be provided through the `GEONAMES_USERNAME` environment variable. It is embedded in the app as a public service identifier, not a secret. An empty value shows a recoverable configuration error in the destination screen.

Photos are requested only for composed/visible results. The app first looks for a Wikidata image linked by the exact GeoNames ID and checks Commons metadata. It accepts public domain/CC0 and supported CC BY and CC BY-SA variants. If that lookup has no permitted photo or fails, it searches Openverse with the destination and country. Photos without required creator/source/license metadata are skipped. A small info button on a loaded photo opens its title, author, supplied credit, license, crop notice, and links to the source and license. Coil caches image bytes in a bounded 50 MiB app cache; provider metadata is re-fetched in a later process.

There is no offline catalog or offline destination search in this version. Search requires network access and a valid GeoNames account name. Photo lookup is best-effort, so a placeholder is expected when a source is unavailable or no eligible image is found.

Openverse photos use the direct media URL rather than its thumbnail proxy, which can time out even when the original image is accessible. Coil decodes the image for the card size and caches it; original files can use more bandwidth than thumbnails. Credits are shown only after the image has loaded successfully.

Openverse indexes license data but does not verify each source's rights or attribution. Check the source page before selecting photos for any curated or bundled collection.

Run the destination tests with:

```sh
bash ./gradlew :app:testDebugUnitTest --tests 'com.swent.roamigo.ui.trip.creation.DestinationSearchViewModelTest' --tests 'com.swent.roamigo.ui.trip.creation.DestinationScreenTest'
```
