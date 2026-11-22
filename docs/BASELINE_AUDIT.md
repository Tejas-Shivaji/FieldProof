# FieldProof — Phase 0 Baseline Audit

**Date:** 2026-11-23  
**Auditor role:** Senior Android engineer (read-only validation)  
**Scope:** Existing codebase only. No application source changes.  
**Only new artifact from this phase:** this document.

---

## 1. Project structure

```
FieldProof/
├── app/                          Android application module
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/fieldproof/app/
│       │   │   ├── FieldProofApp.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── di/AppContainer.kt
│       │   │   ├── domain/model|repository
│       │   │   ├── data/local|integrity|location|remote|repo|settings|sync
│       │   │   └── ui/… (Compose screens, theme, ViewModels, nav)
│       │   └── res/
│       └── test/…                JVM unit tests (2 classes)
├── backend/                      FastAPI service
│   ├── app/                      main, models, schemas, database, hashing
│   ├── tests/test_api.py
│   ├── Dockerfile
│   └── requirements.txt
├── gradle/                       Version catalog + wrapper
├── docker-compose.yml            PostgreSQL 16 + API
├── README.md
├── INTERVIEW.md
├── local.properties.example
├── dist/FieldProof-debug.apk     Prebuilt debug APK (local artifact)
└── docs/BASELINE_AUDIT.md        This report
```

**Architecture shape (as implemented):**

| Layer | Location | Notes |
|---|---|---|
| UI | `ui/` | Compose + ViewModels; navigation in `FieldProofNav` |
| Domain | `domain/` | Models + `ObservationRepository` / `SettingsRepository` interfaces |
| Data | `data/` | Room, files, SHA-256, Keystore, Retrofit, WorkManager, conflict merger |
| DI | `di/AppContainer.kt` | Manual container (no Hilt) |
| Backend | `backend/app` | FastAPI + SQLAlchemy; SQLite by default, Postgres via compose |

Package root: `com.fieldproof.app`  
Single Gradle module: `:app`

---

## 2. Technology versions

| Component | Version / value | Source |
|---|---|---|
| Host OS | Windows 11 (10.0.26300) | Runtime |
| Java (build host) | Oracle JDK **21.0.9** | `java -version` |
| Gradle wrapper | **8.9** | `gradle/wrapper/gradle-wrapper.properties` |
| Gradle Kotlin (runtime) | 1.9.23 (Gradle’s embedded) | `./gradlew --version` |
| Project Kotlin | **2.0.21** | `gradle/libs.versions.toml` |
| KSP | 2.0.21-1.0.28 | same |
| Android Gradle Plugin | **8.7.3** | same |
| compileSdk | **35** | `app/build.gradle.kts` |
| minSdk | **26** | same |
| targetSdk | **35** | same |
| Compose BOM | 2024.12.01 | catalog |
| Room | 2.6.1 | catalog |
| WorkManager | 2.10.0 | catalog |
| Retrofit / OkHttp | 2.11.0 / 4.12.0 | catalog |
| Android SDK root | `C:\Users\Admin\AppData\Local\Android\Sdk` | `local.properties` + preflight |
| Installed platforms | android-35, android-37.0 | SDK tree |
| Build-tools | 34.0.0, 36.0.0 | SDK tree |
| FastAPI stack | 0.115.6 / SQLAlchemy 2.0.36 / Pydantic 2.10.4 | `backend/requirements.txt` |
| Python used for tests | **3.12.10** | host |
| Docker | **Not installed / not on PATH** | `docker` command missing |

### Connected devices / emulators

| Target | State |
|---|---|
| USB device `003317661004207` | **unauthorized** (cannot install/run) |
| AVDs | **0** configured |
| sdkmanager / avdmanager | **not found** in plugin PATH |
| Emulator binary | Present; acceleration (WHPX) OK |
| Instrumentation / UI run | **Blocked** in this environment |

---

## 3. Build results (Android)

Commands run from project root with `JAVA_HOME=C:\Program Files\Java\jdk-21`:

