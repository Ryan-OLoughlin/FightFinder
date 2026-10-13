# FightFinder

A Kotlin Android app for discovering martial arts gyms and tracking training details.

## Data behavior

Gym records are held in memory only and are cleared when the app process restarts. Persistence is not required.

## Build

Open this directory in Android Studio, or run the Gradle wrapper from the project root:

```bash
./gradlew assembleDebug
```

When building from a shell, set `ANDROID_HOME` to the Android SDK installation directory if it is not already configured.
