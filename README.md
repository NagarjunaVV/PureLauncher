# PureLauncher

## Abstract

PureLauncher is a role-based custom Android launcher and monitoring application designed to minimize screen time through a minimalistic design and intentional friction mechanisms.

## Problem Statement

In today's digital age, there is a significant increase in screen time due to addictive apps. Users often find themselves passively and involuntarily opening apps, leading to "doom scrolling" with minimal effort or conscious thought.

## Proposed Solution

PureLauncher addresses this by splitting the device experience into two distinct roles:

### 1. Child / Personal Mode

This mode focuses on minimal distraction and active friction to prevent doom scrolling.

- **Onboarding**: Feature tour highlighting core features (App Vault, Usage Limits, Minimalist Interface, Screen Time Tracking).
- **Permissions**: Multi-step permission wizard requesting System Overlay, Default Home Screen, Usage Access, Notification Access, and Post-Notifications.
- **Authentication**: Email/password signup and login via Firebase Authentication.
- **Minimalist Launcher**: Custom home screen replacing Android default with four pages (Home, Widgets, Vault, QR Code).
- **App Vault**: Hide apps behind intentional friction gates with modal dialogs on access attempts.
- **Screen Time Analytics**: Detailed 7-day usage statistics with per-app breakdowns and notifications tracking.
- **Real-time Telemetry**: Continuous tracking of screen time, notification count, device unlocks, and friction gate triggers synced to Firebase.

### 2. Parent Mode

This mode is designed for guardians to monitor and manage the child's screen time remotely.

- **Onboarding**: Feature tour for parent features (Remote Monitoring, Policy Management, Device Linking, Analytics).
- **Authentication**: Email/password signup and login via Firebase Authentication.
- **Device Linking**: QR code-based pairing linking parent to child device.
- **Live Dashboard**: Real-time monitoring displaying child's weekly screen time, notification counts, unlock counts, friction gate triggers, and vaulted app count with bar chart visualization.
- **Policy Management & Usage Restrictions**: Framework for setting usage limits and time-based app restrictions.

---

## Build Status

- ✅ Verified successful build: `:app:assembleDebug`
- ✅ APK generated: `app-debug.apk` (18.8 MB)
- ✅ Build verified: 2026-04-20
- **Target**: Android 12+ (minSdk 31, targetSdk 36)

## Quick Start Build

### Android Studio

1. Open the project root (`PureLauncher3`) in Android Studio
2. Let Gradle sync complete
3. Click **Build > Build Bundle(s)/APK(s) > Build APK(s)**

### Command Line (Windows PowerShell)

Set Android Studio JBR as Java home, then build:

```powershell
# Option A: One-line with JAVA_HOME set
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug

# Option B: Persistent configuration (already added)
# gradle.properties now contains: org.gradle.java.home=C:\Program Files\Android\Android Studio\jbr
.\gradlew.bat :app:assembleDebug
```

Expected output: `BUILD SUCCESSFUL` with APK at `app/build/outputs/apk/debug/app-debug.apk`

## Java/JDK Requirement

**⚠️ Important**: This project requires Java 11-21 or Android Studio JBR. If you have Java 25+ installed, use Android Studio JBR to avoid Gradle Kotlin DSL errors.

**Why?** Gradle 8.13's Kotlin compiler cannot parse Java 25.0.2 LTS version format.

Check your Java:

```powershell
java -version
```

If Java 25+: Use Android Studio JBR (recommended, already configured in `gradle.properties`)

## Firebase Configuration

**Required**: Add `app/google-services.json` (Firebase configuration file) before building.

- Without it, `:app:processDebugGoogleServices` task will fail
- Obtain from Firebase Console: Project Settings > Your App > google-services.json

---

## Setup Flow & Navigation

The app has a sequential onboarding flow with full back navigation support:

