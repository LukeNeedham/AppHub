# Instructions for Claude

<!-- ci-workflows:begin (managed by LukeNeedham/ci-workflows, do not edit) -->
- `.claude/skills/lukeneedham-conventions` and the callers `trigger_on_pull_request.yml`, `build_main.yml`, `delete_prereleases.yml`, `release.yml` in `.github/workflows/` are kept in sync from
  `LukeNeedham/ci-workflows` by CI, which keeps one PR titled "Sync from ci-workflows" open when they
  differ. Do not edit them here and do not copy them by hand: change them in ci-workflows. The callers
  hold no settings: every app is built the same way (see `docs/app-config.md` there).
- **Always give the user the AppHub link when work on this app completes.** As soon as a PR is open,
  paste `https://www.lukeneedham.com/apphub/pr/<repo name>/<PR number>` into the chat (CI posts the
  same link as a PR comment). Do not wait for the build and do not paste the APK link instead.
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