| Command | Result | Notes |
|---|---|---|
| `gradlew.bat tasks` | **SUCCESS** (~3s) | Task graph healthy |
| `gradlew.bat :app:assembleDebug` | **SUCCESS** (~3s, UP-TO-DATE) | APK produced previously and still present |
| `gradlew.bat :app:testDebugUnitTest` | **SUCCESS** (~2s, UP-TO-DATE) | 10 tests, 0 failures |

**APK artifact:**  
`app/build/outputs/apk/debug/app-debug.apk` (also copied historically to `dist/FieldProof-debug.apk`)

**Not run (environment blockers):**

- `connectedDebugAndroidTest` — no authorized device, no AVD, no `androidTest` sources found
- Emulator launch via plugin — no AVD; physical device unauthorized
- `lintDebug` — not required for baseline; not executed this pass

### Unit test detail (from `app/build/test-results/testDebugUnitTest/`)

| Suite | Tests | Failures | Errors |
|---|---:|---:|---:|
| `Sha256HasherTest` | 4 | 0 | 0 |
| `ConflictMergerTest` | 6 | 0 | 0 |
| **Total** | **10** | **0** | **0** |

Covered: known SHA-256 vectors, avalanche, constant-time hex compare; conflict matrix (push / insert / fast-forward / conflict / in-sync).

**Not covered by automated tests:** Room transactions, process-death survival, WorkManager scheduling, camera/FileProvider flow, Retrofit/SyncManager end-to-end, Keystore on device, Compose UI.

---

## 4. Backend test results

### What was run

```text
Python 3.12.10
pip install -r backend/requirements.txt
python -m pytest backend/tests -v
```

| Result | Detail |
|---|---|
| **6 passed** | ~1.09s |
| Database under test | **In-memory SQLite** (`sqlite://` + `StaticPool` in `tests/test_api.py`) |
| PostgreSQL | **Not exercised** |
| Docker Compose | **Unavailable** (`docker` not found) |

### Individual tests

| Test | Status | What it proves |
|---|---|---|
| `test_health` | PASSED | `/health` → `ok` |
| `test_create_is_idempotent` | PASSED | Same UUID create does not bump revision |
| `test_conflict_preserves_server_row` | PASSED | HTTP 409; server notes unchanged |
| `test_fast_forward_when_base_matches` | PASSED | `last_synced_revision == server.revision` allows update |
| `test_pull_returns_created_row` | PASSED | Pull since 0 returns created observation |
| `test_evidence_hash_mismatch_rejected` | PASSED | Wrong SHA-256 → HTTP 400 |

### Warnings (non-fatal)

- FastAPI `@app.on_event("startup")` is deprecated in favor of lifespan handlers
- pytest-asyncio config noise from global environment (not project-owned)

### Distinction (important)

| Claim | Status |
|---|---|
| API logic works against SQLAlchemy + SQLite test harness | **Verified** |
| API works against PostgreSQL 16 via Docker | **Not verified** (Docker missing) |
| Live multipart evidence round-trip with Android client | **Not verified** |

Default non-Docker run uses SQLite file (`DATABASE_URL` default `sqlite:///./fieldproof.db` in `database.py`). Compose wires `postgresql+psycopg2://…` when Docker is available.

---

## 5. Existing feature inventory

Classification key:

- **IMPLEMENTED AND VERIFIED** — code path exists + automated test or successful build artifact proves the unit
- **IMPLEMENTED BUT NOT VERIFIED** — full code path exists; no automated/device proof in this audit
- **PARTIALLY IMPLEMENTED** — important gaps, stubs, or incomplete wiring
- **NOT IMPLEMENTED** — missing
- **BROKEN** — code present but known incorrect/failing (none identified as hard-broken at compile time)