```
┌─────────────────────────────────────────────────┐
│ MainActivity (Router)                           │
│ └─ Checks session state → routes to next screen │
└──────────────┬──────────────────────────────────┘
               │
    ┌──────────▼────────────┐
    │ OnboardingActivity    │
    │ └─ Select role:       │
    │   Parent / Child      │
    └──────────┬────────────┘
               │
    ┌──────────▼─────────────────────────┐
    │ SetupActivity                       │
    │ └─ Preload icons & telemetry       │
    │ └─ Transition delay (2.5 sec)      │
    └──────────┬─────────────────────────┘
               │
    ┌──────────▼─────────────────────────────────────┐
    │ CHILD MODE PATH                                 │
    │                                                 │
    │ PersonalFeatureTourActivity → 4-page swipeable │
    │  tour (App Vault, Limits, Minimalist, Stats)   │
    │ ↓                                               │
    │ PersonalPermissionsActivity → 5-step wizard    │
    │  (System Overlay, Default Home, Usage Access,  │
    │   Notification, Post-Notifications)            │
    │ ↓                                               │
    │ AuthenticationActivity → LoginActivity         │
    │ (Firebase Email/Password Auth)                 │
    │ ↓                                               │
    │ LauncherActivity ⭐ (Main UI)                  │
    │  ├─ Home Page: Clock + Daily/Weekly Telemetry │
    │  ├─ Widgets Page: Customizable Shortcuts       │
    │  ├─ Vault Page: Hidden Apps with Friction     │
    │  └─ QR Code Page: Pairing Display              │
    │                                                 │
    │ Additional Screens:                            │
    │  ├─ AppSearchActivity (App Drawer)             │
    │  ├─ ScreenTimeActivity (7-day Analytics)       │
    │  ├─ AppVaultActivity (Vault Management)        │
    │  └─ DialogFrictionGateActivity (Modal Gate)    │
    └──────────────────────────────────────────────┘
               │
    ┌──────────▼──────────────────────────┐
    │ PARENT MODE PATH                    │
    │                                      │
    │ ParentFeatureTourActivity → 4-page   │
    │  tour (Monitoring, Policy, Linking,  │
    │  Analytics)                          │
    │ ↓                                    │
    │ AuthenticationActivity               │
    │ ├─ LoginActivity (Firebase Auth)     │
    │ └─ SignupActivity (New parent)       │
    │ ↓                                    │
    │ ActivityParentDashboardActivity ⭐  │
    │  ├─ Weekly Screen Time (Bar Chart)   │
    │  ├─ Linked Child Telemetry Display  │
    │  └─ Navigation to Policy Manager     │
    │                                      │
    │ Additional Screens:                 │
    │  ├─ ParentLinkChildActivity         │
    │  │  (QR Scanner for Child Pairing)   │
    │  ├─ PolicyManagerActivity            │
    │  ├─ UsageRestrictActivity            │
    │  └─ GlobalSettingsActivity           │
    └──────────────────────────────────────┘
```

---

## Current Implementation Overview

### Core Architecture

**Role-Based Activity Router Pattern**: The app routes users through different activity flows based on their role (Parent/Child), ensuring role-specific features and UI tailored to each use case.

### Key Components Implemented

#### **Child/Personal Mode Features**

| Feature                   | Component                                             | Status      | Description                                                                      |
| ------------------------- | ----------------------------------------------------- | ----------- | -------------------------------------------------------------------------------- |
| **Minimalist Launcher**   | `LauncherActivity`                                    | ✅ Complete | Custom home screen with 4-page navigation (Home, Widgets, Vault, QR Code)        |
| **App Vault**             | `AppVaultActivity`, `DialogFrictionGateActivity`      | ✅ Complete | Hide apps with intentional friction gates; modal dialogs on access attempts      |
| **Screen Time Analytics** | `ScreenTimeActivity`                                  | ✅ Complete | 7-day usage breakdown with per-app statistics and BarChart visualization         |
| **App Drawer**            | `AppSearchActivity`                                   | ✅ Complete | Full app list with letter-based indexing, search, and icon caching               |
| **Real-time Telemetry**   | `TelemetryRepository`, `NotificationTelemetryService` | ✅ Complete | Track screen time, notifications, unlocks, and friction gates; sync to Firebase  |
| **Feature Tour**          | `PersonalFeatureTourActivity`                         | ✅ Complete | 4-page ViewPager2 onboarding (App Vault, Usage Limits, Minimalist, Stats)        |
| **Permission Wizard**     | `PersonalPermissionsActivity`                         | ✅ Complete | 5-step permission flow (Overlay, Home, Usage, Notifications, Post-Notifications) |
| **Authentication**        | `LoginActivity`, Firebase Auth                        | ✅ Complete | Email/password login via Firebase                                                |
| **QR Pairing Display**    | `ChildQrActivity`                                     | ✅ Complete | Displays child's Firebase UID as QR code for parent linking                      |

