# PushWave SDKs

Client SDKs for a self-hosted [PushWave](https://push.bigosting.com) push
service — Android and Flutter. Add one dependency; no SDK source in your app.

Both mirror the OneSignal SDK they replace, so migrating an existing app is
mostly renaming call sites.

There are no secrets in here. An app id is public by design — the same way it
ships inside an APK — and it only lets a device register itself and report its
own events. Sending needs the REST key, which never leaves your server.

---

## Android

`settings.gradle` — make sure JitPack is a repository:

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
implementation 'com.github.YOUR_GITHUB_USER.pushwave-sdk:pushwave-sdk:0.1.0'
```

`firebase-messaging` comes with it, so you do not declare it yourself. You do
still need Firebase in the app: the `com.google.gms.google-services` plugin and
`app/google-services.json`.

`res/values/pushwave.xml`:

```xml
<resources>
    <string name="pushwave_app_id">YOUR_APP_ID</string>
    <string name="pushwave_service_url">https://push.yourdomain.com</string>
</resources>
```

`Application.onCreate()`:

```java
new PushWave.Builder(this)
        .setAppId(getString(R.string.pushwave_app_id))
        .setServiceUrl(getString(R.string.pushwave_service_url))
        .build(() -> {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra(PushWave.EXTRA_POST_ID, PushWave.AdditionalData.postId);
            intent.putExtra(PushWave.EXTRA_LINK, PushWave.AdditionalData.link);
            startActivity(intent);
        });
```

Your launcher Activity's `onCreate()`:

```java
new PushWave.Builder(this).requestNotificationPermission();
// Android's own tray draws the notification when the app process was killed,
// and that tap lands here rather than on the SDK's activity.
PushWave.handleLaunchIntent(this);
```

**Remove any `FirebaseMessagingService` of your own** — the SDK ships one, and
two services listening for `MESSAGING_EVENT` means Android picks one and the
other silently never fires.

Then: `PushWave.setTag(k, v)` · `setExternalId(id)` · `setEnabled(false)` ·
`getSubscriptionId()`.

## Flutter

`pubspec.yaml`:

```yaml
dependencies:
  pushwave_flutter:
    git:
      url: https://github.com/YOUR_GITHUB_USER/pushwave-sdk.git
      path: flutter/pushwave_flutter
      ref: v0.1.0
```

```dart
await Firebase.initializeApp();
await PushWave.initialize(
  appId: 'your-app-id',
  serviceUrl: 'https://push.yourdomain.com',
);
PushWave.notifications.addClickListener((event) {
  route(event.notification.additionalData);
});
```

Full API and the OneSignal migration table: [`flutter/pushwave_flutter/README.md`](flutter/pushwave_flutter/README.md).

---

## Releasing a version

JitPack builds a tag on demand — nothing to publish by hand.

```bash
git tag v0.1.1 && git push origin v0.1.1
```

The first request for a new tag takes a minute or two while JitPack builds it;
watch `https://jitpack.io/#YOUR_GITHUB_USER/pushwave-sdk`. Apps then move by
bumping the version string.

## Layout

```
android/pushwave-sdk/     Gradle library, drop-in for the OneSignal wrapper
flutter/pushwave_flutter/ Dart package wrapping firebase_messaging
settings.gradle           Gradle build at the root so JitPack finds it
jitpack.yml               JDK 17 and the publish command
```

## Requirements

Android: minSdk 21, JDK 17 to build.
Flutter: Dart 3.7+, Flutter 3.22+.
