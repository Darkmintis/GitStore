# Assets

Repository-only images used by the README and GitHub, not packaged into the APK.

- `brand/` — logo sources and the app icon (README header, press use)
- `screenshots/` — screenshots used in the README

The app's runtime icons live in `app/src/main/res/` (`mipmap/` for the launcher,
`drawable/` for in-app icons). Files bundled into the APK belong in
`app/src/main/assets/`, not here.
