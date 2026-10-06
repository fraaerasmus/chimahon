# Chimahon Custom: Fork Changelog

Living summary of what this fork changes relative to upstream
([sohilsayed/chimahon](https://github.com/sohilsayed/chimahon)). Per-build notes live on
[GitHub Releases](https://github.com/fraaerasmus/chimahon/releases). Upstream's own
changelog is `CHANGELOG.md` (kept as a byte-clean mirror).

## Changed

- Identity separation (2026-07-12, base: upstream v2.2.0): app id
  `app.chimahon.custom`, app name "Chimahon Custom", in-app updater and About/GitHub
  links point at `fraaerasmus/chimahon`. Installs alongside upstream Chimahon.
- Builds signed with our own keystore (CI secrets), not upstream's.
- CI: the `client_secrets.json` step in `release.yml` / `build_pull_request.yml`
  tolerates a missing `GOOGLE_CLIENT_SECRETS_JSON` secret (upstream requires it;
  Google-account features are simply disabled in builds without it).
- YouTube extension navigation (2026-07-18): minimizes with in-process browser
  history retained, provides separate back, forward, and explicit session-exit
  controls, and defaults fresh signed-in sessions to Watch history with a configurable
  Home start page and signed-out fallback.
- French dictionary lookup (2026-07-18): scans from word starts and across phrases,
  handles elisions such as `l'homme`, ranks lemma definitions first, highlights the
  full matched selection, and formats Yomitan deinflection glossaries consistently
  across novel, manga/OCR, subtitle, recursive-popup, Process Text, and Dictionary-tab
  lookups. After the v2.3.2 merge (2026-08-13) this composes with upstream's
  per-language scan resolution: French keeps the phrase-aware scanner, other
  space-delimited languages use upstream's whole-word expansion.
- Player asset self-heal (2026-08-13): re-copies bundled mpv assets (`cacert.pem`,
  `subfont.ttf`) when the existing copy is unreadable, not just when sizes differ;
  an unreadable `cacert.pem` makes every TLS stream fail.
- Shared YouTube links (2026-08-13): plain VIEW/share intents carrying a YouTube URL
  route through the in-app YouTube pipeline instead of handing watch pages to mpv.
- Player long-press gesture (2026-08-16): new Player > Gestures setting to choose
  between the screenshot sheet (default) and YouTube-style hold-for-2x playback
  speed; speed is restored on release. After the v2.3.4 merge (2026-09-01) it sits
  alongside upstream's Anikku-aligned Gestures screen: upstream's "Disable long-press
  screenshot" toggle still applies, but only while the long-press action is set to
  Screenshot.
- KOReader progress sync (2026-09-01): new Settings > Data and storage > Novel
  KOReader Sync page pairs the novel reader with a kosync server, alongside the
  existing Drive sync. Books are identified by KOReader's partial MD5 over the packed
  EPUB, so the same file on a Kobo matches without changing any setting there. Pull on
  open and on resume applies a newer remote position, taking the chapter from the
  XPointer's DocFragment index and the paragraph from resolving the element path
  against the chapter XHTML, with percentage as the fallback. Push on close sends a
  crengine XPointer that always resolves, since KOReader applies a reflowable pull with
  no percentage fallback and an unresolvable pointer sends the device to page 1.
- OPDS catalog import (2026-09-01): the novel library's add button now offers OPDS
  catalogs as well as the file picker. Saved catalogs carry optional Basic-auth
  credentials, and browsing supports navigation and acquisition feeds, OpenSearch
  templates, and rel=next paging. Every href is resolved against the feed URL, honouring
  xml:base, because calibre's content server emits root-relative links. Downloads are
  streamed to a temp file and imported without repacking.
- Imported EPUBs keep their source bytes (2026-09-01): the importer now stores the
  packed EPUB verbatim beside the extracted tree. It previously kept only the extracted
  files, with images re-encoded to WebP, so there were no original bytes to identify a
  document by. Books imported before this change do not sync until they are re-imported.

- OPDS for manga (2026-09-07): Browse > Sources > add now offers "OPDS", which opens the
  same saved catalogs in comic mode. CBZ/CBR acquisition links (calibre's
  `application/x-cbz` / `x-cbr` and the `vnd.comicbook` types) are downloaded byte-exact and
  saved as `local/<series>/<title>.<ext>` for the local source, then the series is refreshed
  so the chapter appears at once. The series folder is the entry's series metadata when the
  feed has it (calibre writes `SERIES: name [index]` into the entry content) and otherwise
  the title with its trailing volume/chapter marker stripped, so calibre-style
  "Title, Vol. N" entries do not become one folder per volume. When an entry offers both CBR
  and CBZ (calibre lists formats alphabetically) the CBZ is taken, since only a CBZ can carry
  the server's `.mokuro` entry.
- KOReader sync for manga (2026-09-07): the kosync page also syncs CBZ chapters in the local
  source and in downloads, with a "Sync manga chapters" toggle. A chapter is identified by
  KOReader's partial MD5 of the archive file, so the same download from one OPDS catalog
  matches on a Kobo. Progress goes over the wire the way KOReader sends it for documents
  with pages: the 1-based page number as the progress value plus page / page count. Pull
  happens when a chapter opens and on returning to the reader, and applies a remote position
  newer than the last local page turn; push happens when a chapter is left or the reader is
  paused. Pulls are capped at four seconds so an unreachable server does not hold the reader.

- Server upload of downloaded chapters (2026-09-07): a series' three-dot menu gains "Upload
  to server". While on, every downloaded chapter stored as a single CBZ is PUT over WebDAV
  to `<WebDAV URL>/<upload folder>/<series>/<series>, Ch. NNN.cbz` (MKCOL first, skipped when
  the server already has the same size), and each new download follows as it completes.
  The upload reuses the WebDAV sync URL, username and password; the upload folder is a new
  setting under Data and storage > Server upload (default `manga`). After an upload the app
  polls for `<stem>.mokuro` beside the archive, first after two minutes and then doubling,
  giving up after six hours, and stores it as the reader's sibling sidecar so OCR text is
  available without re-downloading. Downloaded ComicInfo.xml now carries `LanguageISO` from
  the source language so the server's OCR sweep knows which engine to use. Turning the toggle
  on first creates the upload folder on the server and reports the outcome, the progress
  notification names the chapter and count, and a job that fails every attempt raises an
  error notification with the series and the last reason.
- OCR lookup respects line breaks (2026-09-08): for space-delimited languages the tap
  lookup in the manga reader and the video OCR overlay no longer runs into the previous or
  next OCR line. Block text joins lines without a separator, so tapping the first word of a
  line used to look up the previous line's last word glued to it. CJK lookups still scan
  across lines.
- Sentence audio from authenticated streams (2026-09-14): Anki cards mined from a
  Jellyfin (or Emby/Plex) video no longer fail with "The selected video source could not
  be read for sentence audio". The sentence-audio resolver refused any remote URL whose
  query carried a credential-looking parameter (`api_key`, `token`, `Signature`, ...) and
  refused the whole input when a source header was not on its allow-list. Jellyfin
  authenticates its stream URLs with `api_key`, so every card lost its audio. The URL is
  already what mpv plays and what frame/scene capture hands to FFmpeg, and the diagnostic
  journal redacts those values, so the query check is gone; headers are now filtered to
  the allow-list instead of being fatal, and the list includes `Authorization`, `Cookie`
  and the Emby/Plex token headers.
- Upstream v2.4.1 merge (2026-09-14): upstream moved the novel reader out of the `chimahon`
  module into `app` (`chimahon.novel`), made the novel library, reader and history DB-first,
  and removed the ttu/Drive sync. KOReader sync was re-threaded onto that: the code now lives
  in `app/src/main/java/chimahon/novel/kosync`, and for registered novels the position is read
  from and written to the novel chapter and history rows (the `bookmark.json` sidecar only
  serves unregistered books). The pull runs before the reader reads its resume rows on open
  and again when the reader returns from a stop; the push runs on stop, after the rows are
  flushed. Upstream now keeps the original EPUB bytes itself (`<folder>.epub`, or `book.epub`
  in the extraction cache), so the fork's `source.epub` copy is gone and kosync recognises all
  three names. OPDS novel downloads go through the same import path as the file picker, so the
  book is registered in the DB like any other import. The "Novel TTU Sync" settings entry is
  gone with upstream's removal; the Novels group holds KOReader Sync alone.
- Vendored FlexibleAdapter (2026-09-14): JitPack purged
  `com.github.arkon.FlexibleAdapter:flexible-adapter:c8013533` after its source repository went
  private, which broke every release build. The AAR and POM Gradle had cached now live in
  `local-maven/`, listed first in `settings.gradle.kts`, so the catalog coordinate stays
  upstream's. Drop it once upstream moves off the JitPack coordinate.
- Player media session reports playing/paused (2026-09-19): the anime player's
  `MediaSession` was created with an actions-only `PlaybackState` that was never updated,
  so outside observers (`MediaController.getPlaybackState`, e.g. a NotificationListener) saw
  state NONE for the whole video. The mpv `pause` observer now publishes `STATE_PLAYING` /
  `STATE_PAUSED`, ahead of the exit guard so the pause on backgrounding is reported too.
  `onDestroy` publishes `STATE_STOPPED` right before releasing the session, so observers
  see playback end instead of a last state stuck on PLAYING.
- Jellyfin playback reporting (2026-09-19): streams from the Jellyfin extension were invisible
  to the server (no session, no now-playing), because nothing called `/Sessions/Playing*`.
  `JellyfinPlaybackReporter` recognises such a stream from the video itself (a `MediaBrowser`
  `Authorization` header plus a `/Videos/{id}/` url), then reports start, pause/resume, a 10s
  progress check-in and stop, reusing that header so the session matches the extension's
  device and token. No settings; other sources, downloads, casting and the external player
  are untouched. Side effect, same as any Jellyfin client: the server now stores the resume
  position and marks items played near the end.
- Upstream v2.4.5 merge (2026-09-19): upstream brought back TTU sync, so the Novels group
  under Data and storage holds both "Novel TTU Sync" and "KOReader Sync" again, and on
  open the reader runs the TTU import and then the KOReader pull before it seeds the resume
  position. Upstream moved the Browse "+" import dialog into a shared
  `LocalMangaImportHost.kt` (also used by the new manga library add button); the fork's
  "OPDS" button is now an optional `onOpds` callback on `LocalMangaImportDialogs`, passed
  only from Browse > Sources. Upstream dropped the `DropdownMenu` import from
  `NovelLibraryScreen.kt` without a conflict while the fork's add menu still uses it; the
  import is restored.
- Player gestures and subtitle list (2026-09-27):
  - Vertical swipe chooser: "Subtitle swipe controls" used to switch off the volume and
    brightness swipes. Player > Gestures gains "Vertical swipe" under that switch, choosing
    between "Replay line / hide subtitles" (default, as before) and "Volume and brightness".
    Horizontal swipes keep moving between subtitle lines either way.
  - Gestures while locked: Player > Player > "Allow gestures while locked" (default off)
    keeps swipes, double tap and long press working while the controls are locked. The
    buttons stay hidden and a single tap still reveals the unlock button.
  - Double tap on the subtitle line: the subtitle layer claimed every touch on its box, so a
    double tap there did nothing. It now only claims touches that land on a word (lookup
    stays instant); the rest of the line, and the whole secondary subtitle line, fall
    through to the player gestures via `sharePointerInputWithSiblings`.
  - Subtitle side list: the active line was centred against the viewport height while item
    offsets start after the top content padding, which parked it half below the bottom edge
    and made each new line snap up and slide back. It is now centred with the viewport
    offsets, follows the cue id (so it keeps following once the history is capped at 120
    lines), and holds still between lines instead of jumping to the end of the list.
- Upstream v2.4.6 merge (2026-09-27): three conflicts, all unions. `PlayerActivity`'s
  `fileLoaded()` runs the Jellyfin reporter and then upstream's new Discord presence update.
  Upstream deleted `SurfacePlaybackLoadGateTest` because its copy no longer compiled against
  the `(url, options)` callback; the fork's copy already follows that callback and stays, so
  the file is fork-only from here on.
- Subtitle position past 100 no longer crashes (2026-09-27): upstream v2.4.6 maps positions
  above 100 (the slider goes to 150) to a negative bottom padding, which Compose rejects
  with "Padding must be non-negative" as soon as a subtitle is drawn. The fork clamps the
  padding at zero and moves the line down by the same amount with an offset. Drop this once
  upstream stops passing a negative padding.
- Selectable subtitle list (2026-09-27): long press a line in the player's subtitle side
  list to select text and get the system text menu: Copy, Select all, apps that handle
  selected text (Translate, this app's dictionary lookup) and the system's smart actions.
  It is Compose's `SelectionContainer`, so items a phone maker builds into its own text
  widget (Share, Samsung's Translate) are not in it; native `TextView` rows would be the
  way to get those. A tap still seeks to the line; the row no longer uses `clickable`,
  which claimed the touch and fired on release after a long hold.
- Seek swipe sensitivity (2026-09-27): Player > Gestures > "Seek swipe sensitivity" sets how
  far a horizontal swipe seeks, from 10% to 300% of the stock distance in steps of 10%
  (default 100%, the stock 0.15 seconds per pixel). It applies to the horizontal seek
  gesture, so it is greyed out while that gesture is off or "Subtitle swipe controls"
  replaces it.
- Lookup with the subtitle list open (2026-09-27): upstream blocks the subtitle dictionary
  popup while any panel, sheet or dialog is showing. The subtitle side list is now exempt,
  so tapping a word in the on-screen subtitle looks it up with the list open. Other panels,
  sheets and dialogs still block it.
- Player key bindings (2026-09-29): Player > "Key bindings" sets what each gamepad button
  and keyboard key does in the player. Upstream hardcodes a handful of keys, ignores gamepad
  buttons, and only sees a key when no view holds focus, so Space did not pause on a real
  device.
  - A binding is one key, a key with Ctrl, Alt or Shift, a long press, or a combination
    (one key pressed while another is held). It is set by pressing the key in the dialog.
  - Actions: play or pause, seek by a number of seconds, volume, previous, next and replay
    subtitle, show or hide subtitles, next subtitle track (primary and secondary), back,
    and any mpv command written as in input.conf.
  - Defaults: Space and gamepad A pause, Left and Right seek 5 seconds, Up and Down change
    the volume, L1 and R1 move between subtitles, Y replays one, Select hides them, L2 and
    R2 cycle the primary and secondary subtitle track, B goes back. Up and Down used to
    reach mpv, which seeks a minute; delete those two bindings to get that back.
  - A key with no binding goes where it went before, so mpv and input.conf keep working.
    While a sheet, panel or dialog is open only a key bound to back is taken.
  - A key that has a long press, or that a combination holds, runs its plain binding when
    it is let go instead of when it is pressed, since only then is it known which was meant.
  - Upstream's swapped Left and Right in `PlayerActivity.onKeyDown` are left as they are.
    The default bindings take both keys first, so the swap only shows if they are deleted.
- Looking words up with keys (2026-09-29): a subtitle word can be looked up, the popup read
  and a card added without touching the screen.
  - "Pick a subtitle word" (gamepad X or Enter) pauses and highlights the first word of the
    subtitle on screen. Left and Right move word by word. A or Enter opens the dictionary
    popup on the word, and Left and Right then move the popup to the next word.
  - In the popup Up and Down change entry, L1 and R1 scroll, Y plays the word's audio and X
    adds the entry to Anki. B or Escape closes it and playback carries on, unless the player
    was paused before the word was picked.
  - Words end where the dictionary's best match ends, which is what a tap on the same
    character looks up, so the cursor and the popup highlight the same text. A spot the
    dictionary does not know is one character in Japanese or Chinese and a whole word in a
    spaced language.
  - These keys are a second set, "While looking a word up", under Player > "Key bindings".
    It applies while a word is picked or the popup is open, including one opened by a tap.
  - Someone who changed their player bindings before this build has to add "Pick a
    subtitle word" themselves, as saved bindings are never rewritten.
  - The popup keys act on the entry nearest the top of the popup, as the popup's own arrows
    do. A last entry too short to scroll to the top cannot be reached with them.
- Key bindings listed by action (2026-09-29): Player > "Key bindings" now lists what can be
  done, under Playback, Subtitles, Look up a word, Dictionary popup and Other, with each
  action's keys drawn as key caps and "Not set" where there is none. Tapping an action opens
  its keys: press a key to add one, a chip makes it a long press, and nothing is kept until
  Save. A seek's seconds are edited there too. The four directions show as arrows, and a
  gamepad button carries a gamepad mark so its A is told from the A key.
- Arrows in a tapped popup (2026-09-29): Left and Right did nothing in a dictionary popup
  opened by a tap, since they move a cursor and a tap made none; they now start on the tapped
  word. Left at the first word, or Right at the last, closed the popup by asking for the word
  it already showed; it now stays open. Keys go to a second popup opened from inside the
  first, when the recursive lookup mode is "popup". A cursor left behind by playback resumed
  with a touch is dropped, so the arrows seek again.
- Volume keys take a size (2026-09-29): a key bound to the volume now moves it by a chosen
  number of the phone's volume steps, the way a seek takes seconds. "Volume up" and "Volume
  down" became one "Volume" action with a step count, negative for down; bindings saved with
  the old two read as one step each. Playback gains "Add a volume change" next to "Add a seek".
- One row per action (2026-09-30): the key bindings list no longer splits a seek or a volume
  change into a row per number, and the "Add a seek", "Add a volume change" and "Add an mpv
  command" rows are gone. Each action is one row, and a key that takes a number carries it on
  its cap ("← -5 s") and in the dialog. Brightness is a new such action: a key moves the
  player window's brightness by a count of 5% steps, negative to dim.
- Previous and next as one action with a step (2026-09-30): "Previous subtitle" and "Next
  subtitle" are one "Subtitle line" row whose keys carry -1 or +1, and the same for the two
  subtitle track rows, "Word", "Entry" and "Scroll" in the popup. A step of 2 or 3 works
  too. L2 and R2 still step the subtitle tracks forward, and holding them now steps back.
  Keys saved with the old names read as a step of one, so nothing changes until edited.
- Inflected words find their lemma (2026-10-01): in the popup, a form the rules cannot
  undo now also shows the word its "form of" entry points at, instead of only that entry.
  A rule for one part of speech no longer lands on an entry of another, and entries
  matched at the same length are ordered by fewest rule steps, exact headword, then
  frequency. Applies to every language deinflected in Kotlin. The looked-up form's own
  "form of" entry now sits right after its lemma instead of below every shorter match:
  the longest match leads, and the lemma-first rule only orders entries of equal length.
- Secondary subtitle on Anki cards (2026-10-01): a `{secondary-subtitle}` marker fills a
  field with the secondary subtitle line on screen when a word is looked up in the player.

- YouTube videos with dubbed audio tracks have sound (2026-10-04): release builds shrank
  away a protobuf message NewPipe uses to read each track's language tags, so any video
  carrying more than one audio track (an original plus YouTube dubs) lost all of its audio
  streams and played silent. A keep rule for protobuf-javalite messages fixes it. The
  resolver also now attaches one audio stream per language (original first, dubs marked)
  and one subtitle format instead of every itag and three formats, and the player selects
  the first audio track as soon as it opens, so audio no longer starts seconds after the
  video. Audio with no preferred language takes the source's first track instead of the
  device language, so an English device gets the original rather than the auto-dub.
- YouTube "Prefer reliable playback over highest audio quality" (2026-10-04): on by
  default in the YouTube settings. Resuming a video part-way seeks the external audio
  stream, and on a throttled connection that seek took 57 s in the Opus/WebM stream while
  the same seek in the AAC/MP4 stream took under a second (same video, same phone). On, the
  AAC stream is used unless only the 48 kbps tier exists; off, the best bitrate wins.
- YouTube captions and dictionary profile (2026-10-04): manually written captions are
  listed before auto-generated ones, which are now labelled "(auto-generated)", so a
  preferred subtitle language picks the uploader's captions when they exist. The YouTube
  settings gain a "Dictionary profile" choice for the whole source (the generic source
  settings screen that holds this picker is not reachable for the built-in extension); a
  profile set on a channel still takes priority.
- YouTube watch history sync (2026-10-05, off by default): the in-app browser intercepts
  every video tap before YouTube sees it, so the signed-in account never learned what was
  watched and its recommendations had nothing to go on. With "Sync watch history to
  YouTube" on, the player reports playback the way the official players do (a start ping,
  then the position every 10 s and on stop) using the browser session's cookies. Best
  effort against undocumented endpoints; failures are logged and ignored.
- Playback log switch (2026-10-05): Player > Advanced > "Write a playback log" saves mpv's
  detailed log to the app's external files folder, so a playback problem can be diagnosed
  without editing mpv.conf. Off by default.
- YouTube stream selection in its own file (2026-10-05): the rules for which audio and
  subtitle streams reach the player moved out of the resolver into `YouTubeStreamSelection`,
  pure functions with their own tests. No behaviour change.

## Dropped (superseded by upstream)

- Player sentence audio mining (2026-07-18, dropped 2026-08-13): upstream v2.3.1/v2.3.2
  ship a full sentence-audio pipeline with external-track selection and AAC transcode.
- mpv config edit handling (2026-07-12, dropped 2026-08-13): upstream now persists
  mpv.conf and input.conf edits via preferences and resolves the config directory with
  a safe fallback, covering our earlier fix.