| # | Feature | Classification | Evidence / gap |
|---|---|---|---|
| 1 | Observation creation | **IMPLEMENTED BUT NOT VERIFIED** | `ObservationRepositoryImpl.create` + `CaptureScreen` + `CaptureViewModel`; compiles; no instrumented/UI test |
| 2 | Offline persistence (Room) | **IMPLEMENTED BUT NOT VERIFIED** | Room DB `fieldproof.db`, entities/DAOs, transaction on create; no process-kill / Room instrumented test |
| 3 | Photograph capture | **IMPLEMENTED BUT NOT VERIFIED** | Camera (`TakePicture` + FileProvider) and gallery (`GetContent`); requires runtime permission; no device run |
| 4 | App-private evidence storage | **IMPLEMENTED BUT NOT VERIFIED** | `EvidenceFileStore` → `filesDir/evidence/`; FileProvider paths for cache/capture only |
| 5 | SHA-256 calculation | **IMPLEMENTED AND VERIFIED** | `Sha256Hasher` + 4 unit tests (NIST-known digests) |
| 6 | Evidence integrity verification | **IMPLEMENTED BUT NOT VERIFIED** | `verify()` re-hashes file, `MATCH/MISMATCH/MISSING`; UI button on detail; no automated verify test on real file |
| 7 | Optional GPS capture | **IMPLEMENTED BUT NOT VERIFIED** | `DeviceLocationReader` last-known + permission gate; device-reported disclaimer present |
| 8 | Device timestamps | **IMPLEMENTED BUT NOT VERIFIED** | `System.currentTimeMillis()` on capture; shown as device clock in UI/report |
| 9 | Pending sync queue | **IMPLEMENTED BUT NOT VERIFIED** | Status enum + `dao.pending()`; queue is Room rows, not a separate queue table |
| 10 | WorkManager execution | **IMPLEMENTED BUT NOT VERIFIED** | `SyncScheduler` unique work + `NetworkType.CONNECTED` + `SyncWorker`; no WorkManager test driver run |
| 11 | Conflict detection | **IMPLEMENTED AND VERIFIED** (logic) | `ConflictMerger` 6 unit tests; backend 409 test |
| 12 | Conflict preservation | **IMPLEMENTED BUT NOT VERIFIED** (end-to-end) | `applyRemote(…, asConflict=true)` keeps local + inserts SERVER version; backend preserves server row; no full client E2E |
| 13 | Evidence / version history | **IMPLEMENTED BUT NOT VERIFIED** | `observation_versions` table + detail UI list + report section |
| 14 | Backend integration | **PARTIALLY IMPLEMENTED / PARTIALLY VERIFIED** | Retrofit API + SyncManager + FastAPI exist; backend unit tests pass on SQLite; **no live Android↔API sync verified**; Postgres not verified; cleartext allowed for local HTTP |
| 15 | Verification report export | **IMPLEMENTED BUT NOT VERIFIED** | Text report + share intent; written under `filesDir/reports/` |
| 16 | Keystore digest signing | **IMPLEMENTED BUT NOT VERIFIED** | `KeystoreSigner` ECDSA; soft-fail if keystore unavailable (`runCatching`); no instrumented test |
| 17 | Settings (API base URL) | **IMPLEMENTED BUT NOT VERIFIED** | DataStore; default `http://10.0.2.2:8000` |
| 18 | Instrumentation / UI tests | **NOT IMPLEMENTED** | No `androidTest` sources |
| 19 | Auth / multi-user | **NOT IMPLEMENTED** | Documented as postponed |
| 20 | Room schema export / migrations | **PARTIALLY IMPLEMENTED** | `exportSchema = false`, single version 1, no Migration objects |

---

## 6. Architecture assessment

### Strengths

1. **Clear offline-first intent:** create path writes file → hash → Room transaction → enqueue WorkManager; UI reads Room `Flow`s.
2. **Layering is real, not decorative:** domain models are separate from Room entities; repository interface sits above data.
3. **Conflict policy is correct for evidence:** revision-based, not clock-based; conflict keeps both sides; unit tests encode “never pick a silent winner.”
4. **Integrity honesty:** disclaimers in UI strings and report text; SHA-256 framed as relative integrity.
5. **Security hygiene (baseline):** `allowBackup=false`, data extraction exclusions, BASIC OkHttp logging, app-private evidence dir.
6. **Backend matches client contract:** push/pull/evidence endpoints, 409 conflict payload, idempotent create.

