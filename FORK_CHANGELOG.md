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

## Dropped (superseded by upstream)

- Player sentence audio mining (2026-07-18, dropped 2026-08-13): upstream v2.3.1/v2.3.2
  ship a full sentence-audio pipeline with external-track selection and AAC transcode.
- mpv config edit handling (2026-07-12, dropped 2026-08-13): upstream now persists
  mpv.conf and input.conf edits via preferences and resolves the config directory with
  a safe fallback, covering our earlier fix.
