# Instructions for Claude

- Before starting work, check that `.claude/skills/lukeneedham-conventions` matches
  `skills/lukeneedham-conventions` on `main` of `LukeNeedham/ci-workflows`; if not, copy it over and
  include that in this work's PR. See that repo's `docs/skill.md`.
- AppHub lists the open PRs of the owner's public repos with their APK and web deploy links, and downloads
  and installs the APKs. `domain/` is pure Kotlin (unit-tested); `data/` holds GitHub, download and install.
- When the shared CI comment format changes in `LukeNeedham/ci-workflows`
  (`.github/workflows/android_pr_build.yml`), update `CommentParser` and its test to match.