### Weaknesses / smells (not blockers for baseline)

1. **Manual DI only** — fine for MVP; harder to test repository with fakes without constructor injection at call sites already present (container is OK).
2. **`ObservationRepositoryImpl` is large** (~416 LOC) and doubles as sync mutation API (`applyRemote`, `markSynced`) used by `SyncManager` — coupling is intentional but tight.
3. **No WorkManager backoff customization** beyond defaults; unique work `KEEP` may delay immediate re-sync after failure until constraints allow.
4. **Cleartext traffic globally enabled** for student local API — must not ship production that way.
5. **Deprecated FastAPI startup hook.**
6. **Domain `CreateObservationInput` depends on `java.io.File`** — minor purity leak.
7. **No Hilt/Koin, no multi-module** — acceptable for this size.
8. **Git repository not initialized** in this workspace — blocks GitHub upload until `git init` + remote (out of Phase 0 code scope).

### Data flow (as coded)

```
Capture UI
  → CaptureViewModel.save
  → ObservationRepositoryImpl.create
      → EvidenceFileStore.persist
      → Sha256Hasher.hash
      → KeystoreSigner.signDigest (best-effort)
      → Room transaction (observation + evidence + version)
      → SyncScheduler.enqueue
  → List/Detail observe Room Flows

SyncWorker
  → SyncManager.synchronize
      → health → push pending → upload evidence → pull since cursor
      → ConflictMerger.decide → applyRemote / markSynced / CONFLICT
```

---

## 7. Known technical risks

| Risk | Severity | Notes |
|---|---|---|
| No device/emulator verification | **High** | Camera, location, Keystore, WorkManager, FileProvider unproven on hardware |
| No PostgreSQL integration test | **Medium** | SQLite tests ≠ Postgres (types, concurrency, psycopg2) |
| Unauthorized USB device | **High (env)** | Blocks install until user accepts RSA fingerprint |
| No AVDs + missing sdkmanager in plugin path | **High (env)** | Emulator workflow incomplete |
| Docker missing | **Medium (env)** | Cannot validate compose stack here |
| Sync marks SYNCED even if evidence file missing locally | **Medium** | If file gone after metadata push success path, worker may still `markSynced` when upload skipped because `!file.exists()` |
| Global cleartext | **Medium** | Acceptable for lab; dangerous if mis-deployed |
| Keystore failure swallowed | **Low** | Signature may be null; verify still works on hash alone |
| Room `exportSchema = false` | **Low** | Migrations harder later |
| `local.properties` present on disk | **Low** | Correctly gitignored; ensure never committed |
| Prebuilt APK under `dist/` | **Low** | `*.apk` gitignored; good |
| No automated CI config | **Low** | No GitHub Actions yet |
| Backend CORS `allow_origins=["*"]` | **Low** | Fine for local MVP |

---

## 8. Missing functionality (relative to product brief / production readiness)

Already in code as MVP gaps or postponed:

- Multi-user auth / OAuth
- HTTPS-only production network config
- End-to-end encryption of blobs
- Instrumentation tests (Room, WorkManager, UI)
- PostgreSQL CI job
- Schema export + Migration test
- Trusted time / external TSA
- Video/audio evidence
- Automatic semantic conflict merge (correctly deferred)
- Play Integrity / “authenticity” claims (correctly absent)
- Git remote / GitHub Actions / release signing

Runtime gaps vs “verified working product”:

- Live sync Android ↔ running API
- Conflict UI exercised with real 409
- Camera on physical device
- Process death persistence demo automated

---

## 9. Recommended implementation order (after Phase 0 approval)

Do **not** start these until this audit is accepted.

1. **Git + GitHub hygiene** — `git init`, ensure `.gitignore`, first commit, create empty GitHub repo, push (no secrets, no `local.properties`, no APKs).
2. **Environment unblock** — authorize USB device **or** create an AVD; confirm `adb devices` = `device`.
3. **Manual smoke script** — offline capture → kill app → reopen → verify hash → start backend (venv SQLite) → sync → confirm SYNCED.
4. **Instrumented tests** — Room create/read; optional WorkManager test with `SynchronousExecutor`.
5. **Fix sync edge cases** if smoke finds them (e.g. evidence-missing still SYNCED).
6. **Docker/Postgres verification** when Docker is available.
7. **CI** — GitHub Actions: `./gradlew test` + `pytest`.
8. **Only then** feature work (multi-photo, better conflict UX, auth, etc.).

