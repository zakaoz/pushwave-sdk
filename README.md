# PushWave SDKs

Client SDKs for a self-hosted PushWave push service — **Android** and
**Flutter**. Add one dependency; no SDK source in your app.

Both mirror the OneSignal SDK they replace, so migrating an existing app is
mostly renaming call sites.

There are no secrets in here. An app id is public by design — the same way it
ships inside an APK — and it only lets a device register itself and report its
own events. Sending needs the REST key, which never leaves your server.

## 📖 Guides

| | |
|---|---|
| **[Android](docs/android.md)** | install, init, taps, tags, payload, migration, troubleshooting |
| **[Flutter](docs/flutter.md)** | same, plus iOS setup and foreground behaviour |

## Quick start

### Android

`settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

`app/build.gradle`:

```gradle
implementation 'com.github.zakaoz:pushwave-sdk:v0.1.1'
```

The group is the GitHub owner and the version is the tag — note the leading `v`.
`firebase-messaging` comes with it.

```java
new PushWave.Builder(this)
        .setAppId(getString(R.string.pushwave_app_id))
        .setServiceUrl(getString(R.string.pushwave_service_url))
        .build(() -> openYourActivity());
```

→ [Full guide](docs/android.md)

### Flutter

```yaml
dependencies:
  pushwave_flutter:
    git:
      url: https://github.com/zakaoz/pushwave-sdk.git
      path: flutter/pushwave_flutter
      ref: v0.1.1
```

```dart
await Firebase.initializeApp();
await PushWave.initialize(
  appId: 'YOUR_APP_ID',
  serviceUrl: 'https://push.yourdomain.com',
);
PushWave.notifications.addClickListener((event) {
  route(event.notification.additionalData);
});
```

→ [Full guide](docs/flutter.md)

## What you need either way

1. **Firebase** in the app — `google-services.json` / `GoogleService-Info.plist`.
   PushWave delivers through FCM, and FCM relays to APNs on iOS.
2. **An app in the PushWave dashboard**, with the Firebase service account JSON
   pasted in. It hands you the app id.

Version history and upgrade notes: [CHANGELOG.md](CHANGELOG.md).

## Releasing a version

JitPack builds a tag on demand — nothing to publish by hand.

```bash
git tag v0.1.1 && git push origin v0.1.1
```

The first request for a new tag takes a minute or two while it builds; watch
<https://jitpack.io/#zakaoz/pushwave-sdk>. Apps move by bumping the version, and
a pinned `ref` means a release never changes an app without you choosing it.

## Layout

```
android/pushwave-sdk/       Gradle library, drop-in for the OneSignal wrapper
flutter/pushwave_flutter/   Dart package wrapping firebase_messaging
docs/                       the integration guides
settings.gradle             Gradle build at the root so JitPack finds it
jitpack.yml                 JDK 17 and the publish command
```

## Requirements

Android — minSdk 21, JDK 17 to build.
Flutter — Dart 3.7+, Flutter 3.22+.
