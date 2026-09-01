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
  speed; speed is restored on release.
- KOReader progress sync (2026-09-01): new Settings > Data and storage > Novel
  KOReader Sync page pairs the novel reader with a kosync server, alongside the
  existing Drive sync. Books are identified by KOReader's partial MD5 over the packed
  EPUB, so the same file on a Kobo matches without changing any setting there. Pull on
  open and on resume applies a newer remote position, taking the chapter from the
  XPointer's DocFragment index and the paragraph from resolving the element path
  against the chapter XHTML, with percentage as the fallback. Push on close sends a
  crengine XPointer that always resolves, since KOReader applies a reflowable pull with
  no percentage fallback and an unresolvable pointer sends the device to page 1.
- Imported EPUBs keep their source bytes (2026-09-01): the importer now stores the
  packed EPUB verbatim beside the extracted tree. It previously kept only the extracted
  files, with images re-encoded to WebP, so there were no original bytes to identify a
  document by. Books imported before this change do not sync until they are re-imported.

## Dropped (superseded by upstream)

- Player sentence audio mining (2026-07-18, dropped 2026-08-13): upstream v2.3.1/v2.3.2
  ship a full sentence-audio pipeline with external-track selection and AAC transcode.
- mpv config edit handling (2026-07-12, dropped 2026-08-13): upstream now persists
  mpv.conf and input.conf edits via preferences and resolves the config directory with
  a safe fallback, covering our earlier fix.
