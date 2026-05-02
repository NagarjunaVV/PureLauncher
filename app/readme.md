# PureLauncher App Module

This README documents the current build status, setup flow, and configuration for the PureLauncher app module.

## Build Status

- ✅ Verified successful build: `:app:assembleDebug`
- ✅ APK generated: `app-debug.apk` (18.8 MB)
- ✅ Build verified: 2026-04-20
- **Target**: Android 13+ (minSdk 33, targetSdk 36)

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
    ┌──────────▼─────────────────┐
    │ PersonalFeatureTourActivity │ (Child only)
    │ └─ Feature intro slides     │
    │ └─ Swipeable pages          │
    └──────────┬─────────────────┘
               │
    ┌──────────▼──────────────────────┐
    │ PersonalPermissionsActivity     │ (Child only)
    │ └─ 3-step permission wizard:    │
    │   1. Overlay permission         │
    │   2. Set default home screen    │
    │   3. Usage access (tracking)    │
    │ └─ Auto-triggers first request  │
    └──────────┬──────────────────────┘
               │
    ┌──────────▼──────────────────┐
    │ AuthenticationActivity       │
    │ └─ Parent: PIN entry        │
    │ └─ Child: Routes to Login   │
    └──────────┬──────────────────┘
               │
    ┌──────────▼──────────────┐
    │ LoginActivity            │ (Child only)
    │ └─ Email/Password login  │
    │ └─ Firebase Auth         │
    │ └─ Or SignupActivity     │
    └──────────┬──────────────┘
               │
    ┌──────────▼──────────────┐
    │ LauncherActivity        │
    │ └─ Main home screen     │
    └─────────────────────────┘
```

### Key Features

- ✅ **Back button works throughout**: Users can go back from any screen (except LauncherActivity)
- ✅ **Role selection locked**: Once role is saved, users cannot change it during setup
- ✅ **Permissions auto-trigger**: First permission dialog opens automatically on permissions screen
- ✅ **Centered login/signup**: Sign-in screens have centered titles for better UX

### Session Management

- Role saved in `SessionPrefs` after onboarding
- Once role is set, `OnboardingActivity` skips to next screen
- Permissions tracked: `isPersonalPermissionsComplete`
- Tour tracked: `isPersonalTourComplete`
- Auth state: `isChildAuthComplete` + Firebase user

## Android Studio Setup

1. Open `PureLauncher3` project root
2. Let Gradle sync complete
3. Gradle uses Android Studio JBR (configured in `gradle.properties`)

No additional manual JVM configuration needed.

## Module Configuration

| Setting     | Value                      |
| ----------- | -------------------------- |
| Namespace   | `com.example.purelauncher` |
| Compile SDK | 36                         |
| Target SDK  | 36                         |
| Min SDK     | 33 (Android 13)            |
| Java        | 11                         |
| Kotlin      | 2.0.21                     |
| Gradle      | 8.13                       |

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
2. Test full setup flow on Android 13+ device/emulator
3. Verify permissions request sequence works
4. Test back navigation through entire flow
5. Build release APK: `.\gradlew.bat :app:assembleRelease`