#### **Parent Mode Features**

| Feature                 | Component                                        | Status       | Description                                                                               |
| ----------------------- | ------------------------------------------------ | ------------ | ----------------------------------------------------------------------------------------- |
| **Live Dashboard**      | `ActivityParentDashboardActivity`                | ✅ Complete  | Real-time monitoring of child's telemetry (screen time, notifications, unlocks, friction) |
| **Device Linking**      | `ParentLinkChildActivity`                        | ✅ Complete  | QR code-based parent↔child device pairing via camera/gallery scan                         |
| **Bar Chart Analytics** | `BarChartView` (custom)                          | ✅ Complete  | 7-day screen time visualization with touch interaction                                    |
| **Feature Tour**        | `ParentFeatureTourActivity`                      | ✅ Complete  | 4-page ViewPager2 onboarding (Monitoring, Policy, Linking, Analytics)                     |
| **Authentication**      | `LoginActivity`, `SignupActivity`, Firebase Auth | ✅ Complete  | Email/password signup and login via Firebase                                              |
| **Policy Management**   | `PolicyManagerActivity`                          | ⏳ Framework | Setup for app usage policies (UI structure in place)                                      |
| **Usage Restrictions**  | `UsageRestrictActivity`                          | ⏳ Framework | Setup for time-based app locking (UI structure in place)                                  |

#### **Cross-Platform Features**

| Feature                   | Component                                                                              | Status      | Description                                                               |
| ------------------------- | -------------------------------------------------------------------------------------- | ----------- | ------------------------------------------------------------------------- |
| **Firebase Auth**         | Firebase Authentication                                                                | ✅ Complete | Email/password authentication with error handling                         |
| **Firestore Sync**        | `UserProfileStore`, `TelemetryRepository`                                              | ✅ Complete | Real-time database for user profiles, telemetry, and parent-child linking |
| **Role-Based Routing**    | `MainActivity`, `SessionPrefs`                                                         | ✅ Complete | Routes users to appropriate activities based on role                      |
| **Permission Management** | `PersonalPermissionsActivity`                                                          | ✅ Complete | Requests and validates critical system permissions                        |
| **Local Caching**         | SharedPreferences (`SessionPrefs`, `VaultPrefs`, `WidgetPrefs`, `TelemetryLocalStore`) | ✅ Complete | Local storage for session state, preferences, and telemetry buffer        |

### Data Models & Persistence

#### **Firestore Structure**

- **`users`** collection: User profiles with role, linked child/parent UIDs, authentication metadata
- **`users/{uid}/usage_metrics`** subcollection: Daily telemetry snapshots (organized by date)

#### **TelemetrySnapshot** (Data Model)

```
{
  weeklyScreenTimeMinutes: int,      // 7-day total foreground time
  dailyUsageMinutes: int[],           // Per-day breakdown (7 days)
  monthlyScreenTimeMinutes: int,      // 30-day total
  monthlyUsageMinutes: int[],         // Per-day breakdown (30 days)
  notificationCount: int,             // Today's notifications
  unlockCount: int,                   // Device unlocks today
  frictionCount: int,                 // Vault access attempts today
  vaultedCount: int                   // Number of vaulted apps
}
```

#### **Local Storage (SharedPreferences)**

- **`purelauncher_session`**: Onboarding state, role, tour flags, permission flags
- **`purelauncher_vault`**: Vaulted app package set
- **`purelauncher_widgets`**: Widget shortcut ordering
- **`purelauncher_telemetry_local`**: Daily notification/friction counts, per-app metrics (synced to Firestore)

### Custom UI Components

Located in `com.purelauncher.ui.views`:

- **`BarChartView`**: Canvas-based bar chart for 7-day usage visualization with touch interaction
- **`CircularCounterView`**: Canvas-based circular progress indicator (252-degree arc for metrics)

### Key Helper Classes

| Class                          | Purpose                                                               |
| ------------------------------ | --------------------------------------------------------------------- |
| `AppIconCache`                 | Concurrent icon caching with background preload via ExecutorService   |
| `QrCodeUtils`                  | ZXing integration for QR code generation and decoding                 |
| `PasswordValidator`            | Real-time password validation (min 8 chars, letter, number, symbol)   |
| `NotificationTelemetryService` | NotificationListenerService to track notification events in real-time |
| `TelemetryRepository`          | Query Android UsageStatsManager and aggregate usage data              |
| `UserProfileStore`             | Firebase integration for user profile and linking operations          |

