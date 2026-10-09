# Offshore Field Console

An Android inspection application with required checklists, evidence references, a Room database, and authenticated event synchronization.

## Run

Requirements: Android SDK, Kotlin, and Jetpack Compose.

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

## Behavior

The interface records operator-supplied data and retains events offline. Configure the session using an operations archive token. Delivery requires HTTPS and persistence confirmation. `ArchiveWorkerFactory` integrates WorkManager with a session provider. Device execution still requires validation.

## Result synchronization

The [operations archive](https://vercel-home-telemetry-api.vercel.app/laboratory.html?project=android-offshore-field-console) stores execution results. Supabase migrations are in the [API repository](https://github.com/brunnojob/vercel-home-telemetry-api/tree/main/supabase/migrations).

```sh
python cloud/sync.py enqueue result.json --project android-offshore-field-console
python cloud/sync.py sync
```

Set `BRUNNODEV_ACCESS_TOKEN` to your session token. The SQLite outbox retains reports until the server confirms persistence; identical content does not create duplicate records. Tokens are not stored in source code. To run the synchronization tests:

```sh
python -m unittest discover -s cloud
```
