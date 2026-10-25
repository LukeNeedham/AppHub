# PR status pill colours

Each pull request row ends in one pill: two lines (see below), then, after a divider, the icon of
what a press does next (or the progress of a download). Pressing that icon does the action; pressing the text side opens a sheet.
The code is `controlOf` and `StatusControl` in `ui/HomePage.kt`.
The pill is a rounded rectangle (12dp corners), not a full circle shape: a stadium's end caps waste space.

## The rule

The colour says **what needs you**, not what kind of thing the state is. A state takes its colour from where
it sits in that list, so a new state should be placed in it, not given a new colour.

| Colour | Means | States |
|---|---|---|
| Grey | Nothing to do: there is no APK and none is coming | No build, Cancelled |
| Blue | Something is running; wait | Building, Starting (main build asked for), Downloading |
| Brown (the app's primary colour) | An APK is ready and you can act on it | Ready, Update ready, Ready to install |
| Amber | What is on offer is out of date | Stale build: the new build has not finished, so only the previous APK can be offered. Behind main: the installed main build lacks later commits |
| Green | Done | Installed |
| Red | Broken | Build failed, Download failed |

Notes:

- **Brown is for action.** "Update ready" is brown, not amber: a newer build than the installed one is an
  opportunity, not a warning. Amber is kept for the stale build so that it always means "this APK lacks the
  latest commits".
- **Blue is for progress.** Building (CI) and Downloading (this device) share it because in both you wait. A press on Building opens the GitHub Actions run (the Open icon); a press on Downloading cancels the download.
- **The age of the APK** is the description (second line) of every pill that is about an APK (Ready, Update ready,
  Installed, Stale build…): "2 minutes old", "1 second old", "1 day old". For a stale PR build it is the stale APK's
  age, which a PR gets from the release the APK was published in. For an update it is the new build's age, not the
  installed one's. The timestamp is not on the pill: see the bottom sheet below.
- **Red is for failure** whoever's it is: a failed CI build or a failed download. The text tells them apart.
- Amber on a stale build wins over the build's own state, as the build is still running in that case.

## The two lines

Every pill lays out the same two lines, each exactly one line tall (ellipsized if too long), so all pills are
equally tall:

1. **Status**: the state, in the status colour ("Ready", "Stale build", "No build").
2. **Description**: depends on the status. By default how old the APK is ("2 minutes old"); for a stale or
   behind main build, how far it lags main ("3 commits behind"); while downloading, how far the download is ("42%",
   or "3.2 MB" when the size is unknown), and the status reads "Downloading…".

With no APK (No build, Building, Build failed) there is no description and the status is centred in the height of both lines.

## The bottom sheet

A press on the text side of a pill (left of the divider, not an action icon) always opens a bottom sheet and
never does an action, whatever the status and however many actions there are. The sheet shows:

1. The status as its title, and what the description says ("3 commits behind", "42%") when it says anything.
2. When the APK was built, relative ("2 minutes old") and absolute ("23/10/2026 18:27:11"), when known.
3. Every action of the pill (including cancelling a download), each a row with its icon and a short description
   of what it does. A press anywhere on a row runs the action and closes the sheet.

Pills with no APK (No build, Building, Cancelled…) open the sheet too, with just the status and the actions
they have, if any. The pill's own action icons still run their action with one press.

## The main build

The main build of a repo sits in the row of buttons at the top of its card, after the GitHub and Claude buttons. It uses the
same pill, from `controlOf(MainBuildItem)`. Its description is the age of the build, or, for a stale build or one behind main, how far it is behind main
("3 commits behind"); with no build only the status shows. Building main is an action of the pill:
the only one when there is no other (No build, Build failed), else the second one, next to the download, install or open
action of a build that lags main. It is left out when the build is up to date or one is running. Its states are those above, with these meanings:

- **Stale build** (grey): the APK on offer lacks commits, because `main` has moved on or a newer build is running.
- **Behind main** (grey, with the Open icon): the installed build is a main build, but `main` has newer commits.
- **Starting** (blue): the build was asked for and GitHub has not listed the run yet.
- Both are grey, not amber: `main` is not built for every push, so lagging behind it is expected and should
  draw little attention. Amber is only for a PR's stale build.
- **Installed** (green) means the installed build is the tip of `main`.

## The trailing icon

| Icon | Press does | When |
|---|---|---|
| Download | Downloads the APK, then installs it | An APK is ready and not installed, or an update or stale build |
| Install | Installs the APK already downloaded | Downloaded but not installed |
| Open | Starts the installed app | Installed |
| Circular arrow | Downloads again | **Retry** after a failed download, in red |
| Open | (second action) Opens the run that is building the replacement | Stale build whose replacement is building: the first action downloads or installs the stale APK |
| Wrench | Builds main as it is now | Main only: No build or Build failed (the only action), or a build behind main (the second action) |
| Progress ring | Cancels the download | Downloading |
| None | Nothing | No APK to act on: Building without an older APK, Cancelled, and a PR's No build or Build failed |

Only a press on an action's own icon (or the progress ring, which cancels) runs it; the text side of the pill only opens the sheet.

## Contrast

The status colours are mid-tones that read on dark backgrounds. In light mode the text and icon use a darker
shade of the status colour (30% towards black) so they stay readable on their own tint. The border and
divider keep the plain colour.

## Changing it

Keep the table above in line with `controlOf`. Meaning colours the Material scheme has no slot for (green,
amber, blue) live in `StatusColors` in `ui/theme/AppTheme.kt`.
