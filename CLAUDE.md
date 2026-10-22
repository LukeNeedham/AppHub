# Instructions for Claude

<!-- ci-workflows:begin (managed by LukeNeedham/ci-workflows, do not edit) -->
- `.claude/skills/lukeneedham-conventions` and the callers `trigger_on_pull_request.yml`, `build_main.yml`, `delete_prereleases.yml` in `.github/workflows/` are kept in sync from `LukeNeedham/ci-workflows` by CI, which
  keeps one PR titled "Sync from ci-workflows" open when they differ. Do not edit them here and do
  not copy them by hand: change them in ci-workflows (settings of a caller, such as `apk-path`, are
  the exception and stay as they are).
<!-- ci-workflows:end -->
- AppHub lists the open PRs of the owner's public repos with their APK and web deploy links, and downloads
  and installs the APKs. `domain/` is pure Kotlin (unit-tested); `data/` holds GitHub, download and install.
- When the shared CI comment format changes in `LukeNeedham/ci-workflows`
  (`.github/workflows/android_pr_build.yml`), update `CommentParser` and its test to match.
- The colours and icons of the PR status pill follow `docs/status-colors.md`; update it with any change to
  `controlOf` in `ui/HomeScreen.kt`.
- Main builds are started through the caller `.github/workflows/build_main.yml`, which calls
  `android_main_build.yml` in `LukeNeedham/ci-workflows`. `MAIN_BUILD_WORKFLOW` and `APK_ARTIFACT` must match
  those two; change them together.
