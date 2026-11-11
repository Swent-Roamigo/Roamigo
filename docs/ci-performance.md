# CI performance

How the CI workflow (`.github/workflows/ci.yml`) is arranged to give feedback quickly, what each change saved, and what was tried or left out. Timings are from GitHub-hosted `ubuntu-latest` runners and vary from run to run, so treat them as estimates.

## Where the time goes

The workflow runs three jobs, plus the `CI` job that the branch ruleset requires:

```
Checks (unit tests, lint, build) ----------------\
                                                  +--> Coverage report --> CI
Connected tests, shards 0..2 (emulator tests) ---/
```

The coverage report needs the coverage of both sides, so it runs last. The time to green is therefore the slowest connected-test shard plus the report.

## What was done

| Change | Where | Effect |
|---|---|---|
| Run Checks and Connected tests as parallel jobs | `ci.yml` | Before this, one job ran everything in sequence. |
| Share the job setup in a composite action | `.github/actions/setup-android-build` | No time saved. It removes three copies of the same setup steps. |
| Split the instrumented tests into 3 shards, one emulator each | `connected-tests` matrix, `numShards` / `shardIndex` | 556s for one job to about 300-370s per shard. Going from 2 to 3 shards took the time to green from about 480s to about 440s (3 runs each). |
| Run unit tests in several JVMs | `maxParallelForks` in `app/build.gradle.kts` | 48s to 32s for the unit tests on a 10-core machine. A 4-core runner gains less. |
| Cache the Firebase CLI (`~/.npm`) and the Firebase emulator jars | `actions/cache` steps in `connected-tests` | CLI install 18s to 8s, and the emulator jars are no longer downloaded in every shard. Only pays off from the second run. |
| Cache the emulator disk (AVD) | `actions/cache` with key `avd-34` | The emulator boots from a snapshot in about 13s instead of 60-90s. Restoring the cache costs 35-41s. |
| Reuse the Gradle build cache | `--build-cache` | 46 of 78 tasks in a shard come from the cache. |
| Cancel the older run of the same PR | `concurrency` | Pushing again does not queue behind an outdated run. |

Measured (whole workflow, from start to the green `CI` check):

| | Before | After |
|---|---|---|
| Checks | 5m27 | 2m30-2m55 |
| Slowest connected-test job | 9m16 (one job) | 5m-6m10 (3 shards) |
| Coverage report | 1m29 | 1m10-1m25 |
| Time to green | about 10.5 min | 7m05-7m31 |

Samples of the full workflow, all green:

| Setup | Runs (time to green) | Average |
|---|---|---|
| 2 shards | 471s, 496s | 484s |
| 3 shards, default emulator cores | 437s, 425s, 451s | 438s |

That is about 45s (9%) faster for the third shard, and the three shards finish closer together (295-367s) than the two did. Three runs per setup is a small sample, so read the difference as "about 30-60s", not an exact figure.

## Where a connected-test shard spends its time

For a shard of about 373s (2 shards, before the caches):

| Phase | Time | Can it be cut? |
|---|---|---|
| Android SDK and system image install | 59s | Not cheaply. The emulator action downloads the roughly 1 GB system image on every run. Caching it takes about as long to restore as to download. |
| AVD cache restore | 35-41s | No. It buys the 13s snapshot boot. A cold boot is slower. |
| Gradle configure and build | 70s | Mostly packaging, dexing and coverage instrumentation, which are not cacheable. The prebuilt APKs of the Checks job cannot be reused, because it runs at the same time and Gradle's coverage tasks need the build. |
| Firebase CLI and emulator start | 19s + 17s | Cut by caching (see above). |
| The tests themselves | about 106s | Shorter with more shards, but each shard repeats everything above. |

## Tried or left out

- **One Gradle call for `jacocoTestReport` and `sonar`.** The combined call made Gradle compile the app in the report job, and the job failed. They stay two calls.
- **Skip CI for documentation-only changes.** A skipped workflow leaves the required `CI` check pending and blocks the merge.
- **Remove `lintDebug` from the Checks command.** `check` already runs it, so Gradle runs it once.
- **A second Sonar analysis with unit-test coverage only.** Sonar keeps one analysis per PR, so it would overwrite the real one.
- **Gradle configuration cache.** It could save 10-20s, but it has not been tested with this build's Android Gradle plugin, Jacoco and Sonar setup, and may break it.
- **Starting the report job early and waiting for the artifacts.** GitHub Actions has no clean way to do this, and polling for artifacts is fragile.

## Tried and reverted

- **4 emulator cores (`cores: 4`).** Together with 3 shards it saved about 20-30s, but `TripMapScreenTest.openSettingsWithTheDefaultStarterOpensTheAppDetailsSettings` then failed with `RootViewWithoutFocusException` (no window focus within 10 s) in shard 1 on 2 of 4 runs. With 3 shards and the default 2 cores, it passed in all 3 timed runs, so the cores are the likely cause. If a flaky focus failure appears again, check that nobody raised the emulator cores.

## Measuring it again

`gh run view <run-id> --json jobs` lists each job's start and end. The step times are in `gh api repos/<owner>/<repo>/actions/runs/<run-id>/jobs`.
