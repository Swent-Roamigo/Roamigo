<!-- Author : Constantin Dunoyer de Segonzac -->
<!-- Co-author : ChatGPT-Codex -->
# Destination search testing procedure

Destination search tests use `FakeDestinationRepository`; they must not call GeoNames, Wikidata, Commons, Openverse, or download image bytes. Keep search responses, failures, and photo metadata in memory. Use an invalid or local image URL if a UI test needs to exercise image failure.

Run the focused tests with:

```sh
bash ./gradlew :app:testDebugUnitTest --tests 'com.swent.roamigo.ui.trip.creation.DestinationSearchViewModelTest' --tests 'com.swent.roamigo.ui.trip.creation.DestinationScreenTest'
```

The ViewModel checks cover trimmed short-query suppression, the 300 ms debounce, stale result rejection, the 20-result display limit, empty/error/retry states, and stable ordered selection. Screen checks cover loading and recovery feedback, stable-ID card/remove semantics, visible-card photo requests, and accessible attribution when a photo is available.
