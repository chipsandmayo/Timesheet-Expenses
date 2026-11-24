# Timesheet & Expenses

Android app (Kotlin + Jetpack Compose) that fills in the Elevate Offshore **timesheet** and **travel expense claim form**.

**Download the latest APK:** https://github.com/chipsandmayo/Timesheet-Expenses/releases/latest/download/TimesheetExpenses.apk

After the first install, use **Settings → Check for updates / Update app** — no new links needed.

## Features
- **Profiles** – client, consultant, position, vessel, project, authoriser, location code, address, signature, per-mile rate. Switch from the top bar.
- **Timesheet** – pick a start and end date: first/last day go in *Travel*, days in between in *Work offshore*. Tap a day to change it (offshore → travel → onshore → standby → empty). Location code and remarks.
- **Expenses** – photograph a receipt; the app reads it (on-device ML Kit), lists the amounts it found (likely totals starred), asks if it's GBP and, if not, which currency, then converts using the rate for the receipt date (ECB via Frankfurter, fallback currency-api).
- **Saving** – files go to `Documents/Timesheet Expenses/<Mon-YYYY>/`, named
  `<Consultant> <Mon-YYYY> Timesheet.xlsx`, `… Expenses.xlsx`, `… Photo 01.jpg`.
- **Save & email** – one tap attaches the timesheet, expenses and receipt photos to an email.

## Build
Every push to `main` builds a signed APK in GitHub Actions and publishes it as the latest release.
The signing key is in `keystore/` so every build has the same signature (needed for in-place updates).
