# Cobalt Material icon usage report

Inspected the Android source on `main`, 2026-11-11. This is a source audit; no runtime UI test or APK change was made.

The category called “Material fallbacks” contains explicit AndroidX Compose Material icon references. The inspected code does not try a Cobalt drawable and switch to Material when it fails. These icons are chosen directly with `Icon(imageVector = Icons...)`, or returned by the shared feedback model. “Material icons used in the app” is a more accurate description.

## Coverage

- 29 distinct referenced Material icon definitions, across 18 Kotlin files.
- The scan finds 41 source lines containing 43 icon expressions. One optional icon branch (Sell) is currently disabled by its only caller.
- 17 definitions have an existing Cobalt symbol with the same basic meaning, or an existing semantic alternative. This does not mean their geometry is identical.
- The remaining 12 definitions include absent symbols, circle/triangle status variants, and the dormant version-tag decoration.
- `Sell` is optional and currently hidden: `AppVersionBadge` defaults to `showIcon = false`; its only app call in Home does not override that default.
- Shared components make these icons appear in more screens than the 18 direct reference files.

## Where they appear and why

| Area | Material icons | Actual role |
|---|---|---|
| Settings and model picker | Info, Search, Clear, Check, CheckCircle | Tooltips, guidance, searching models and marking selected options. |
| Switch controls | Rounded Check / Close | Animated on/off markers inside the switch thumb, at `SwitchDefaults.IconSize`. Small-icon legibility matters when replacing them. |
| Shared feedback banners | CheckCircle, Info, Warning, Error | `UserFeedbackMessage.icon` maps severity to an `ImageVector`; `AppFeedbackBanner` renders that property. These are currently used by MainActivity and media search. |
| Crash reporting | BugReport, Email, ContentCopy | Crash-dialog identity, sending the report and copying its text. |
| Feedback and FAQ | Add, AttachFile, CheckCircle, AutoAwesome, ThumbUp / ThumbDown | Add bug reproduction steps, attach evidence, confirm submissions and rate help answers. |
| Media and capture | CloudOff, Refresh, Close, VolumeMute / VolumeUp, Warning | Unavailable files, playback failure/retry, dismissing poster zoom, sound toggles and extraction failures. |
| Trakt | Rounded Refresh / Check | Sync now and confirm a successful connection. |
| Folder creation and release notes | Add, Close, ChevronRight | Submit an inline folder, dismiss release notes and open full version history. |
| Version badge | Sell | Optional tag decoration; currently hidden. |

## Why this mixture exists

What the source establishes:

1. The app depends on Material icons core and extended. Their `ImageVector` assets are immediately available to Compose code.
2. Some abstractions were built around `ImageVector`, especially `UserFeedbackMessage.icon`, so using an XML drawable there requires changing that abstraction or adding a painter/resource path. Ordinary `Icon` composables already support `painterResource`, so this is not a platform limitation.
3. The local library lacks dedicated attachments, thumbs, volume states, unavailable-media/offline, warning-triangle, error-circle and success-circle assets.
4. Adoption of the Cobalt library is partial. For example, the Gemini picker already uses `ic_custom_sync` for refresh, while its search/clear/selected markers remain Material. Crash-report email/copy and release-note close/chevron also have Cobalt equivalents.

The likely reason is a mixture of existing Material component code and gaps in the custom catalog. The original intent of each author is not documented, so this is an inference. Repository history supports partial adoption: commit `91a1d42` (“style(ui): refresh shared icons and semantic action glyphs”) refreshed many Cobalt resources but changed only one settings icon call site; it did not migrate every remaining Material reference.

## Full inventory


Each location below is an active source expression except the separately noted dormant Sell branch. Conditional screens and states are only displayed when their conditions occur.

