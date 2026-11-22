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
| Blue | Something is running; wait | Building, Downloading |
| Purple (the action colour, `AppColors.action`) | An APK is ready and you can act on it, or a website's preview is up | Ready, Update ready, Ready to install, Preview |
| Amber | What is on offer is out of date | Stale build: the new build has not finished, so only the previous APK can be offered |
| Green | Done | Installed |
| Red | Broken | Build failed, Download failed, Login needed |

Notes:

- **Purple is for action.** "Update ready" is purple, not amber: a newer build than the installed one is an
  opportunity, not a warning. Amber is kept for the stale build so that it always means "this APK lacks the
  latest commits".
- **Blue is for progress.** Building (CI) and Downloading (this device) share it because in both you wait. A press on Building opens the GitHub Actions run (the Open icon); a press on Downloading cancels the download.
- **The age of the APK** is the description (second line) of every pill that is about an APK (Ready, Update ready,
  Installed, Stale build…): "2 minutes old", "1 second old", "1 day old". For a stale PR build it is the stale APK's
  age, which a PR gets from the release the APK was published in. For an update it is the new build's age, not the
  installed one's. The timestamp is not on the pill: see the bottom sheet below.
- **Red is for failure** whoever's it is: a failed CI build or a failed download. The text tells them apart.
- Amber on a stale build wins over the build's own state, as the build is still running in that case.

## A website's pill

A website (the stack `next-js` of ci-workflows) has **no APK**, so its PR has no APK comment. Its pill shows the preview instead, from the
comment of the CI with the header `web-preview-link` (`web_pr.yml` there; `CommentParser.parseWebsitePreview`, `WebsitePreview`). A PR with no APK comment
and no such comment still reads "No build". An app's PR never takes these states: its APK comment decides.

| Status | Colour | Means | Press on the icon |
|---|---|---|---|
| Preview | Purple | The preview is up and opens without a login | Opens the preview (the Open icon) |
| Building | Blue | Vercel has not finished the preview, or it failed | Nothing |
| Login needed | Red | The preview is up but Vercel asks for a login: Deployment Protection is on for previews | Opens the preview |

There is no description line (no APK, so no age), and the status is centred in the height of both lines, like the other pills with no APK.

### The production pill

A website has no main build either, so in the place of the main build's pill (the row of buttons at the top of its card, and of its page) it has the
**Production** pill (`productionControl` in `ui/HomePageContent.kt`): purple, as the deployment is live and can be acted on, and a press on its icon
(the Open icon), or on the row in its sheet, opens the production deployment. The URL is the **`productionUrl`** of the website's entry in `apps.json` of ci-workflows (`docs/next-js-app.md` there); a website
whose entry has none shows no pill. It does not need the token, as the file is already read for the list of apps.

## The two lines

Every pill lays out the same two lines, each exactly one line tall (ellipsized if too long), so all pills are
equally tall:

1. **Status**: the state, in the status colour ("Ready", "Stale build", "No build").
2. **Description**: depends on the status. By default how old the APK is ("2 minutes old"); while downloading, how far the download is ("42%",
   or "3.2 MB" when the size is unknown), and the status reads "Downloading…".

With no APK (No build, Building, Build failed) there is no description and the status is centred in the height of both lines.

## The bottom sheet

A press on the text side of a pill (left of the divider, not an action icon) always opens a bottom sheet and
never does an action, whatever the status and however many actions there are. The sheet shows:

1. The status as its title, and what the description says ("42%") when it says anything.
2. When the APK was built, relative ("2 minutes old") and absolute ("23/10/2026 18:27:11"), when known.
3. Every action of the pill (including cancelling a download), each a row with its icon and a short description
   of what it does. A press anywhere on a row runs the action and closes the sheet.

Pills with no APK (No build, Building, Cancelled…) open the sheet too, with just the status and the actions
they have, if any. The pill's own action icons still run their action with one press.

## The main build

The main build of a repo sits in the row of buttons at the top of its card, after the GitHub and Claude buttons. It is the
**latest build of `main`**: the APK of the release `main-build`, which the CI of the repo replaces on every push to `main`. A
repo without that release has no main build and shows nothing. AppHub never starts a build, and does not say how far the build
is behind `main`: it is always the newest one the CI published. It uses the same pill, from `controlOf(MainBuildItem)`, with
only these states:

- **Ready** (purple): the latest main build is not installed.
- **Update ready** (purple): another build of the app is installed, so this one is an update.
- **Installed** (green) means the installed build is the latest main build. A push to `main` makes the next build a different
  one, so it shows Update ready again.
- Downloading and Download failed are those above. The description is the age of the build.

## The trailing icon

| Icon | Press does | When |
|---|---|---|
| Download | Downloads the APK, then installs it | An APK is ready and not installed, or an update or stale build |
| Install | Installs the APK already downloaded | Downloaded but not installed |
| Open | Starts the installed app | Installed |
| Circular arrow | Downloads again | **Retry** after a failed download, in red |
| Open | (second action) Opens the run that is building the replacement | Stale build whose replacement is building: the first action downloads or installs the stale APK |
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