### Adapters (RecyclerView)

- **`AppSearchAdapter`**: Searchable app list with letter-based indexing
- **`WidgetsAdapter`**: Two-type adapter for app shortcuts + add button
- **`FrequentAppsAdapter`**: Recent/frequent apps with category labels
- **`AppListAdapter`** (in `ScreenTimeActivity`): Per-app usage breakdown

---

## Java/JDK Requirement

**⚠️ Important**: This project requires Java 11-21 or Android Studio JBR. If you have Java 25+ installed, use Android Studio JBR to avoid Gradle Kotlin DSL errors.

**Why?** Gradle 8.13's Kotlin compiler cannot parse Java 25.0.2 LTS version format.

Check your Java:

```powershell
java -version
```

If Java 25+: Use Android Studio JBR (recommended, already configured in `gradle.properties`)

## Firebase Configuration

**Required**: Add `app/google-services.json` (Firebase configuration file) before building.

- Without it, `:app:processDebugGoogleServices` task will fail
- Obtain from Firebase Console: Project Settings > Your App > google-services.json

---

## Quick Start Build

### Android Studio

1. Open the project root in Android Studio
2. Let Gradle sync complete
3. Click **Build > Build Bundle(s)/APK(s) > Build APK(s)**

### Command Line (Windows PowerShell)

Set Android Studio JBR as Java home, then build:

```powershell
# Option A: One-line with JAVA_HOME set
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'; .\gradlew.bat :app:assembleDebug

# Option B: Persistent configuration (already added)
# gradle.properties now contains: org.gradle.java.home=C:\Program Files\Android\Android Studio\jbr
.\gradlew.bat :app:assembleDebug
```

Expected output: `BUILD SUCCESSFUL` with APK at `app/build/outputs/apk/debug/app-debug.apk`

---

## Dependencies

### Key Libraries

| Library                       | Version     | Purpose                                      |
| ----------------------------- | ----------- | -------------------------------------------- |
| **Firebase Authentication**   | BOM managed | Email/password authentication                |
| **Cloud Firestore**           | BOM managed | Cloud database for user profiles & telemetry |
| **ZXing Core**                | 3.5.3       | QR code generation and scanning              |
| **AndroidX AppCompat**        | Latest      | Compatibility library                        |
| **AndroidX Material**         | Latest      | Material Design components                   |
| **AndroidX ConstraintLayout** | Latest      | Responsive UI layout                         |
| **AndroidX RecyclerView**     | Latest      | Efficient list rendering                     |
| **AndroidX ViewPager2**       | Latest      | Swipeable page navigation                    |

---

## Project Structure

```
PureLauncher_v1/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/
│   │   │   │   ├── example/purelauncher/    # Main app package
│   │   │   │   │   ├── *Activity.java       # All activities
│   │   │   │   │   ├── *Adapter.java        # RecyclerView adapters
│   │   │   │   │   ├── *Prefs.java          # SharedPreferences wrappers
│   │   │   │   │   ├── *Repository.java     # Data access layer
│   │   │   │   │   ├── TelemetrySnapshot.java # Data model
│   │   │   │   │   ├── QrCodeUtils.java     # QR code utilities
│   │   │   │   │   └── *Validator.java      # Input validation
│   │   │   │   └── purelauncher/ui/views/   # Custom UI components
│   │   │   │       ├── BarChartView.java
│   │   │   │       └── CircularCounterView.java
│   │   │   ├── res/
│   │   │   │   ├── layout/                  # Activity & item layouts
│   │   │   │   ├── values/                  # Strings, colors, dimensions
│   │   │   │   └── drawable/                # App icons and assets
│   │   │   └── AndroidManifest.xml
│   │   ├── test/
│   │   └── androidTest/
│   ├── build.gradle.kts
│   ├── google-services.json (required)
│   └── proguard-rules.pro
├── gradle/
│   └── libs.versions.toml                  # Dependency versions
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
└── RULES.md
```

---

## Development Notes

### Adding New Features

1. **Create an Activity**: Extend `AppCompatActivity` in `com.example.purelauncher` package
2. **Add to Manifest**: Register in `AndroidManifest.xml` with appropriate intent filters
3. **Create Layout**: Add XML layout file in `res/layout/`
4. **Navigate**: Use explicit intents or routes from routing activities
5. **Persist Data**: Use appropriate SharedPreferences wrapper or Firestore integration

