# Swift Field Inspections

An offline inspection store for Apple and Linux clients. A Swift actor serializes revisions and maintains a durable JSON outbox. An exclusive file lock prevents two stores from writing the same file. Tokens are passed to the HTTPS transport and are never stored in the inspection file.

## Verify

Requirements: Swift 6 and Foundation.

```sh
swiftc -swift-version 6 -parse-as-library Sources/*.swift Tests/StoreChecks.swift -o inspection-checks
./inspection-checks
```

Use `save` with the current revision to revise an inspection. Use `flush` with an authenticated `HTTPArchiveTransport` targeting the operations API at `/api/runs`. A successful HTTP response only removes an item when its receipt confirms the same client key. Failed delivery persists its retry count and next attempt; retries are bounded to one hour.

Native apps run on their devices. The operations API and web interface can run on Netlify with Supabase persistence.

Maintained by [Brunno Dev](https://brunnodev.store).
