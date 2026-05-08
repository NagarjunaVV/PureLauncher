# PureLauncher

PureLauncher is a role-based Android launcher and parent monitoring app built around a child launcher experience and a parent dashboard. The current codebase focuses on launcher control, vault friction, telemetry sync, and QR-based parent-child linking.

## What Is Implemented

The current build includes a child launcher with three tabs, a parent dashboard with Firestore-backed live updates, an app drawer for vaulting apps, a friction gate, usage analytics, and QR pairing. App widgets are not implemented. Notification tracking is not implemented in the current codebase, so the README no longer describes it as an active feature.

## Main User Flows

### Child Flow

1. `MainActivity` checks onboarding and session state.
2. `OnboardingActivity` lets the user choose Child mode.
3. `PersonalFeatureTourActivity` shows the child feature tour.
4. `PersonalPermissionsActivity` requests the required system permissions.
5. `AuthenticationActivity` routes to `LoginActivity` or signup as needed.
6. `LauncherActivity` opens the child launcher with three tabs: Home, Vault, and Settings.
7. The Vault tab opens the app drawer, where apps can be searched and added to the vault.
8. Vaulted apps open through the friction gate or the limit-reached flow.

### Parent Flow

1. `MainActivity` checks onboarding and session state.
2. `OnboardingActivity` lets the user choose Parent mode.
3. `ParentFeatureTourActivity` shows the parent feature tour.
4. `LoginActivity` and `SignupActivity` handle Firebase authentication.
5. `ParentLinkChildActivity` and `ParentQrScannerActivity` link the parent to the child by QR code.
6. `ActivityParentDashboardActivity` shows three tabs: Home, Vault, and Settings.
7. Firestore listeners update the parent dashboard, vault list, and child telemetry in real time.
8. The parent can monitor usage and manage vault friction and limits from the dashboard.

## Child Experience In Detail

### Launcher Tabs

The child launcher is implemented in `LauncherActivity` and uses three tabs only:

| Tab      | File                                                                                 | Purpose                                                      |
| -------- | ------------------------------------------------------------------------------------ | ------------------------------------------------------------ |
| Home     | `LauncherActivity`, `view_launcher_home_panel.xml`                                   | Shows time, basic telemetry, and launcher shortcuts.         |
| Vault    | `LauncherActivity`, `view_launcher_vault_panel.xml`, `AppSearchActivity`             | Shows vaulted apps, app search, and the vault friction flow. |
| Settings | `LauncherActivity`, `view_launcher_qr_panel.xml`, `view_launcher_settings_panel.xml` | Shows launcher settings and the child QR pairing area.       |

### Vault Flow

The vault experience is built to slow down app opening:

1. The app drawer is opened from the Vault tab or the add-to-vault controls.
2. `AppSearchActivity` lists launchable apps and supports search plus letter indexing.
3. Long-pressing an app shows add/remove-from-vault options.
4. Vaulted apps are stored in `VaultPrefs` and removed from the normal app drawer list.
5. Opening a vaulted app triggers `DialogFrictionGateActivity` unless a valid unlock state exists.
6. If an app has a daily limit, `LimitReachedActivity` blocks it when the limit is reached.
7. Vault friction and limit state are synced and enforced through the local vault data and usage guard service.

### Child Permissions

The child flow currently uses the permissions needed for launcher and overlay behavior. The code requests system overlay, default launcher, and usage access where required. Notification permission is not part of the implemented feature set described here, because notification tracking is not currently implemented.

## Parent Experience In Detail

### Dashboard Tabs

`ActivityParentDashboardActivity` drives the parent side and exposes three tabs:

| Tab      | File                                                                | Purpose                                                                  |
| -------- | ------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| Home     | `ActivityParentDashboardActivity`, `view_parent_home_panel.xml`     | Shows the child's current metrics and screen-time chart.                 |
| Vault    | `ActivityParentDashboardActivity`, `view_parent_vault_panel.xml`    | Lists vaulted apps and lets the parent update friction and daily limits. |
| Settings | `ActivityParentDashboardActivity`, `view_parent_settings_panel.xml` | Handles parent settings, logout, and remove-link actions.                |

