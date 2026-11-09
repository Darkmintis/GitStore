# What's New in v1.4.1

A faster, more reliable Git Store: tabs open from a saved list, downloads keep running in the background, and progress stays in sync everywhere.

### Highlights
- **Instant tabs** - Trending, New and Popular paint their saved list first, so switching is immediate after the first load. Lists refresh once a day, or when you tap Retry.
- **Background downloads** - installs keep running when you leave the app or restart it. The notification shows live progress and opens the repo when tapped.
- **Reliable downloads** - "Install downloaded" only appears when the APK is complete; partial or corrupt files are removed instead of failing at the system installer.
- **Clearer Downloads** - repo avatar, app name and APK filename, with a clear Install button.
- **Search sorting** - Most Stars, Most Forks and Best Match are sent to GitHub, and exact or prefix name matches rank first.

### Improvements
- Offline, all three tabs keep their saved list, show when it was saved, and offer Retry.
- Offline screens across Home, Search, Installed Apps and Developer Profile share one WiFi empty state and retry automatically when the connection returns.
- New and Popular no longer show a misleading "no repositories" message while offline.
- Trending now loads from the jsDelivr CDN instead of GitHub's file server, so it opens faster.
- Search is more name-focused, with fewer false matches from README-only mentions.
- Details shows **Install downloaded** when the APK is on disk and **Reinstall** after installing.
- Dark mode applies before the first frame, and status bar icons follow the theme.
- Android 13+ asks for notification permission so download notifications can appear.
- The download notification icon is bolder and easier to read.
- All seven UI locales are complete (286 strings each).

### Bug Fixes
- Download progress stays in sync between Details, the Downloads screen and the notification, and resumes after the app is reopened instead of freezing at the last percentage.
- "Install downloaded" no longer appears while an APK is still downloading.
- "Update all" reports the correct number of successful updates.
- The in-app updater no longer offers the version you are already running.
- Fixed a rare crash when a finished download reported no local file path.
- The offline banner no longer overlaps the status bar or double-pads top app bars.
- Downloads uses the repo avatar (or its initial) instead of the GitStore logo.
- Fixed a lost trailing space in the German "More Apps by Darkmintis" label.

### Under the Hood
- Saved lists and the Trending snapshot live in app storage, not the OS cache, so a cache trim cannot delete them.
- Saved lists are written after each verified batch, so leaving a tab mid-load still caches what was shown; Trending snapshots older than 48 hours are discarded.
- Active downloads are reconciled on app start, so notifications and progress survive a process kill.
- DownloadManager jobs survive ViewModel teardown; notifications deep-link via `gitstore://repo/{id}`.
- Release version name and code come from the git tag in CI; local builds fall back to `app/build.gradle.kts`.
- CI no longer races `Dispatchers.IO` against the test dispatcher in SearchViewModel tests.
