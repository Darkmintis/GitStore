# What's New in v1.5.0

Trending data moves to a dedicated free CDN repo, Downloads gains full app management, search is faster and wider, and the app can prompt when a new Git Store release is out.

### Highlights
- **GitStore-data** - Trending loads from `Darkmintis/GitStore-data` via jsDelivr (search catalog next)
- **Downloads app management** - see installed state, open or uninstall from the Downloads list
- **Faster, wider search** - name + description, forks included, 100 candidates per page, prerelease APKs accepted when needed
- **Update prompt** - on launch, if a newer Git Store release exists: Update / Later / Ignore

### Improvements
- Legacy ≤1.4.x builds keep working: app-repo discovery still fills `cached-data/` until enough users upgrade (see `TODO.md`)
- v1.5 falls back to the legacy CDN URL if the data repo is empty or lagging
- Background downloads, Install downloaded / Reinstall, and notification deep-links from 1.4.x remain

### Under the Hood
- Primary trending URL: `cdn.jsdelivr.net/gh/Darkmintis/GitStore-data@main/trending`
- Discovery still runs in this app repo for old clients; GitStore-data runs its own discovery for v1.5+
- Local fallback version: **1.5.0** (10500)
