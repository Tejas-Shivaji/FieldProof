# FieldProof

Offline-first, tamper-evident field evidence recorder.

Android (Kotlin, Jetpack Compose, Room, WorkManager) + FastAPI + PostgreSQL.

This is a complete student/professional MVP: capture observations without a network, persist them on device, hash evidence files, sync when connectivity returns, and **never silently overwrite** a conflicting version.

## What the product does

1. Create field observations with no internet.
2. Attach a photograph (camera or picker).
3. Optionally store **device-reported** GPS and timestamps.
4. Persist in Room (SQLite). Records survive process death and reboot.
5. Compute SHA-256 over the evidence file bytes.
6. Re-verify that the file still matches the recorded digest.
7. Queue pending uploads as Room rows, not an in-memory list.
8. Synchronize with WorkManager when the network is available.
9. Detect sync conflicts using revision numbers, not clocks.
10. Preserve both local and server versions.
11. Keep an append-only evidence history.
12. Export a verification report.

## Honesty constraints (say these in an interview)

- **SHA-256** proves only that current bytes match the digest recorded at capture (or last hash). It does not prove authorship, that the original photo was “real”, or that GPS/time are true.
- **GPS and timestamps** are reported by the device. They can be wrong, stale, or mocked.
- **Android Keystore signatures** make it harder to forge the stored digest without the device key. They still do not certify the real world.
- **Conflicts** are data. Last-write-wins is forbidden for evidence.

## Repository layout

```
FieldProof/
  app/                  Android application
  backend/              FastAPI service
  docker-compose.yml    PostgreSQL + API
  README.md
  INTERVIEW.md
```

## Architecture

```
Compose UI  →  ViewModels  →  domain models / repository interfaces
                                      ↓
                    Room (source of truth) + private files
                                      ↓
                    WorkManager SyncWorker (network constraint)
                                      ↓
                    FastAPI  →  PostgreSQL  +  uploaded blobs
```

Layers:

| Layer | Package | Responsibility |
|---|---|---|
| UI | `ui/` | Compose screens, navigation, permissions |
| Domain | `domain/` | Models, repository interfaces, no Android types except `File` on the create input |
| Data | `data/` | Room, files, SHA-256, Keystore, Retrofit, WorkManager, conflict merger |

**Reads** always come from Room `Flow`s. **Writes** commit locally first, then enqueue sync. The UI never blocks capture on HTTP.

### Sync and conflict rule

```
base = lastSyncedRevision ?: 0
localDirty     = localRevision > base
remoteChanged  = remote.revision > base
```

| localDirty | remoteChanged | Action |
|---|---|---|
| no | no | In sync |
| yes | no | Push local |
| no | yes | Fast-forward to remote |
| yes | yes | **CONFLICT** — keep local row, store server snapshot in `observation_versions` |

Client IDs are UUIDs generated on device, so create is idempotent.

## Run the backend

### Option A — Docker (PostgreSQL)

```bash
docker compose up --build
```

API: http://127.0.0.1:8000  
Docs: http://127.0.0.1:8000/docs  
Health: http://127.0.0.1:8000/health

### Option B — local Python (SQLite, no Docker)

```bash
cd backend
python -m venv .venv
# Windows: .venv\Scripts\activate
# macOS/Linux: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

Tests:

```bash
cd backend
pytest -q
```

## Run the Android app

1. Open the `FieldProof` folder in Android Studio (Ladybug / Koala or newer, AGP 8.7).
2. Let Gradle sync. JDK 17 is required.
3. Create `local.properties` if Studio does not:

   ```
   sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
   ```

4. Run the `app` configuration on an emulator (API 26+) or a device with USB debugging.

Emulator API base URL (default): `http://10.0.2.2:8000`  
Physical device: set Settings → API base URL to `http://<your-lan-ip>:8000`.

The app is fully usable **offline**. Sync is opportunistic.

## How to demo the 13 features

1. Turn on airplane mode.
2. Capture an observation + photo. Optionally include GPS.
3. Force-stop the app, reopen it — the row is still there (Room).
4. Open the observation → Recompute SHA-256 → **MATCH**.
5. Airplane mode off. Tap the cloud icon. WorkManager uploads when connected.
6. To demo a conflict: create a record, sync, then change notes on the device **and** `PUT`/`POST` a different note for the same UUID against the API (`last_synced_revision` older than server). Next sync marks **CONFLICT** and both versions appear in history.
7. Export the verification report and share it.

## Security notes

- `android:allowBackup="false"` — evidence is not thrown into cloud backup.
- Evidence lives under `filesDir/evidence/`, app-private storage.
- OkHttp logging is **BASIC** so bodies (photos, GPS) are not printed.
- Network cleartext is allowed for local development only. Use HTTPS in any real deployment.

## Tests worth knowing

| Test | What it proves |
|---|---|
| `Sha256HasherTest` | Known SHA-256 vectors; avalanche on one flipped bit |
| `ConflictMergerTest` | Dirty+changed ⇒ conflict, never a silent winner |
| `backend/tests/test_api.py` | Idempotent create, 409 preserves server row, hash mismatch rejected |

Android unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

## Tech versions

- Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.12.01
- Room 2.6.1, WorkManager 2.10.0, Retrofit 2.11.0
- minSdk 26, targetSdk 35
- FastAPI 0.115, SQLAlchemy 2, PostgreSQL 16

## What was deliberately postponed

Multi-user OAuth, E2E encryption, hash chains / Merkle trees, trusted timestamp authorities, video/audio, automatic semantic merge, Play Integrity as “authenticity”.
