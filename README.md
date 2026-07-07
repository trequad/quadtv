# QuadTV

QuadTV is a private Android TV IPTV client and companion admin portal.

## Repository layout

```text
android-app/      Native Kotlin Android TV client
admin-portal/    FastAPI admin portal and web UI
scripts/         Local operator scripts
```

## Components

- **Android app:** Kotlin/Gradle Android TV client with embedded playback, profiles, Live TV, VOD, Jellyfin-style media browsing, update checks, and responsive TV/phone/tablet UI work.
- **Admin portal:** FastAPI backend with a simple web UI for users, devices, profiles, app config, provider-feed resolution, and release metadata.
- **Scripts:** helper tooling for local builds and private beta publishing. Deployment-specific targets should be provided via CLI flags or environment variables, not committed defaults.

## Safety notes

This public README intentionally omits live hostnames, internal IPs, credentials, API keys, customer data, deployment paths, and operator handoff notes.

Do not commit:

- `.env` files
- APK signing keys
- provider credentials
- admin portal credentials
- customer exports/databases
- local deployment handoff documents

## Local development

Backend tests, from `admin-portal/`:

```bash
./.venv/bin/pytest -q
```

Android debug build, from `android-app/`:

```bash
./gradlew --no-daemon :app:assembleDebug
```