### Monitoring And Controls

The parent dashboard is updated from Firestore and local telemetry snapshots. The Home tab displays screen-time totals, unlock count, friction count, vaulted count, and chart data. The Vault tab shows apps currently vaulted for the child and lets the parent:

1. Add or remove apps from the child vault.
2. Change the friction level for a vaulted app.
3. Set or update a daily limit for a vaulted app.
4. Send sync requests so the child launcher refreshes its local state.

The parent dashboard listens to Firestore collections so changes from the child device and changes made by the parent remain aligned.

## Data Structures

### Local Snapshot And Usage Data

| Structure                                          | Defined In                             | What It Stores                                                                       |
| -------------------------------------------------- | -------------------------------------- | ------------------------------------------------------------------------------------ |
| `TelemetrySnapshot`                                | `TelemetrySnapshot.java`               | Aggregated local telemetry used for sync and dashboard display.                      |
| `TelemetryRepository.AppUsageEntry`                | `TelemetryRepository.java`             | Per-app usage for a specific day, including package name, minutes, and launch count. |
| `ActivityParentDashboardActivity.ParentAppEntry`   | `ActivityParentDashboardActivity.java` | Parent-side app list entry with label and package name.                              |
| `ActivityParentDashboardActivity.ParentVaultEntry` | `ActivityParentDashboardActivity.java` | Vault entry with friction, daily limit, and limit-change metadata.                   |
| `LauncherActivity` vault/app lists                 | `LauncherActivity.java`                | Cached app lists for the child drawer and vault pages.                               |

### TelemetrySnapshot Fields

`TelemetrySnapshot` contains the values that are written into Firestore and shown in the UI:

| Field                      | Meaning                                          |
| -------------------------- | ------------------------------------------------ |
| `weeklyScreenTimeMinutes`  | Total screen time for the latest 7-day window.   |
| `dailyUsageMinutes`        | 7-day array of daily usage totals.               |
| `monthlyScreenTimeMinutes` | Total screen time for the latest 30-day window.  |
| `monthlyUsageMinutes`      | 30-day array of daily usage totals.              |
| `unlockCount`              | Number of device unlocks today.                  |
| `frictionCount`            | Number of friction events tracked locally today. |
| `vaultedCount`             | Count of apps currently in the vault.            |

### SharedPreferences Keys

| Preference File                | Used For                                                                                                      |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------- |
| `purelauncher_session`         | Onboarding completion, selected role, tour completion, permission completion, and child authentication state. |
| `purelauncher_vault`           | Vaulted package list, friction type, app limits, and last-unlocked package state.                             |
| `purelauncher_telemetry_local` | Local telemetry counters such as friction counts.                                                             |
| `child_sync_prefs`             | Child sync request tracking and last sync request ID.                                                         |
| `parent_sync_prefs`            | Parent dashboard sync cooldown tracking.                                                                      |
| `launcher_ui_prefs`            | Theme and font-size settings.                                                                                 |

## Firebase And Firestore

### Firebase Services Used

| Service                 | Used In                                                                                                                                            | Purpose                                                                              |
| ----------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------ |
| Firebase Authentication | `LoginActivity`, `SignupActivity`, `AuthenticationActivity`, `MainActivity`, `ActivityParentDashboardActivity`, `LauncherActivity`                 | Signs users in and routes them to the correct role-based flow.                       |
| Cloud Firestore         | `UserProfileStore`, `SyncCoordinator`, `ActivityParentDashboardActivity`, `ParentScreenTimeActivity`, `ParentLinkChildActivity`, `ChildQrActivity` | Stores user profiles, linking state, vault data, usage telemetry, and sync requests. |

### Firestore Collections