### Syncing Telemetry

- **Automatic**: `TelemetryRepository.syncCurrentChild()` called on app resume/permissions granted
- **Manual**: Triggered by `NotificationTelemetryService` when notifications arrive
- **Local Buffer**: Stored in `purelauncher_telemetry_local` preferences until synced

### Testing

Run unit tests:

```powershell
.\gradlew.bat test
```

Run instrumented tests on device/emulator:

```powershell
.\gradlew.bat connectedAndroidTest
```

---

## Troubleshooting

| Issue                                           | Solution                                                                                          |
| ----------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| Build fails: "Java 25 LTS version format error" | Ensure `gradle.properties` has `org.gradle.java.home=C:\Program Files\Android\Android Studio\jbr` |
| Firebase integration fails                      | Verify `app/google-services.json` is present and valid from Firebase Console                      |
| QR code scanning fails                          | Grant Camera permission in manifest and ensure device camera is available                         |
| Telemetry not syncing                           | Check Firebase Firestore rules allow read/write for authenticated users                           |
| Permission requests loop                        | Ensure permissions are fully granted in system settings; clear app data and reinstall             |

---

## Core Dependencies

Managed via `gradle/libs.versions.toml`:

- **UI**: `androidx.appcompat`, `com.google.android.material`, `androidx.constraintlayout`
- **Navigation**: `androidx.activity`, `androidx.recyclerview`
- **Firebase**: Firebase Auth (for child authentication)
- **Testing**: JUnit, Espresso

See `app/build.gradle.kts` for full dependency list.

## Build Outputs

### Success

```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/bundle/debug/app-debug.aab
```

### Troubleshooting Build Failures

**Error**: `java.lang.IllegalArgumentException: 25.0.2`

**Solution**: Ensure Android Studio JBR is used:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat clean :app:assembleDebug
```

**Error**: `:app:processDebugGoogleServices` fails

**Solution**: Add `app/google-services.json` from Firebase Console

**Error**: Resource linking fails (`activity_*.xml`)

**Solution**:

1. Check XML namespace attributes (`android:` vs `app:`)
2. Run with stacktrace:

```powershell
.\gradlew.bat :app:processDebugResources --stacktrace
```

3. Verify attribute names in GridLayout children (use `android:layout_column`, not `app:layout_column`)

## Resource Files

### Layouts

- Activities: `activity_*.xml` (onboarding, authentication, launcher, etc.)
- Items: `item_*.xml` (numpad keys, app rows, policy items, toggles)
- Dialogs: `dialog_*.xml`

### Drawables

- Backgrounds: `bg_*.xml`
- Icons: `ic_*.xml`
- PIN states: `pin_dot_*.xml`
- Radio buttons: `rb_*.xml`

## Known Issues & Resolutions

| Issue                      | Cause                           | Resolution                                                    |
| -------------------------- | ------------------------------- | ------------------------------------------------------------- |
| Gradle fails in terminal   | Java 25+ incompatibility        | Use Android Studio JBR via `JAVA_HOME` or `gradle.properties` |
| Firebase build error       | Missing `google-services.json`  | Add file from Firebase Console                                |
| XML resource errors        | Wrong namespace attributes      | Use `android:` prefix, not `app:` for layout positioning      |
| Back navigation broken     | `FLAG_ACTIVITY_CLEAR_TASK` used | ✅ Fixed in recent update                                     |
| Permissions not requesting | Missing auto-trigger            | ✅ Fixed: auto-triggers on screen load                        |

## Recent Updates (2026-04-20)

- ✅ Removed `FLAG_ACTIVITY_CLEAR_TASK` from all navigation for proper back stack
- ✅ Added auto-trigger for first permission request in PersonalPermissionsActivity
- ✅ Centered login/signup screen titles
- ✅ Locked role selection after onboarding
- ✅ Updated back button support across all activities
- ✅ Configured gradle.properties with Android Studio JBR path

## Next Steps

1. Add `google-services.json` for Firebase
2. Test full setup flow on Android 12+ device/emulator
3. Verify permissions request sequence works
4. Test back navigation through entire flow
5. Build release APK: `.\gradlew.bat :app:assembleRelease`