| Material definition | Source locations | UI purpose | Cobalt option / gap |
|---|---|---|---|
| `Icons.AutoMirrored.Filled.VolumeMute` | [VideoPlayer:414](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/VideoPlayer.kt:414) | Show the muted state / unmute action in video controls. | New volume-muted icon; preserve RTL behavior (missing dedicated symbol) |
| `Icons.AutoMirrored.Filled.VolumeUp` | [VideoPlayer:414](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/VideoPlayer.kt:414) | Show the unmuted state / mute action in video controls. | New volume-on icon; preserve RTL behavior (missing dedicated symbol) |
| `Icons.AutoMirrored.Rounded.OpenInNew` | [ProfileSettings:918](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/ProfileSettings.kt:918) | Open Google AI Studio to get a Gemini API key. | ic_custom_link_external; preserve RTL mirroring (existing cobalt equivalent) |
| `Icons.Default.Add` | [InlineNewFolderCreator:277](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/InlineNewFolderCreator.kt:277)<br>[FeedbackScreen:460](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/FeedbackScreen.kt:460) | Add a folder; add a reproduction step to a bug report. | ic_custom_plus (existing cobalt equivalent) |
| `Icons.Default.AttachFile` | [FeedbackScreen:584](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/FeedbackScreen.kt:584) | Attach a screenshot or recording to a bug report. | New attachment / paperclip icon (missing dedicated symbol) |
| `Icons.Default.AutoAwesome` | [FeedbackScreen:1133](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/FeedbackScreen.kt:1133) | Celebrate a submitted feature request. | ic_custom_sparkle or ic_custom_idea (existing cobalt equivalent) |
| `Icons.Default.BugReport` | [ExpressiveCrashReportDialog:74](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ExpressiveCrashReportDialog.kt:74) | Mark the crash-report dialog. | ic_custom_feedback (existing cobalt equivalent) |
| `Icons.Default.Check` | [WidgetSettingsScreen:385](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/WidgetSettingsScreen.kt:385)<br>[EdgePanelAnimationSettingsScreen:365](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/EdgePanelAnimationSettingsScreen.kt:365) | Mark the selected widget action and selected dock animation. | ic_custom_check (existing Cobalt equivalent) |
| `Icons.Default.CheckCircle` | [UserFeedbackState:28](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/util/UserFeedbackState.kt:28)<br>[FeedbackScreen:623](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/FeedbackScreen.kt:623) | Show success in feedback banners and after submitting a bug report. | New success-circle icon, or a Cobalt check inside a circle (missing exact status variant) |
| `Icons.Default.ChevronRight` | [ReleaseNotesBottomSheet:177](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ReleaseNotesBottomSheet.kt:177) | Navigate from release notes to the full changelog. | ic_custom_chevron_right (existing cobalt equivalent) |
| `Icons.Default.Close` | [ReleaseNotesBottomSheet:120](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ReleaseNotesBottomSheet.kt:120)<br>[MediaDetailScreen:667](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/MediaDetailScreen.kt:667) | Dismiss release notes and a zoomed media poster. | ic_custom_close (existing cobalt equivalent) |
| `Icons.Default.CloudOff` | [UnavailableMediaPlaceholder:63](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/UnavailableMediaPlaceholder.kt:63)<br>[VideoPlayer:327](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/VideoPlayer.kt:327)<br>[HomeScreen:1717](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/HomeScreen.kt:1717) | Unavailable media and playback-error states; compact unavailable thumbnails on Home. | New media-unavailable icon; consider a separate offline-cloud icon (missing dedicated symbol) |
| `Icons.Default.ContentCopy` | [ExpressiveCrashReportDialog:159](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ExpressiveCrashReportDialog.kt:159) | Copy a crash report to the clipboard. | ic_custom_copy (existing cobalt equivalent) |
| `Icons.Default.Email` | [ExpressiveCrashReportDialog:144](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ExpressiveCrashReportDialog.kt:144) | Send a crash report by email. | ic_custom_mail (existing cobalt equivalent) |
| `Icons.Default.Error` | [UserFeedbackState:31](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/util/UserFeedbackState.kt:31) | Error severity in the shared feedback-message model. | New error-circle icon (missing dedicated symbol) |
| `Icons.Default.Info` | [UserFeedbackState:29](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/util/UserFeedbackState.kt:29) | Informational severity in the shared feedback-message model. | ic_custom_info (existing cobalt equivalent) |
| `Icons.Default.Refresh` | [VideoPlayer:371](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/VideoPlayer.kt:371)<br>[CaptureScreen:2883](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/CaptureScreen.kt:2883) | Retry failed video playback; clear a selected media preview to choose another item. | ic_custom_sync for retry; ic_custom_edit for Change, or a dedicated refresh icon (existing semantic alternatives) |
| `Icons.Default.Sell` | [AppVersionBadge:73](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/AppVersionBadge.kt:73) | Optional release-channel/version badge decoration. The only call site leaves showIcon=false, so this icon is currently hidden. | New version-tag icon if this decoration is enabled (dormant optional symbol) |
| `Icons.Default.ThumbDown` | [SupportScreen:531](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/SupportScreen.kt:531) | Rate an FAQ answer as not helpful. | New thumbs-down icon (missing dedicated symbol) |
| `Icons.Default.ThumbUp` | [SupportScreen:516](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/SupportScreen.kt:516) | Rate an FAQ answer as helpful. | New thumbs-up icon (missing dedicated symbol) |
| `Icons.Default.Warning` | [UserFeedbackState:30](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/util/UserFeedbackState.kt:30)<br>[CaptureScreen:1327](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/CaptureScreen.kt:1327)<br>[CaptureScreen:3199](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/CaptureScreen.kt:3199) | Extraction failure in capture; warning severity in shared feedback banners. | New warning-triangle icon (missing dedicated symbol) |
| `Icons.Outlined.Info` | [ExpressiveSettingsComponents:124](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ExpressiveSettingsComponents.kt:124)<br>[ExpressiveSettingsComponents:234](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ExpressiveSettingsComponents.kt:234)<br>[ExpressiveSettingsComponents:1354](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ExpressiveSettingsComponents.kt:1354)<br>[ProfileSettings:690](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/ProfileSettings.kt:690) | Settings tooltips; blur-strength guidance; floating-scrollbar explanation. | ic_custom_info (existing cobalt equivalent) |
| `Icons.Outlined.Warning` | [ProfileSettings:1184](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/ProfileSettings.kt:1184) | Prompt the user to grant overlay permission. | New warning-triangle icon (missing exact status variant) |
| `Icons.Rounded.Check` | [ExpressiveSwitch:55](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ExpressiveSwitch.kt:55)<br>[ExpressiveSettingsComponents:580](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ExpressiveSettingsComponents.kt:580)<br>[TraktSyncSettingsScreen:787](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/TraktSyncSettingsScreen.kt:787) | Checked switch thumb; selected settings option; successful Trakt connection. | ic_custom_check (existing cobalt equivalent) |
| `Icons.Rounded.CheckCircle` | [ExpressiveSettingsComponents:817](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ExpressiveSettingsComponents.kt:817) | Mark the selected Gemini model. | New success-circle icon, or a Cobalt check inside a circle (missing exact status variant) |
| `Icons.Rounded.Clear` | [ExpressiveSettingsComponents:735](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ExpressiveSettingsComponents.kt:735) | Clear a search query in the Gemini model picker. | ic_custom_close (existing cobalt equivalent) |
| `Icons.Rounded.Close` | [ExpressiveSwitch:55](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ExpressiveSwitch.kt:55) | Show the unchecked state inside an expressive switch thumb. | ic_custom_close (existing cobalt equivalent) |
| `Icons.Rounded.Refresh` | [TraktSyncSettingsScreen:383](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/screens/TraktSyncSettingsScreen.kt:383) | Run Trakt Sync Now. | ic_custom_sync (existing cobalt equivalent) |
| `Icons.Rounded.Search` | [ExpressiveSettingsComponents:727](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/ExpressiveSettingsComponents.kt:727) | Search the Gemini model picker. | ic_custom_search (existing cobalt equivalent) |

