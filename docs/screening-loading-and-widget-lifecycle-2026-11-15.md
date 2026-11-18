# Screening entry and widget lifecycle — 15 November 2026

## Confirmed causes

- Your taste created a null snapshot and a loading flag for each composition, then queried Room and rebuilt the taste profile/ranking in a launched effect. The screen explicitly displayed an indeterminate progress bar until the first snapshot arrived.
- Review a pick created a null pick, reran ranking, and waited for delivery persistence before assigning visible content. Its loading branch had no card. Returning to either screen triggered another refresh through lifecycle observers.
- Screening settings listened to every widget preference change and reranked after each change or resume. Those rankings called `ScreeningStore.observe`, which rewrote title/progress records even for an unchanged library.
- The Screening preview queried the saved item and reactions from composition-related paths, fetched playback again for each preview, and allocated/rendered a complete bitmap on the UI thread.
- Both widget editors appended the persistent draft ID `-1` to the carousel, including when no configured widget existed. The Screening draft label disappeared when it was the only page.
- Provider deletion callbacks existed. `placedIds` filtered Android-bound IDs by saved configuration; it did not infer placement from saved configuration alone. Read-only inspection of the physical phone found eight Cobalt registrations across four launcher hosts. Android still considers those IDs bound; its public provider API does not reveal whether the launcher displays each one.

## Changes

- An application-scoped `ScreeningReadModel` prepares a media-only catalog, learning snapshot, ranking, title indexes, and saved notification deliveries eagerly. All Screening screens share it. Refreshes retain the previous usable snapshot, including after an error. Opening a screen no longer clears data or starts a fresh ranking.
- Store invalidation follows committed database changes. Consistent reads and repeated publication do not invalidate the model. Widget configuration changes do not trigger taste reranking.
- Reviews render the selected title and enable the feedback sheet immediately. Durable reservation/publication runs afterward; answering still validates the current title and preserves first-answer-wins behavior.
- App previews use native layout/text/icons with the approved full artwork, 48 dp thumbs, and optional playback edge. Optional artwork loads separately. Playback and reactions come from shared state; the preview performs no database read or full bitmap rendering in composition.
- Background ranking observes an unchanged media library once, then reads existing signals. Changed media and reset progress baselines are still observed. Genre normalization reuses its regex, and genre affinity reuses the profile's index.
- `WidgetInventory` prepares widget configuration off the UI thread, shows only configured IDs that Android still registers, and updates after configuration/deletion callbacks or activity resume. Unregistered configurations remain available for Android's later `onRestored` ID remapping; inventory refresh does not delete them.
- Widget carousels contain configured registrations only. A draft appears after an explicit Create action and is labelled **Preview · not added**. Removing the last saved widget returns to the creation entry, rather than substituting a draft card.
- Late settings/refresh writes cannot recreate deleted configurations. **Remove from Cobalt** clears an individual saved widget configuration and its Folio preview cache while preserving notes, folders, media and feedback.
- Lightweight Folio rows and folders warm eagerly in the existing ViewModel. Widget pin preparation and persistence run off the UI thread.
- Eager Room readers defer while note migration or startup recovery is active, then start when both guards clear. Leaving recovery publishes the cleared guard before its preference notification, so readers cannot miss the transition. Their initial read receives priority over optional widget writes without delaying activity creation.

## Verification

- Final focused host suite passed: **108 tests across 13 suites, zero failures, errors or skipped tests**.
- The actual Taste, Why, saved-title search, review, notification question and widget-settings wrappers were exercised with prepared data while the learning database was deliberately locked. The first populated taste frame retained its snapshot, the review card appeared, and its feedback sheet remained available without waiting for persistence. No indeterminate entry loader was present.
- Native preview checks passed at 320 dp width and 1.6 font scale. Both 48 dp thumb targets remained inside the card, separate from its title. Generated screenshots were visually inspected.
- Normal provider deletion and explicit removal with a launcher-retained bound ID both returned to the creation entry. Late writes could not recreate deleted configurations. Inventory ignored unregistered IDs while preserving their configuration for restoration; pending pins and drafts survived reconciliation.
- A synthetic note sentinel kept its title, content, folder membership and active state after widget removal. All 60 synthetic genres remained in the taste profile, with five shown in the overview and 55 in Others.
- The installed startup crash guard was exercised: eager readers stayed deferred until migration and recovery both cleared, and the recovery preference notification observed the guard already cleared.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest` succeeded. Instrumentation was assembled only; no connected-device test ran. `git diff --check` passed.

Final command:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.example.media.recommendation.*' \
  --tests 'com.example.widget.*' \
  --tests 'com.example.ui.screens.ScreeningEntryUiTest' \
  --tests 'com.example.notes.NoteMigrationGateTest' \
  --tests 'com.example.notes.NoteBackgroundReadinessTest' \
  :app:assembleDebug :app:assembleDebugAndroidTest \
  -x :app:syncIssueVersions --console=plain
```

Synthetic host screenshots:

- `app/build/reports/screening-entry/review-native.png`
- `app/build/reports/screening-entry/preview-320dp-large-font.png`
- `app/build/reports/screening-entry/widget-removed.png`

Built artifacts and SHA-256:

```text
app/build/outputs/apk/debug/app-debug.apk
d1639957fca181da2008d978795e30a272285014b15986a1954173fb04b906e5

app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
4e1a4e26d10d00662cf9d59aa30794a74fa4c093daf1d2a4145f1eda3d7757a4
```

The attached physical phone is not a test device. Only its Android widget registry was inspected. No APK installation, instrumentation, app launch, widget manipulation, note access, or app-data change was performed on it.

## Scope and limits

The speed target is a new screen composition inside an already running app, with shared data retained during asynchronous updates. Process startup still requires an initial local database read; there is no synchronous database read added to navigation. These host checks establish rendering and lifecycle behavior, not measured emulator or physical-device latency. Optional artwork may arrive after the native card layout.

Normal Android widget deletion removes the saved entry automatically. A launcher that retains a removed widget's bound ID cannot be distinguished safely from a launcher displaying that widget using Android's public provider API. **Remove from Cobalt** is the explicit cleanup path for such leftover registrations. It does not delete Android's launcher-owned IDs.

Every genre still learns independently. The overview remains five leading genres plus expandable Others.
