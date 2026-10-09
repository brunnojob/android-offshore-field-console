# Offshore Field Console

An Android inspection application with required checklists, evidence references, a Room database, and authenticated event synchronization.

## Run

Requirements: Android SDK, Kotlin, and Jetpack Compose.

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

## Behavior

The interface records operator-supplied data and retains events offline. Configure the session using an operations archive token. Delivery requires HTTPS and persistence confirmation. `ArchiveWorkerFactory` integrates WorkManager with a session provider. Device execution still requires validation.

## Optional report archive

Use the [shared operations archive client](https://github.com/brunnojob/vercel-home-telemetry-api/tree/main/cloud) to queue `result.json` under project `android-offshore-field-console`. The client uses `BRUNNODEV_ACCESS_TOKEN` and retains unacknowledged reports locally.

## License

Original source and documentation are MIT licensed; see [LICENSE](LICENSE). Third-party dependencies and media retain their respective terms. Maintained by [Brunno Dev](https://brunnodev.store).

## Implementation update

The [native Swift inspection store](ios/README.md) adds an actor-isolated file store, optimistic revisions, a durable delivery queue, bounded retries and exact receipt matching. The Android application remains implemented in Kotlin. Swift checks run in the macOS workflow; neither implementation is hosted as a mobile runtime on Netlify.

Contribution trailer: `Co-authored-by: nyctophile <33561761+ineedfoundmyway@users.noreply.github.com>`.