Shared feedback rendering: [UserFeedbackState.kt](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/util/UserFeedbackState.kt:26) defines the severity icons; [AppFeedbackBanner.kt](/home/hanan-bhatti/StudioProjects/second-brain/app/src/main/kotlin/com/example/ui/components/AppFeedbackBanner.kt:109) draws them.

## Recommendations

1. Reuse existing Cobalt symbols for search, clear/close, add, copy, mail, info, chevrons, external links, sync and feedback. Check switch-thumb visibility before replacing tiny filled markers with outline artwork.
2. Add missing symbols as one coherent Cobalt family: attachment, thumbs up/down, volume on/muted, media unavailable, warning triangle, error circle and success circle. A version tag is only needed if its optional decoration is enabled.
3. Make the shared feedback icon API accept drawable resource IDs or a small icon abstraction, so status icons can use the same source library.
4. Preserve automatic mirroring when replacing external-link and volume icons in RTL layouts.
5. Review two semantic mismatches: CloudOff is also used for a missing local file, which can imply a network problem; Refresh also labels a “Change” action that clears the selected media rather than refreshing it.
6. Remove unused Material imports separately. Imports alone are not evidence that an icon is rendered.

## Catalog ordering

The first Figma import sorted friendly names alphabetically within each category, which separated Undo from Redo and split other related pairs. The catalog and main-component area now use semantic rows that keep Undo/Redo, Import/Export, Play/Pause/Stop, Show/Hide, and outline/bold navigation states together. All 167 component identities are preserved.
