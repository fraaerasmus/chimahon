# Chimahon Custom: Fork Overview

Chimahon Custom is an identity-separated, upstream-tracking fork of
[sohilsayed/chimahon](https://github.com/sohilsayed/chimahon), maintained at
[fraaerasmus/chimahon](https://github.com/fraaerasmus/chimahon). Upstream lineage:
Mihon → TachiyomiSY → Komikku → Chimahon → **Chimahon Custom**.

This is an **overlay fork**: we take all of upstream and keep divergence as thin as
possible. Every diverged line is a future merge conflict.

## What this fork changes (the "always-ours" list)

Re-verify these after every upstream merge. Each of these lines is fenced like any other
fork line:

| What | Where |
|---|---|
| `applicationId = "app.chimahon.custom"` | `app/build.gradle.kts` |
| App name "Chimahon Custom" | `i18n/src/commonMain/moko-resources/base/strings.xml` (`app_name`) |
| Debug app name "Chimahon Custom Dev" | `app/src/debug/res/values/strings.xml` |
| Updater repo `fraaerasmus/chimahon` | `app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt` (`getGithubRepo()`) |
| `Constants.GITHUB_PROJECT` → fork URL | `core/common/src/main/kotlin/tachiyomi/core/common/Constants.kt` |
| About-screen GitHub link → fork URL | `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/AboutScreen.kt` |
| `local-maven/` repo listed first (vendored FlexibleAdapter, see its README) | `settings.gradle.kts` (`dependencyResolutionManagement.repositories`) |

Signing: our own keystore (never upstream's), applied in CI via repo secrets
`SIGNING_KEY` / `ALIAS` / `KEY_STORE_PASSWORD` / `KEY_PASSWORD`.

## Deliberately NOT changed

- Internal package names / `namespace = "eu.kanade.tachiyomi"`: renaming buys nothing
  and costs every merge.
- OCR model downloads still pull from `sohilsayed/chimahon-local-models`
  (`app/.../data/ocr/ModelDownloader.kt`). Fork that repo only if it disappears or we
  need our own models.
- User-Agent strings embedding the upstream repo URL (`MangabakaApi.kt`,
  `BangumiInterceptor.kt`).
- Leftover `Komikku-*` artifact names in `build_pull_request.yml` / `build_benchmark.yml`.
- `codeberg_mirror.yml`, which is inert here (guarded to `komikku-app/komikku`).
- `chimahon/src/main/cpp/hoshidicts` submodule still points at `Manhhao/hoshidicts`
  (third-party; no need to repoint).

## Merge philosophy

- `origin` = upstream (fetch only), `fork` = ours (push only to this). Default branch:
  `chimahon-custom`.
- If upstream implements something we built: drop ours, adopt theirs.
- If upstream refactors a file we touch: adopt their architecture, re-apply our thin slice.
- `CHANGELOG.md` stays a byte-clean mirror of upstream's; fork changes go to
  `FORK_CHANGELOG.md`.
- Recurring flows are encoded as Claude Code skills (`merge-upstream`,
  `ship-chimahon-custom`) under `.claude/skills/` on the maintainer's machine
  (kept untracked).

## How fork code is laid out

Fork code lives in fork-owned files. An upstream file carries only short hooks into it, and
every hook is fenced:

```kotlin
// Custom -->
chimahon.custom.di.CustomModule.register(this, app)
// Custom <--
```

- **The marker is `Custom`, not `Chimahon`.** Upstream uses `// Chimahon -->` for its own
  additions, so that marker cannot tell our lines from theirs. Forms: `// Custom -->` and
  `// Custom <--` around a block, `/* Custom --> */ ... /* Custom <-- */` inside one line,
  `# Custom -->` in proguard rules, and `<!-- Custom -->` with `<!-- /Custom -->` in XML,
  where a comment cannot contain two dashes.
- **Fork-owned roots:** `app/src/main/java/chimahon/custom/` (`di`, `core`, `ui`, `kosync`,
  `upload`, `player`, `youtube`, `lookup`), `chimahon/keybinding`,
  `chimahon/novel/{kosync,opds}`, `chimahon/custom/lookup` in the `chimahon` module, and the
  `strings_custom.xml` files in `i18n` and `i18n-ank`. Not everything under
  `app/src/main/java/chimahon/` is ours: `chimahon/novel/ui/reader` is upstream's.
- **The audit:** `python3 .github/scripts/custom_fence_audit.py` lists every fork line in an
  upstream file that sits outside a fence. It reads the working tree, so run it before a
  commit and after every upstream merge. It must report nothing.
- **Hooks name fork code in full** (`chimahon.custom.upload.serverUploadPreferences(...)`), so
  they need no import. ktlint rejects comments inside an import list, which is why imports are
  the one thing that cannot be fenced.

Ways to keep a hook thin, all in use:

- **Wrap without re-indenting.** To put upstream code inside a wrapper, open and close the
  wrapper in fenced lines and leave the lines between as they are. See
  `ProvideServerUploadAction` in `ui/manga/MangaScreen.kt` and the `SelectionContainer` in
  `SubtitleListPanel.kt`. The indentation looks off; the merge stays clean.
- **A branch ahead of upstream's.** To replace a branch of an `if` chain, add a fenced branch
  with the same condition in front of it and leave upstream's branch in place, unreached. See
  `GenericLookup` in `DictionaryRepository.kt`.
- **Carve out inline.** To exempt one case from upstream's logic, add a fenced term to its
  condition instead of rewriting the block. See the French carve-outs in `OcrLookupPopup.kt`.
- **Supply through a CompositionLocal.** To add something to a composable several layers down,
  provide it at the top and read it at the bottom. See `LocalCustomMangaActions`.
- **One registration point.** New services go in `CustomModule`, new strings in
  `strings_custom.xml`, new series-menu entries in `LocalCustomMangaActions`. None of these
  needs a new line in an upstream file.
- **Fork preferences in a fork class.** A fork option is stored through upstream's
  `PreferenceStore` from a class of ours (`KosyncPreferences`, `CustomGesturePreferences`,
  `KeyBindingPreferences`), not as a new function in one of upstream's preference classes.

### Shared pieces: reuse these before writing another

Each of these exists because the fork once had two or three copies of it.

| Need | Use | Where |
|---|---|---|
| Talk to a server the user runs (kosync, OPDS, WebDAV) | `CustomHttp.client.withTimeouts(...)` with `await()`. Not `NetworkHelper.client`: its DNS-over-HTTPS cannot resolve LAN or VPN names and its call timeout cuts large transfers. | `chimahon/custom/core/CustomHttp.kt` |
| Report a server's refusal, or a network failure, to the user | throw `ServerException(message)`, show `error.describeForUser()` | same file |
| A typed address without a scheme | `String.withHttpScheme()` | same file |
| Server address, username and password fields | `ServerLoginFields` | `chimahon/custom/ui` |
| A settings switch, list or slider | upstream's `Preference.PreferenceItem` on a `PreferenceStore` preference, as `KosyncScreen` and `CustomGestureSettings` do | |
| State that is not a setting, kept as a JSON file | `JsonFileStore`, which writes through `writeAtomic` | `chimahon/custom/core` |
| Parse XML from outside the app | `hardenedDocumentBuilderFactory` | `chimahon/custom/core/Xml.kt` |
| Is this language French | `LookupLanguage.isFrench` | `chimahon/dictionary/LookupPolicy.kt` |
| A fake server or preference store in a unit test | `TinyHttpServer`, `FakePreferenceStore` (upstream's `InMemoryPreferenceStore` forgets every write) | `app/src/test/kotlin/chimahon/custom/core` |

Adding the usual things:

- **A player key action:** an entry in `KeyAction` (screen, heading, title, argument kind), a
  branch in `PlayerKeyController.run`, and its strings. The settings rows, labels and
  validation follow from the entry.
- **A player gesture option:** a preference in `CustomGesturePreferences`, a field in
  `CustomGestureConfig`, a row in `CustomGestureSettings`, and its use in `GestureHandler.kt`.
  A row that sits between upstream's rows is one fenced line naming it.
- **A YouTube option:** a property in `YouTubeCustomPreferences` and a `CheckboxRow` or
  `RadioRow` in `YouTubeCustomSettings`.
- **A language-specific lookup rule:** in `LookupTextScanner` and `LookupPolicy`, and the same
  rule in `assets/shared/lookup-scanner.js`.

### Replaced upstream blocks

These hooks stand in for upstream lines that were deleted. If upstream changes the original,
the merge conflicts here and the fork side has to be updated by hand.

| Upstream file | What upstream had | Fork code that replaces it |
|---|---|---|
| `ui/youtube/YouTubeBrowserScreen.kt` | `goBack()`, the back and close buttons, the target / latest / home chain | `YouTubeBrowserNavigation`, `YouTubeBrowserStart` |
| `ui/youtube/YouTubeResolver.kt` | the subtitle and audio track lists in `resolveVideo` | `YouTubeStreamSelection` |
| `ui/player/controls/PlayerControls.kt` | `detectTapGestures` on the subtitle line, `extractOcrLookupString` in the tap selection | `claimTapWhereHit`, `extractOcrLookupSelection` |
| `ui/player/controls/components/panels/SubtitleListPanel.kt` | the scroll-to-active effect, the centred scroll delta, `clickable` on a row | `FollowActiveCue`, `centeredScrollDelta`, `tapLeavingLongPress` |
| `ui/player/controls/GestureHandler.kt` | the `areControlsLocked` state, the long-press guard and its screenshot body | `rememberCustomGestureConfig`, the rest fenced in place |
| `presentation/more/settings/screen/player/PlayerSettingsGesturesScreen.kt` | `enabled = !subtitleSwipeControls` on the two slider switches | `CustomGestureSettings.slidersAvailable` |
| `ui/reader/viewer/ReaderPageImageView.kt`, `ui/dictionary/ScreenLookupOverlayController.kt`, `ui/player/controls/PlayerVideoOcrOverlay.kt` | lookups starting at the tapped character | `OcrLookup.selectionAt`, `OcrLookup.orderedSelectionAt` |
| `ui/reader/viewer/OcrLookupPopup.kt`, `ui/library/novels/ChimaReaderActivity.kt` | highlight count and offset from the matched text | `LookupPolicy.highlightFor` |
| `ui/library/novels/NovelLibraryScreen.kt` | the add button | `NovelImportFab` |
| `chimahon/dictionary/DeinflectorHelpers.kt` | the loop in `RuleDeinflector.deinflect` | fenced in place |
| `assets/dictionary/renderer.js` | the tapped word as a plain string | fenced in place |

Upstream symbols whose visibility the fork widens (each is one fenced token; do not add more,
copy a small constant instead): `lookupWithSearchResolution` in `OcrLookupPopup.kt`,
`conditionHierarchy` in `DeinflectorHelpers.kt`, `addMangaToLibrary` in `ImportHandler.kt`.

## Where fork features live

- **KOReader sync:** `chimahon/novel/kosync` (client, document ids, XPointers, the novel
  manager, the settings screen under `ui/`) and `chimahon/custom/kosync` (the manga manager
  and its reader hook). `KosyncSession` is what the two managers share: the switches, the
  login, the device id and the rule for when a remote position replaces the local one. Each
  manager adds how its documents are identified and how a position converts. Settings are
  `KosyncPreferences` in the app's preference store; the user key is a private key and the
  device id is app state, so neither enters a backup by default. The novel reader calls
  `KosyncManager.pullOnOpen` from `ReaderScreen` and `KosyncReaderLifecycle` from
  `ChimaReaderActivity`; the manga reader holds one `MangaKosyncReaderHook`.
  `NovelDbPositionStore` is the bridge to upstream's DB-first reader, and
  `jumpToSyncedPosition` in the novel `ReaderViewModel` is the one method that has to live in
  upstream's class.
- **OPDS:** `chimahon/novel/opds` (browser, feed parser, client, `OpdsComicImporter`) and its
  `ui/` (`OpdsMangaScreen`, `NovelImportFab`, `NovelOpdsBrowser`). Novels go through upstream's
  `importBooks`, which returns its `Job` so the download can be deleted afterwards.
  `BookImporter` and `FileNames` are identical to upstream. Saved catalogs are one private
  entry in the preference store (`OpdsCatalogRepository`).
- **Server upload:** `eu/kanade/tachiyomi/data/upload`. These six files are ours but stay in
  an upstream package on purpose: two of them are WorkManager workers, and WorkManager stores
  the class name with every queued job. The UI is in `chimahon/custom/upload`.
- **Player key bindings:** `chimahon/keybinding`. `KeyAction` is the registry: each entry
  declares its screen, heading, title and argument. Bindings are stored one preference per
  screen (`pref_key_bindings_<screen>`), so a reader can get its own set later without a
  migration, and the player reads them live. `PlayerWordCursor` holds the key cursor; `WordCursorOpenEffect` and
  `PopupKeyScriptsEffect` connect it to the subtitle line and the popup. `cycleSubtitle` stays
  in `PlayerViewModel` because it needs the view model's private state.
- **Player gestures, reporting and assets:** `chimahon/custom/player`. Gesture options
  (`CustomGesturePreferences`, `CustomGestureConfig`, `CustomGestureSettings`,
  `CustomPlayerEnums`), the subtitle list (`SubtitleListSupport`), tapping
  a subtitle word (`SubtitleWordLookup`), media session state and Jellyfin reporting
  (`PlayerSessionReporters`, `JellyfinPlaybackReporter`), the mpv asset self-heal
  (`PlayerAssets`) and the sentence-audio header list (`SentenceAudioInput`). The Jellyfin
  reporter follows mpv's file-loaded event in the activity. The YouTube one
  (`YouTubeWatchHistorySync`) lives in the view model on purpose: the activity is recreated
  when a gamepad or keyboard connects, the view model is not, and a session restarted
  mid-video would send YouTube a second start ping for the same video.
- **YouTube:** `chimahon/custom/youtube`. Browser navigation and start page, stream selection,
  watch history sync, and the fork's settings and preferences. The preferences share upstream's
  `youtube_prefs` file under their own keys. `YouTubeAccount` says whether anyone is signed in,
  from the browser's cookies; the start page and the history sync both ask it.
- **Lookup:** `chimahon/dictionary` in the `chimahon` module holds `LookupTextScanner`,
  `DeinflectedLookup`, `LookupPolicy` and `LookupLanguage`; `chimahon/custom/lookup` holds
  `GenericLookup`, the one lookup both the popup and the Dictionary tab run for every language
  but Japanese, and, in `app`, `OcrLookup` for taps on OCR text. Elisions (`l'homme`) are
  upstream's to strip, in `FrenchTextPreprocessors`. `assets/shared/lookup-scanner.js` is the
  same scanner for the WebViews and has to be kept in step with the Kotlin one by hand.
- **Smaller pieces:** the ComicInfo `LanguageISO` field (`core-metadata` and
  `domain/manga/model/Manga.kt`), the `{secondary-subtitle}` Anki marker (`AnkiCardCreator`,
  `MediaInfo`), the playback log switch (`AniyomiMPVView`, `AdvancedPlayerPreferences`).

## Releases and versioning

Releases are tag-driven: pushing `vX.Y.Z` to the fork triggers `release.yml`
(build → sign → draft GitHub release). `versionCode = X*10000 + Y*100 + Z`, so versions
must stay monotonic. Convention: keep upstream's `X.Y` base and bump `Z` past both
upstream's latest and our last release; after merging a new upstream release, adopt its
`X.Y`. The in-app updater checks `fraaerasmus/chimahon` releases.
