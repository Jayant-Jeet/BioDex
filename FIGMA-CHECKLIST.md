# Figma Make comparison checklist

Source of truth: `design/UI.make` and its embedded Figma Make source archive; the extracted copy under `design/figma-make/` may be stale. The export contains splash, collection/dashboard, camera, and specimen-detail frames. The supplied export does not contain separate result or settings frames.

| Figma frame/state | App mapping | Result / deviation |
|---|---|---|
| Splash / brand | `SplashScreen` | Existing BioDex splash branding is retained. Queue, download progress, network wait, verifying, retry/error, mobile-data consent, battery guidance, and done states extend this screen because the export does not define model-download states. |
| Collection dashboard | `Dashboard` | Figma-style rank/progress, collection summary, search, two-column lazy card list, and capture action are implemented. The Explorer card uses the Figma top accent, layered radial/linear lighting, rank orbit, divider, next-rank label, and XP progress marker; player rank and XP remain live values. The concentric-ring decoration removed by the latest Figma revision is not drawn. `ExplorerCardPreview` uses the Figma sample state (level 03, 620/1,000 XP). The mission panel is omitted from this screen per user confirmation; mission and XP logic remain in the app. |
| Collection sort menu | `Dashboard` + `CardSort` | Newest, oldest, name A–Z, and name Z–A are wired with newest-first default, stable ties, background sorting, and persisted selection. |
| Camera / capture | `CaptureScreen` | Existing CameraX preview and capture flow are retained and previewed. Device-camera behavior still needs hardware validation. |
| Specimen card / detail | `SpecimenScreen` | Common, uncommon, and rare treatments; scientific/common names; quality; capture date/location; fallback text; and long-name wrapping are implemented. Card photo is the locally captured specimen image rather than the export's remote Unsplash placeholder, preserving offline use and the user's actual capture. |
| Result/classification | `ClassifyingScreen` and specimen reveal | The export has no separate result frame. The app's existing local classification/loading screen and reveal flow remain; the loading screen now has a Compose preview. |
| Settings | No matching frame | No new settings screen was added. |
| Share/export | Specimen toolbar + `CardArtwork` | Added PNG capture of the visible card and Android share sheet. The export includes the displayed card and its shadow margin; bounds are rejected rather than silently cropping when the full card is outside the app window. |

## Visual verification status

- Compose previews are present for the dashboard, splash progress/error, camera, classification, and specimen common/uncommon/rare variants.
- An isolated Explorer card preview is available alongside the dashboard previews for direct comparison with the Figma sample.
- The latest Explorer card comparison uses the current Figma Make source; other mapped frames are unchanged by this update.
- The app reuses its bundled DM Sans/DM Serif resources and the Figma-derived vector path icons; no font or icon substitution was introduced.
- The theme file centralizes the font families, core colors, spacing scale, component dimensions, strokes, elevation, and motion values. **Deviation:** one-off color/spacing values and some gradient stops remain inline in screen drawing code, so token extraction is not yet exhaustive.
- The specimen image height adapts to available height and the dashboard is capped at 560 dp to reduce overflow on small/large phones. No physical-device runs at the requested 5.0–6.9 inch sizes have been performed.
- No rendered screenshot comparison against the Figma frames has been run in this environment. Pixel-level parity, exact animation timing/easing, safe-area behavior, and screenshot edge/shadow parity therefore remain unverified; the Compose previews are comparison aids, not screenshot-test evidence.