| Collection / Path                      | Used By                                                                          | Purpose                                                                                |
| -------------------------------------- | -------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| `users/{uid}`                          | `UserProfileStore`                                                               | Stores role, email, display name, active session device, and parent-child link fields. |
| `users/{uid}.linkedChildUid`           | `UserProfileStore`, `MainActivity`, `ActivityParentDashboardActivity`            | Stores the linked child account for the parent.                                        |
| `users/{uid}.linkedParentUid`          | `UserProfileStore`, `MainActivity`, `LauncherActivity`                           | Stores the linked parent account for the child.                                        |
| `child_link_tokens/{uid}`              | `ChildQrActivity`, `ParentLinkChildActivity`                                     | Temporary QR link token flow used during pairing.                                      |
| `child_metrics/{uid}`                  | `SyncCoordinator`, `ActivityParentDashboardActivity`, `ParentScreenTimeActivity` | Stores aggregated child telemetry shown on parent screens.                             |
| `child_apps/{uid}/apps/{packageName}`  | `SyncCoordinator`, `ActivityParentDashboardActivity`                             | Stores the child app catalog for parent vault and monitoring screens.                  |
| `child_vault/{uid}/apps/{packageName}` | `SyncCoordinator`, `ActivityParentDashboardActivity`, `LauncherActivity`         | Stores vaulted apps, friction level, and app limit values.                             |
| `child_usage/{uid}/days/{yyyy-MM-dd}`  | `SyncCoordinator`, `ParentScreenTimeActivity`                                    | Stores day-by-day app usage and per-app usage rows.                                    |
| `sync_requests/{uid}`                  | `SyncCoordinator`, `AppUsageGuardService`, `ActivityParentDashboardActivity`     | Used to trigger and acknowledge child syncs from the parent side.                      |

### Firebase File Responsibilities

| File                                   | Responsibility                                                                                                 |
| -------------------------------------- | -------------------------------------------------------------------------------------------------------------- |
| `UserProfileStore.java`                | Creates user profiles, stores role, links and unlinks parent-child accounts, and manages session device state. |
| `SyncCoordinator.java`                 | Builds child metrics, app lists, vault state, and daily usage snapshots in Firestore.                          |
| `ChildQrActivity.java`                 | Generates the child QR code and stores the child token data for linking.                                       |
| `ParentLinkChildActivity.java`         | Links the parent to the child after QR scan or token entry.                                                    |
| `ParentQrScannerActivity.java`         | Scans the child QR code using the camera or gallery.                                                           |
| `ActivityParentDashboardActivity.java` | Reads Firestore live updates for the dashboard and pushes vault updates back to Firestore.                     |
| `ParentScreenTimeActivity.java`        | Reads detailed child usage documents for the parent screen-time view.                                          |
| `AppUsageGuardService.java`            | Watches sync requests and triggers local updates for the child launcher.                                       |

## Activity And Java File Map

### Routing And Session

| File                          | What It Does                                                                        |
| ----------------------------- | ----------------------------------------------------------------------------------- |
| `MainActivity.java`           | Routes the user into the correct flow based on onboarding, role, and session state. |
| `OnboardingActivity.java`     | Lets the user choose Parent or Child mode.                                          |
| `SetupActivity.java`          | Short setup/preload screen used during startup.                                     |
| `SessionPrefs.java`           | SharedPreferences helper for onboarding, role, and flow flags.                      |
| `AuthenticationActivity.java` | Chooses between login, signup, or child auth routing.                               |

### Child Launcher And Child Tools

