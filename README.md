# AppHub

An Android app that lists the open pull requests of my public GitHub repos, with each PR's debug APK and
web test deploy, so I can install and try a PR build straight from my phone.

- **Lists** the open PRs of every public repo of `LukeNeedham` (one search call), grouped by repo. A PR
  that is merged or closed drops off the list on the next refresh (pull down), and its downloaded APK is
  deleted.
- **Collapsible projects.** Each repo is a card that collapses to its header; a PR shows only its title,
  a status tag and its actions: download/install, Web, Claude session, Open PR.
- **Reads the PR's comments**: the APK link comes from the shared CI comment
  ([ci-workflows](https://github.com/LukeNeedham/ci-workflows)), the web link from a deploy comment on a
  known preview host (Netlify, Vercel, Cloudflare Pages, Firebase, GitHub Pages and so on; see
  `CommentParser.previewHosts`).
- **Downloads and installs the APK itself**, with progress and a state (downloading, downloaded, failed),
  then opens the system installer. "Open PR" opens the PR in the browser (or the GitHub app, if it handles links); "Claude session" opens the Claude Code session linked in the PR
  description or comments, if there is one. Settings (top right) leads to the Debug page in debug builds. The first time, Android asks to allow AppHub to install apps.
- **A page per PR.** "Details" on a PR opens its own page: title, number, author, status, the same actions
  (download/install, Web, Claude session, Open PR) and the PR description.
- **Deep link.** `https://lukeneedham.com/apphub-9c4f1e/PR/<repo name>/<number>` (for example
  `https://lukeneedham.com/apphub-9c4f1e/PR/AppHub/10`) opens that PR's page. The repo is in the link because PR numbers
  repeat across repos; the owner is always `LukeNeedham`. The shared CI is meant to put this link in its
  PR comment, so it can be handed straight to whoever wants to install the build.
- **Stale builds.** While a newer commit is rebuilding, the card still offers the previous APK, marked as out
  of date (the CI comment on the PR does the same).
- **Says whether the PR's build is installed.** Debug builds share a version number, so AppHub remembers
  which commit it installed for each app and when; a later change to the installed app (another build)
  makes it read "a different build is installed".

## Limits

- Sign-in is optional. Without a token only public repos are visible and GitHub allows 60 anonymous
  requests an hour per IP address (a shared Wi-Fi, VPN or mobile network shares it). With a personal access
  token (Settings → GitHub; a fine-grained token with "Public repositories (read-only)" and nothing else) it
  is 5,000. The token is checked with GitHub before it is kept, stored encrypted with an Android Keystore
  key, and sent only to `api.github.com`.
- A refresh is one request to list the open PRs, plus one per PR whose `updated_at` changed since the last
  refresh (a new or edited comment bumps it); the parsed PRs are kept on disk. When the limit is hit the app
  keeps showing the last list and says when to retry.
- The "installed" check only knows about installs done through AppHub, and learns an app's package name
  from the first APK downloaded for its repo.
- A download stops if the app is closed.

## Making the deep link open the app

Android only opens an `https` link in the app without asking when the domain vouches for it. Serve
`https://lukeneedham.com/.well-known/assetlinks.json` (no redirect, `application/json`) with the app's
package and the SHA-256 of the signing certificate (the committed `app/debug.keystore` for the CI builds):

```json
[{
  "relation": ["delegate_permission/common.handle_all_urls"],
  "target": {
    "namespace": "android_app",
    "package_name": "com.lukeneedham.apphub",
    "sha256_cert_fingerprints": ["8F:A2:30:33:62:EC:9E:5F:95:9C:B5:C7:1B:D6:0A:FE:08:00:F5:D9:01:67:19:7C:75:54:6D:28:79:2E:AC:AC"]
  }
}]
```

Until then the link opens in the browser; the app can still be chosen under Settings → Apps → AppHub →
Open by default → Add link. Try a link with
`adb shell am start -a android.intent.action.VIEW -d https://lukeneedham.com/apphub-9c4f1e/PR/AppHub/10`.

## Development

See `CLAUDE.md` and the `lukeneedham-conventions` skill. Every PR builds a debug APK and comments the
download link.
