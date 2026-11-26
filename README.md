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


## Cloud Firestore

Create a default Cloud Firestore database in the Firebase project used by `app/google-services.json`. In the Firebase console, open **Firestore Database → Rules** and publish the rules from `firestore.rules`.

After sign-in, the app syncs gym records under `users/{uid}/gyms` and keeps its private JSON file for offline use. Existing gyms in the signed-in account's JSON file are uploaded when the connection is available. Gym photos stay on the device that selected them.
