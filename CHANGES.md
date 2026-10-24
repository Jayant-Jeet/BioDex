# Changes

## UI and collection

- Reworked the collection dashboard around the Figma Make export, including the rank/progress summary, collection search, sort menu, two-column lazy card list, and capture action. Mission tracking remains in the data layer; its panel is not displayed, as requested.
- Added rarity treatments and missing/long-name handling to specimen cards, with Compose previews for the principal screens and the common, uncommon, and rare card variants.
- Added stable sorting by newest, oldest, name A–Z, and name Z–A. The selected option is persisted locally; sorting and grouping run away from the main thread.
- Added PNG sharing of the visible specimen card through Android's share sheet and the existing `FileProvider`. PNG encoding and temporary-file I/O run on `Dispatchers.IO`; expired export files are cleaned on launch.

## Model download

- Replaced `DownloadManager` with unique WorkManager work using a foreground `dataSync` worker, progress/cancel notification, persisted UI state, network constraints, and retry/backoff.
- Downloads now use an app-private `.part` file and ETag/Last-Modified validators with HTTP Range/If-Range. A changed validator or unsupported range restarts safely; an interrupted transfer retains its bytes.
- Added a free-space check with a 256 MiB reserve, SHA-256 and exact-size validation, atomic installation, and a stored verified marker to avoid hashing the full model on every launch.
- The splash now presents queue, transfer progress/speed/ETA, network wait, verification, error/retry, mobile-data confirmation, and completion states. Notification denial does not block the download. Battery guidance appears on selected OEMs or after repeated worker stops.

## Dependencies and Android configuration

- Added `androidx.work:work-runtime-ktx:2.10.1` and the existing test-only JUnit 4.13.2 dependency.
- Added `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, and `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permissions.
- Declared the WorkManager foreground service as `dataSync` and registered the internal notification-cancel receiver.
- Existing DM Sans/DM Serif font resources and the app's vector path icons are reused. No runtime network dependency was added beyond the first-run model download.

## Validation

- `:app:testDebugUnitTest :app:compileDebugKotlin` passes, including local fake-HTTP-server tests for Range resume, validator change, transient retry, and checksum rejection.
- Compose previews were added for the dashboard, splash, capture/classifying screens, and specimen rarity variants. Android-device rendering, PixelCopy/share-sheet behavior, OEM background restrictions, and side-by-side Figma screenshot comparison still require the manual checks in [MANUAL-TEST-PLAN.md](./MANUAL-TEST-PLAN.md).