| File                              | What It Does                                                                           |
| --------------------------------- | -------------------------------------------------------------------------------------- |
| `LauncherActivity.java`           | Main child launcher with Home, Vault, and Settings tabs.                               |
| `AppSearchActivity.java`          | Searchable app drawer used to add or remove apps from the vault.                       |
| `AppSearchAdapter.java`           | RecyclerView adapter for the searchable app list.                                      |
| `AppVaultActivity.java`           | Dedicated vault management screen for vaulted apps.                                    |
| `DialogFrictionGateActivity.java` | Shows the intentional friction gate before opening vaulted apps.                       |
| `LimitReachedActivity.java`       | Blocks access when an app daily limit is exhausted.                                    |
| `ScreenTimeActivity.java`         | Shows per-day usage details and app-level screen-time history.                         |
| `TelemetryRepository.java`        | Reads Android usage stats and builds local telemetry snapshots.                        |
| `TelemetrySnapshot.java`          | Immutable local telemetry payload used by sync and UI layers.                          |
| `TelemetryLocalStore.java`        | Stores local telemetry counters in SharedPreferences.                                  |
| `VaultPrefs.java`                 | Stores vaulted packages, friction settings, and app limits.                            |
| `AppUsageGuardService.java`       | Keeps the child device in sync and enforces vault or limit behavior in the background. |
| `AppIconCache.java`               | Caches app icons for drawer and list rendering.                                        |
| `FrequentAppsAdapter.java`        | Adapter used by launcher lists that surface frequently used apps.                      |
| `SimpleTextWatcher.java`          | Small text-watcher helper for search fields.                                           |

### Parent Dashboard And Parent Tools

| File                                   | What It Does                                                                       |
| -------------------------------------- | ---------------------------------------------------------------------------------- |
| `ActivityParentDashboardActivity.java` | Parent home, vault, and settings tabs plus Firestore live sync and vault controls. |
| `ParentFeatureTourActivity.java`       | Parent onboarding tour.                                                            |
| `ParentLinkChildActivity.java`         | Parent linking flow and QR pairing handoff.                                        |
| `ParentQrScannerActivity.java`         | Camera and gallery based QR scanner for linking.                                   |
| `ParentScreenTimeActivity.java`        | Detailed parent analytics screen for child usage.                                  |
| `PolicyManagerActivity.java`           | Policy management UI scaffold.                                                     |
| `UsageRestrictActivity.java`           | Usage restriction UI scaffold.                                                     |
| `GlobalSettingsActivity.java`          | Global app settings entry point.                                                   |
| `ItemAppRowActivity.java`              | Row item editor/helper used by settings and policy screens.                        |
| `ItemCategoryLimitActivity.java`       | Category-based limit UI helper.                                                    |
| `ItemPolicyAppActivity.java`           | Policy app configuration UI helper.                                                |
| `ItemSettingsToggleActivity.java`      | Settings toggle row helper.                                                        |

### Shared Helpers And Utilities

| File                       | What It Does                                               |
| -------------------------- | ---------------------------------------------------------- |
| `UserProfileStore.java`    | Firestore profile, linking, and active-session operations. |
| `SyncCoordinator.java`     | Child telemetry writer and Firestore sync builder.         |
| `QrCodeUtils.java`         | QR generation and decoding helper.                         |
| `PasswordValidator.java`   | Password rules and validation helper.                      |
| `NameValidator.java`       | Display-name validation helper.                            |
| `NetworkUtils.java`        | Connectivity checks for flows that require internet.       |
| `LauncherUiPrefs.java`     | Theme, font-size, and UI preference management.            |
| `RequiredPermissions.java` | Checks whether the child launcher permissions are granted. |
| `AppIconCache.java`        | Shared icon cache for app listings.                        |

## Build Status

- Verified build: `:app:assembleDebug`
- Current workspace branch: `main`

## Build And Run

### Android Studio

1. Open the project root in Android Studio.
2. Let Gradle sync complete.
3. Build and run the app on a device or emulator.

### Command Line

```powershell
.\gradlew.bat :app:assembleDebug
```

Expected output: `BUILD SUCCESSFUL` and the APK under `app/build/outputs/apk/debug/`.

## Notes

- The README no longer describes widgets as implemented because the current codebase does not provide them.
- The README no longer describes notification telemetry as implemented because the current codebase does not provide it.
- The child launcher is intentionally split into Home, Vault, and Settings only.
