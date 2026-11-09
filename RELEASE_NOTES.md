# What's New in v1.4.1

### New Features
- **CDN-Served Trending** - the discovered apps list now loads from a global CDN instead of GitHub's plain file server, so the Trending tab opens faster everywhere
- **Offline Fallback** - the last successfully loaded list is kept on device for up to 48 hours, so Trending still renders when you have no connection instead of showing an empty screen
- **Saved Results Notice** - a banner makes it clear when you are browsing saved data, shows when that list was last updated, and offers a retry
- **Background Downloads** - APK downloads keep going after you leave a repo page; progress and completion show in a notification you can tap to open the repo
- **Smarter Search Sort** - Sort By options are sent to GitHub's API, name matches are boosted after APK verification, and the sheet uses the same radio-row layout as the rest of the app

### Improvements
- Trending fetch errors no longer fall through to the live GitHub API more than necessary
- Reduced logging noise on the trending fetch path
- All seven UI locales include the new saved-results notice
- Offline screens across Home, Search, Installed Apps and Developer Profile now share one clear WiFi empty state
- The app retries automatically when the connection comes back, instead of requiring a manual refresh
- New and Popular no longer show a misleading "no repositories" message while offline
- Trending, New and Popular can be switched at any time, including while another list is still loading
- Trending, New and Popular paint their saved list from disk before touching the network, so tabs switch instantly
- Saved lists are written after each verified batch, so leaving a tab mid-load still caches what was shown
- Offline, all three tabs show their saved list with the saved-results notice instead of an empty screen
- Saved lists refresh at most once a day, or immediately when you tap Retry
- Downloads cards show the repo name, open Details on tap, and use clearer Cancel / Install / Delete actions
- Downloads cards now show the repo avatar, app name and APK file name, with a clearer install icon
- Downloads falls back to the repo initial when a saved download has no avatar, instead of the app logo
- The download notification icon is bolder and easier to read
- Details shows **Install downloaded** when the APK is already on disk, and **Reinstall** after an install from Downloads or Details
- Install and Update actions use simple Download / Upgrade icons
- Dark theme no longer flashes light on cold start (night mode is applied before the first frame)

### Bug Fixes
- Fixed a lost trailing space in the German "More Apps by Darkmintis" label that could join two words together
- Restored full 281-string parity across all seven locales
- "Update all" now reports the correct number of successfully updated apps
- Fixed a rare crash when a finished download reported no local file path
- The in-app updater no longer offers the version you are already running
- The offline banner no longer overlaps the status bar or double-pads top app bars
- "Install downloaded" no longer appears while an APK is still downloading; partial or corrupt files are removed instead of failing at the system installer
- Status bar icon contrast now follows the app theme in dark mode
- SearchViewModel tests no longer race `Dispatchers.IO` against `resetMain()` in CI

### Under the Hood
- Trending seed list now resolves through `CachedTrendingDataSource.loadTrending()`, returning a typed result that distinguishes offline from missing data
- Disk cache stored in `cacheDir/trending_apps.json`, reclaimable by the OS without losing authoritative data
- Saved snapshots older than 48 hours are discarded so stale data is never presented as current
- DownloadManager jobs survive ViewModel teardown; app notifications deep-link via `gitstore://repo/{id}`
- Release version name and code are derived from the git tag in CI; local builds fall back to the values in `app/build.gradle.kts`
