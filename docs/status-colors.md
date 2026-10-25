# PR status pill colours

Each pull request row ends in one pill: a coloured dot and the status, then, after a divider, the icon of
what a press does next (or the progress of a download). Pressing anywhere on the pill does that action.
The code is `controlOf` and `StatusControl` in `ui/HomeScreen.kt`.

## The rule

The colour says **what needs you**, not what kind of thing the state is. A state takes its colour from where
it sits in that list, so a new state should be placed in it, not given a new colour.

| Colour | Means | States |
|---|---|---|
| Grey | Nothing to do: there is no APK and none is coming | No build, Cancelled |
| Blue | Something is running; wait | Building, Starting (main build asked for), Downloading |
| Purple (the app's primary colour) | An APK is ready and you can act on it | Ready, Update ready, Ready to install |
| Amber | What is on offer is out of date | Old build: the new build has not finished, so only the previous APK can be offered. Behind main: the installed main build lacks later commits |
| Green | Done | Installed |
| Red | Broken | Build failed, Download failed |

Notes:

- **Purple is for action.** "Update ready" is purple, not amber: a newer build than the installed one is an
  opportunity, not a warning. Amber is kept for the old build so that it always means "this APK lacks the
  latest commits".
- **Blue is for progress.** Building (CI) and Downloading (this device) share it because in both you wait. A press on Building opens the GitHub Actions run (the Open icon).
- **The build time** ("Built 2 hours ago · 09/10/2026 12:56:20") is the second line inside the pill, once an APK of the latest commit is built.
- **Red is for failure** whoever's it is: a failed CI build or a failed download. The text tells them apart.
- Amber on an old build wins over the build's own state, as the build is still running in that case. The row
  keeps its "Out of date: there are later changes, rebuilding now" note.

## The main build

The main build of a repo sits in the row of buttons at the top of its card, after the GitHub and Claude buttons. It uses the
same pill, from `controlOf(MainBuildItem)`. Under the row, a line says how many commits the build is behind main (or that it is up
to date), with a **Build** button to its right that is left out when the build is up to date. Its states are those above, with these meanings:

- **Old build** (grey): the APK on offer lacks commits, because `main` has moved on or a newer build is running.
- **Behind main** (grey, with the Open icon): the installed build is a main build, but `main` has newer commits.
- **Starting** (blue): the build was asked for and GitHub has not listed the run yet.
- Both are grey, not amber: `main` is not built for every push, so lagging behind it is expected and should
  draw little attention. Amber is only for a PR's old build.
- **Installed** (green) means the installed build is the tip of `main`; the line under the row says which commit.

## The trailing icon

| Icon | Press does | When |
|---|---|---|
| Download | Downloads the APK, then installs it | An APK is ready and not installed, or an update or old build |
| Install | Installs the APK already downloaded | Downloaded but not installed |
| Open | Starts the installed app | Installed |
| Circular arrow | Downloads again | **Retry** after a failed download, in red |
| Progress ring | Nothing | Downloading |
| None | Nothing | No APK to act on: No build, Building without an older APK, Build failed, Cancelled |

## Contrast

The status colours are mid-tones that read on dark backgrounds. In light mode the text and icon use a darker
shade of the status colour (30% towards black) so they stay readable on their own tint. The dot, border and
divider keep the plain colour.

## Changing it

Keep the table above in line with `controlOf`. Meaning colours the Material scheme has no slot for (green,
amber, blue) live in `StatusColors` in `ui/theme/AppTheme.kt`.
