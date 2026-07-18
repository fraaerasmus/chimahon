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
- YouTube extension navigation (2026-07-18): an explicit close control exits to
  Chimahon regardless of browser history, with separate reactive back and forward
  controls while Android back remains history-first.
- French dictionary lookup (2026-07-18): scans from word starts and across phrases,
  handles elisions such as `l'homme`, ranks lemma definitions first, highlights the
  full matched selection, and formats Yomitan deinflection glossaries consistently
  across novel, manga/OCR, subtitle, recursive-popup, Process Text, and Dictionary-tab
  lookups.
