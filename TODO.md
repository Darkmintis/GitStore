# TODOs

## Remove app-repo discovery (scripts + cached-data)

**Status:** keep until enough ≤1.4.x installs have upgraded  
**Target:** after **v1.7.0** (or ~6 months past v1.5.0, whichever comes first)  
**Owner:** maintainers

### Why it still exists

Pre-1.5 clients hardcode:

`https://cdn.jsdelivr.net/gh/Darkmintis/GitStore@main/cached-data/trending`

Those builds only know this app repo. So until cut-over:

- Leave `scripts/discover.py` and `.github/workflows/auto-discovery.yml` **as they are** (still discover → `cached-data/`).
- Leave `cached-data/` populated.

**v1.5+** reads [GitStore-data](https://github.com/Darkmintis/GitStore-data) first (`GitStoreLinks.DATA_CDN_TRENDING`) and only falls back to the legacy app-repo URL. Discovery for that CDN runs in the data repo (its own scripts + Actions). Do not change app discovery for that — both sides can keep running until removal.

### Until removal

1. Do **not** edit or “fix” app `scripts/discover.py` / Auto Discovery for the data-repo migration.
2. Do **not** delete or empty `cached-data/`.
3. Keep GitStore-data discovery healthy for v1.5+ clients.

### When removing

- [ ] Confirm ≤1.4.x share is negligible (or announce a cut-off).
- [ ] Delete `.github/workflows/auto-discovery.yml`.
- [ ] Delete `cached-data/` (and `cached-data/README.md`).
- [ ] Delete app-repo `scripts/discover.py` (and discovery-only deps in `scripts/requirements.txt` if unused).
- [ ] Drop `GitStoreLinks.LEGACY_CDN_TRENDING` and the legacy fetch fallback in `CachedTrendingDataSource`.
- [ ] Point all docs at GitStore-data only.

### Related

- Data repo: https://github.com/Darkmintis/GitStore-data
- App CDN constants: `GitStoreLinks.DATA_CDN_*` / `LEGACY_CDN_*`
