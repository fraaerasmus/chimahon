# Chimahon Custom: Fork Overview

Chimahon Custom is an identity-separated, upstream-tracking fork of
[sohilsayed/chimahon](https://github.com/sohilsayed/chimahon), maintained at
[fraaerasmus/chimahon](https://github.com/fraaerasmus/chimahon). Upstream lineage:
Mihon → TachiyomiSY → Komikku → Chimahon → **Chimahon Custom**.

This is an **overlay fork**: we take all of upstream and keep divergence as thin as
possible. Every diverged line is a future merge conflict.

## What this fork changes (the "always-ours" list)

Re-verify these after every upstream merge:

| What | Where |
|---|---|
| `applicationId = "app.chimahon.custom"` | `app/build.gradle.kts` |
| App name "Chimahon Custom" | `i18n/src/commonMain/moko-resources/base/strings.xml` (`app_name`) |
| Debug app name "Chimahon Custom Dev" | `app/src/debug/res/values/strings.xml` |
| Updater repo `fraaerasmus/chimahon` | `app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt` (`getGithubRepo()`) |
| `Constants.GITHUB_PROJECT` → fork URL | `core/common/src/main/kotlin/tachiyomi/core/common/Constants.kt` |
| About-screen GitHub link → fork URL | `app/src/main/java/eu/kanade/presentation/more/settings/screen/about/AboutScreen.kt` |

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

## Releases and versioning

Releases are tag-driven: pushing `vX.Y.Z` to the fork triggers `release.yml`
(build → sign → draft GitHub release). `versionCode = X*10000 + Y*100 + Z`, so versions
must stay monotonic. Convention: keep upstream's `X.Y` base and bump `Z` past both
upstream's latest and our last release; after merging a new upstream release, adopt its
`X.Y`. The in-app updater checks `fraaerasmus/chimahon` releases.
