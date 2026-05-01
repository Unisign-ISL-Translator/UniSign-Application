# UniSign - Project Handoff

> This document is intended to help engineers, QA, and product stakeholders pick up, build, run, and maintain the UniSign Android application.

## Checklist
- [ ] Repo cloned and up-to-date
- [ ] Android SDK installed and configured (see Prerequisites)
- [x] Build succeeded locally using Gradle wrapper
- [ ] Debug app installed and launched on a device/emulator
- [ ] Unit and instrumented tests executed
- [ ] Release signing key and Play Store access confirmed
- [ ] Ownership / contacts identified

---

## Project overview

- Project name: UniSign
- Repo root: `C:/Users/Tal/UniSign/UniSign-Application`
- Primary platform: Android (Kotlin / Gradle)
- App package id: `com.unisign.unisign` (see `app/google-services.json`)

Short description:

UniSign is an Android application. The codebase uses the Gradle Kotlin DSL (`*.gradle.kts`) and the Android Gradle Plugin. The app module is located in the `app/` folder.

Current auth / sync flow:

- `LoginScreen.kt` supports email/password login, Google Sign-In, and navigation to signup.
- `SignupScreen.kt` collects email, password, confirm password, and includes a default-on toggle to sync local favorites into the new account.
- `HomeScreen.kt` reflects auth state live, shows `Guest` for anonymous/offline users, and hides sign-out for guests.
- `MainActivity.kt` and `FavoritesSyncManager.kt` coordinate sign-in sign-out sync behavior so remote favorites are loaded on login, while local favorites remain usable offline.

---

## Repository layout (important files / folders)

- `app/` — Android application module (sources, manifests, resources, build files).
- `build.gradle.kts` — project-level Gradle build file (top-level build config).
- `settings.gradle.kts` — Gradle settings.
- `gradle/` — Gradle wrapper files and version catalog.
- `local.properties` — usually contains `sdk.dir` (NOT checked in to public repos; present in workspace here).
- `app/google-services.json` — Firebase / Google services config (present in repository root `app/`).
- `proguard-rules.pro` — ProGuard/R8 rules for release builds.

---

## Prerequisites (local developer setup)

- JDK: Use a supported JDK for the Android Gradle Plugin used by this project. Common choices: AdoptOpenJDK / Temurin 11 or 17. If uncertain, open `gradle-wrapper.properties` and `build.gradle.kts` to verify the AGP and Java compatibility.
- Android Studio: Latest stable recommended (or at least a version compatible with the AGP used in the project).
- Android SDK: Install the SDK platforms and build tools referenced by the project. `local.properties` in the repo usually points to the SDK location on the machine.
- Gradle wrapper: Use the included wrapper — do not use the system Gradle unless necessary.

Environment variables (optional)

- ANDROID_HOME (if needed) — most IDEs use local.properties instead.

---

## Build & Run (Windows PowerShell)

Open a PowerShell in the repository root and run the Gradle wrapper commands below.

Build (debug):

```powershell
.\gradlew.bat :app:assembleDebug
```

Install to a connected device/emulator:

```powershell
.\gradlew.bat :app:installDebug
```

Build release (locally, requires signing config):

```powershell
.\gradlew.bat :app:assembleRelease
```

Run unit tests:

```powershell
.\gradlew.bat test
```

Run instrumented (connected) tests:

```powershell
.\gradlew.bat connectedAndroidTest
```

Open in Android Studio: File → Open → select `C:/Users/Tal/UniSign/UniSign-Application`.

---

## Firebase / Google services

- The file `app/google-services.json` is present in the repository and contains the Firebase / Google configuration for the `com.unisign.unisign` package.
- Google Sign-In is wired through Firebase Auth and uses the generated `default_web_client_id` resource.
- The login screen currently uses the standard Google-style outlined button and launches Google account sign-in from there.
- Do NOT commit new or different `google-services.json` files for production without coordination — they contain API keys and OAuth client IDs tied to the Firebase project.
- If Google Sign-In fails in a real environment, verify Firebase Console OAuth configuration and the app’s SHA-1/SHA-256 fingerprints.

---

## Release signing

- Release builds require a signing keystore. Check for references in `app/build.gradle.kts`, `gradle.properties` or CI/CD configuration for keystore location and passwords.
- If the keystore is not in repo (recommended), the CI or a secure secrets store should provide it during the release pipeline.

---

## Common developer tasks

- Add a new dependency: edit `app/build.gradle.kts` and add the dependency under the appropriate configuration.
- Update Kotlin or AGP: update `gradle/libs.versions.toml` and `gradle/wrapper/gradle-wrapper.properties`, then sync and test thoroughly.
- ProGuard/R8: rules are in `app/proguard-rules.pro`.
- Authentication work currently lives in `AuthManager.kt`, `LoginScreen.kt`, and `SignupScreen.kt`.
- Favorites persistence and sync behavior currently live in `FavoritesRepository.kt` and `FavoritesSyncManager.kt`.

---

## CI / Release pipeline (notes)

- There is no CI config included in the repo root by default. If a pipeline exists externally (GitHub Actions, GitLab CI, Jenkins, etc.), locate it in the organization or ask the current maintainer for access.
- CI should: run lint, unit tests, assemble debug and release, and sign the release using secure secrets.

---

## Tests

- Unit tests: run with `.\gradlew.bat test`.
- Instrumented tests: run with `.\gradlew.bat connectedAndroidTest` (device/emulator required).

---

## Debugging tips

- If Gradle fails to download dependencies, check network/proxy settings and the Gradle home cache.
- Clean build if you see strange incremental build issues:

```powershell
.\gradlew.bat clean :app:assembleDebug
```

- If version conflicts occur, run `.\gradlew.bat :app:dependencies` to inspect dependency trees.

---

## Known issues / TODOs

- Google Sign-In still uses the legacy `GoogleSignIn` API; migrating to Credential Manager / Google Identity would modernize the flow later.
- Sync is best-effort and offline-safe, but conflict resolution is still simple last-write-wins / replace-on-login behavior.
- Consider adding a visible sync status indicator and a manual "Sync now" action for users.
- `app/google-services.json` must stay aligned with the Firebase project and package name.

---

## Ownership and contacts

- Current owner / maintainer: (add name and contact details here)
- Product manager: (add name)
- QA lead: (add name)

---

## How to add your own handoff updates

1. Edit `HANDOFF.md` in the repo root.
2. Add any new environment changes, new credentials locations, or updated build steps.
3. Commit and open a PR describing the change.

---

## Appendix: Quick commands summary

- Build debug: `.\gradlew.bat :app:assembleDebug`
- Install debug: `.\gradlew.bat :app:installDebug`
- Run unit tests: `.\gradlew.bat test`
- Run connected tests: `.\gradlew.bat connectedAndroidTest`

---

End of handoff.

