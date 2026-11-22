# FieldProof — interview talking points

Use this as a viva / technical-interview script. Speak in this order.

## 1. Problem

People collect evidence where the network is unreliable. A naïve CRUD app loses unsynced data, overwrites conflicts, and cannot tell whether a photo changed. FieldProof treats the **device database as the product**, and the server as a replica with conflict history.

## 2. Offline-first

“The UI observes Room. Capture is a local transaction: metadata + file + hash. Sync is a background reconciliation job. If sync fails, the record stays pending. We never treat the server as required for the primary user journey.”

Contrast with cache-and-fallback: that still has the server as source of truth.

## 3. Why Room

SQLite on disk survives process death. Compose state and ViewModels do not. Room gives transactions (observation + evidence + version row commit together), `Flow` for UI, and compile-time SQL.

## 4. Why WorkManager

Sync is deferrable and must outlive the Activity. A coroutine in a ViewModel dies with the process. WorkManager accepts `NetworkType.CONNECTED`, unique work names, and retry/backoff. It does **not** run instantly; that is acceptable for upload, not for capture.

## 5. SHA-256

`MessageDigest.getInstance("SHA-256")` streamed in 64 KiB chunks. Compare with `MessageDigest.isEqual`.

A match means: these bytes equal the bytes we hashed when we stored this digest.

A match does **not** mean: authentic photo, true GPS, true time, or an untampered database (an attacker who changes file **and** digest still verifies).

Keystore ECDSA over the digest raises the bar for SQLite tampering on that device.

## 6. Sync protocol

- Client-generated UUID as identity (idempotent create).
- `localRevision` increments on every local edit.
- `lastSyncedRevision` is the server revision we last reconciled.
- Clocks are never the conflict key.

HTTP 409 returns the server snapshot. The client writes it into `observation_versions` with `source = SERVER` and sets `syncStatus = CONFLICT`. The local row is untouched.

## 7. Permissions

Camera and location are requested at the moment of use, not at launch. Location is optional. Missing permission ⇒ observation still saves, GPS fields stay null.

## 8. Failure modes you tested mentally

| Event | Result |
|---|---|
| Kill app after Save | Room + file remain |
| No network | Status PENDING_*, worker waits |
| 5xx / timeout | `Result.retry()`, local data kept |
| 409 | Conflict row + history, no overwrite |
| File deleted on disk | Verify → MISSING_FILE |

## 9. What you would add next

Keystore-signed canonical payload (notes + GPS + file digest), HTTPS + real auth, blob store (S3), and a proper migration instead of a single schema version.