---

## 10. Exact commands to run the application

### Prerequisites

- JDK 17+ (this machine used JDK 21 successfully with AGP 8.7.3)
- Android SDK with platform 35
- Python 3.12+ for backend
- (Optional) Docker Desktop for Postgres stack
- Emulator AVD **or** authorized physical device

### Backend — SQLite (no Docker)

```bat
cd FieldProof\backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Health check: http://127.0.0.1:8000/health  
OpenAPI: http://127.0.0.1:8000/docs

### Backend — PostgreSQL (when Docker exists)

```bat
cd FieldProof
docker compose up --build
```

### Backend tests

```bat
cd FieldProof\backend
python -m venv .venv
.venv\Scripts\activate
pip install -r requirements.txt
python -m pytest tests -v
```

### Android

```bat
cd FieldProof
REM Ensure local.properties contains sdk.dir=...
set JAVA_HOME=C:\Program Files\Java\jdk-21
gradlew.bat :app:assembleDebug
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:installDebug
```

Or open the `FieldProof` folder in Android Studio → Run `app`.

**API URL**

| Target | Settings → API base URL |
|---|---|
| Emulator | `http://10.0.2.2:8000` (default) |
| Physical device | `http://<PC-LAN-IP>:8000` |

### Manual offline-first smoke (recommended next human step)

1. Airplane mode ON  
2. Capture observation + photo  
3. Force-stop FieldProof  
4. Relaunch → row still listed  
5. Detail → Recompute SHA-256 → expect MATCH  
6. Airplane mode OFF; ensure backend running  
7. Tap cloud sync → expect SYNCED when network OK  

---

## 11. GitHub upload readiness (observational only)

| Item | Status |
|---|---|
| `.git` present | **No** — not a git repository yet |
| `.gitignore` | Present (build, local.properties, venv, apk, db) |
| Secrets in tree | `local.properties` on disk (gitignored); no API keys found in source |
| README | Present and usable |
| Recommended first push contents | Source + gradle wrapper + backend + docs; **exclude** `local.properties`, build dirs, venvs, APKs |

Phase 0 does **not** initialize git or push. That should be the first approved task after this audit if the goal is GitHub.

Suggested ignore additions before first commit (for a later PR, not done now):

- `backend/.venv-audit/` (created during this audit)
- `dist/` (optional; APKs already covered by `*.apk`)
- `.pytest_cache/`

---

## 12. Baseline verdict

| Gate | Result |
|---|---|
| Android project configures and lists tasks | **PASS** |
| `:app:assembleDebug` | **PASS** |
| `:app:testDebugUnitTest` (10 tests) | **PASS** |
| Backend pytest on SQLite (6 tests) | **PASS** |
| PostgreSQL / Docker | **NOT RUN** (tooling missing) |
| Device / emulator app launch | **NOT RUN** (unauthorized device, 0 AVDs) |
| End-to-end offline + sync demo | **NOT RUN** |
| Safe to treat as “compiles and core pure logic is tested” | **YES** |
| Safe to treat as “field-validated product” | **NO** — needs device smoke + optional Postgres |

**Overall Phase 0 status:** **BASELINE ESTABLISHED.**  
The repository is a coherent offline-first MVP that **builds and unit-tests cleanly**. Runtime and integration confidence remain limited by environment blockers, not by a failed compile.

---

## 13. Stop point

No application source was modified for this audit.  
Next actions require explicit approval, especially:

1. Initialize git and publish to GitHub  
2. Unblock device/emulator and run smoke  
3. Any feature or fix work  

**Recommended next task:** GitHub publication prep (`git init`, verify ignore rules, initial commit, create remote, push) **or** device authorization + manual smoke — user priority: **upload to GitHub**.
