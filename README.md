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
