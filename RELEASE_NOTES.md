# What's New in v1.4.1

### New Features
- **CDN-Served Trending** - the discovered apps list now loads from a global CDN instead of GitHub's plain file server, so the Trending tab opens faster everywhere
- **Offline Fallback** - the last successfully loaded list is kept on device for up to 48 hours, so Trending still renders when you have no connection instead of showing an empty screen
- **Saved Results Notice** - a banner makes it clear when you are browsing saved data, shows when that list was last updated, and offers a retry

### Improvements
- Trending fetch errors no longer fall through to the live GitHub API more than necessary
- Reduced logging noise on the trending fetch path
- All seven UI locales include the new saved-results notice

### Bug Fixes
- Fixed a lost trailing space in the German "More Apps by Darkmintis" label that could join two words together
- Restored full 281-string parity across all seven locales
- "Update all" now reports the correct number of successfully updated apps
- Downloads are now cleaned up when you leave the screen instead of leaving partial APKs behind
- Fixed a rare crash when a finished download reported no local file path
- The in-app updater no longer offers the version you are already running

### Under the Hood
- Trending seed list now resolves through `CachedTrendingDataSource.loadTrending()`, returning a typed result that distinguishes offline from missing data
- Disk cache stored in `cacheDir/trending_apps.json`, reclaimable by the OS without losing authoritative data
- Saved snapshots older than 48 hours are discarded so stale data is never presented as current
- Install/uninstall cleanup runs on a scope that survives the ViewModel, so cancellation cannot strand files
- Release version name and code are derived from the git tag in CI; local builds fall back to the values in `app/build.gradle.kts`
