# AppHub

An Android app that lists the open pull requests of my public GitHub repos, with each PR's debug APK and
web test deploy, so I can install and try a PR build straight from my phone.

- **Lists** the open PRs of every public repo of `LukeNeedham` (one search call), grouped by repo. A PR
  that is merged or closed drops off the list on the next refresh (pull down), and its downloaded APK is
  deleted.
- **Reads the PR's comments**: the APK link comes from the shared CI comment
  ([ci-workflows](https://github.com/LukeNeedham/ci-workflows)), the web link from a deploy comment on a
  known preview host (Netlify, Vercel, Cloudflare Pages, Firebase, GitHub Pages and so on; see
  `CommentParser.previewHosts`).
- **Downloads and installs the APK itself**, with progress and a state (downloading, downloaded, failed),
  then opens the system installer. The first time, Android asks to allow AppHub to install apps.
- **Says whether the PR's build is installed.** Debug builds share a version number, so AppHub remembers
  which commit it installed for each app and when; a later change to the installed app (another build)
  makes it read "a different build is installed".

## Limits

- No sign-in: only public repos, and GitHub allows 60 anonymous requests an hour. Responses are cached
  with ETags (a revalidated `304` is free), but a very large number of open PRs can still hit the limit;
  the app then says when to retry.
- The "installed" check only knows about installs done through AppHub, and learns an app's package name
  from the first APK downloaded for its repo.
- A download stops if the app is closed.

## Development

See `CLAUDE.md` and the `lukeneedham-conventions` skill. Every PR builds a debug APK and comments the
download link.
