# FightFinder

A Kotlin Android app for discovering martial arts gyms and tracking training details.

## Build

Open in Android Studio, or run the Gradle wrapper from the project root:

```bash
./gradlew assembleDebug
```

## Firebase configuration

Register the Android app `org.wit.fightfinder` in your Firebase project and enable Email/Password in Authentication → Sign-in method. Download the Android configuration as `app/google-services.json` and sync Gradle. The project can build without this configuration, and the sign-in screen shows a setup message in that case.

Each account uses its own gym data on the device.
