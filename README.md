# FightFinder

A Kotlin Android app for discovering martial arts gyms and tracking training details.

## Build

Open in Android Studio, or run the Gradle wrapper from the project root:

```bash
./gradlew assembleDebug
```

## Firebase configuration

FightFinder includes the Android client configuration for `fightfinderrelease` in `app/google-services.json`. A build from this repository connects to that project for email sign-in and gym sync.

To use your own Firebase project, register `org.wit.fightfinder`, enable Email/Password in **Authentication > Sign-in method**, and replace `app/google-services.json` with the configuration from your project.


## Cloud Firestore

If you use your own Firebase project, create a default Cloud Firestore database. In the Firebase console, open **Firestore Database > Rules** and publish the rules from `firestore.rules`.

After sign-in, the app syncs gym records under `users/{uid}/gyms` and keeps its private JSON file for offline use. Existing gyms in the signed-in account's JSON file are uploaded when the connection is available. Gym photos stay on the device that selected them.
