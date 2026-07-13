# Chimahon Custom — Fork Changelog

Living summary of what this fork changes relative to upstream
([sohilsayed/chimahon](https://github.com/sohilsayed/chimahon)). Per-build notes live on
[GitHub Releases](https://github.com/fraaerasmus/chimahon/releases). Upstream's own
changelog is `CHANGELOG.md` (kept as a byte-clean mirror).

## Changed

- Identity separation (2026-07-12, base: upstream v2.2.0): app id
  `app.chimahon.custom`, app name "Chimahon Custom", in-app updater and About/GitHub
  links point at `fraaerasmus/chimahon`. Installs alongside upstream Chimahon.
- Builds signed with our own keystore (CI secrets), not upstream's.
