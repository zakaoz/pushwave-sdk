# PushWave on Flutter

Android and iOS from one codebase. For native Android see [android.md](android.md).

- [Before you start](#before-you-start)
- [Install](#install)
- [Initialise](#initialise)
- [Routing a tap](#routing-a-tap)
- [Tags, users, opting out](#tags-users-opting-out)
- [Foreground behaviour](#foreground-behaviour)
- [iOS setup](#ios-setup)
- [What the app receives](#what-the-app-receives)
- [Migrating from onesignal_flutter](#migrating-from-onesignal_flutter)
- [Troubleshooting](#troubleshooting)

---

## Before you start

**1. Firebase.** PushWave delivers through FCM, and FCM relays to APNs on iOS, so
one setup covers both:

- `google-services.json` in `android/app/`
- `GoogleService-Info.plist` in `ios/Runner/`
- `firebase_core` in the app, and `Firebase.initializeApp()` at startup

**2. An app in the PushWave dashboard**, with the Firebase **service account
JSON** pasted into it. One PushWave app covers both platforms — the server
addresses a device by its token, not by a per-platform application.

Note the **App ID**. It is public by design and only lets a device register
itself.

---

## Install

```yaml
dependencies:
  pushwave_flutter:
    git:
      url: https://github.com/zakaoz/pushwave-sdk.git
      path: flutter/pushwave_flutter
      ref: v0.1.0
```

`ref` pins a tag — leave it pinned so a new SDK release cannot change your app
without you choosing it.

Requires **Dart 3.7+** and **Flutter 3.22+**. `firebase_messaging` comes with the
package.

---

## Initialise

```dart
import 'package:firebase_core/firebase_core.dart';
import 'package:pushwave_flutter/pushwave_flutter.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Firebase.initializeApp();

  await PushWave.initialize(
    appId: 'YOUR_APP_ID',
    serviceUrl: 'https://push.yourdomain.com',
  );

  runApp(const MyApp());
}
```

Registration happens in the background, so this never delays your first frame.

Ask for permission once the UI exists — a permission dialog on a blank screen
gets dismissed:

```dart
await PushWave.notifications.requestPermission();
```

---

## Routing a tap

```dart
PushWave.notifications.addClickListener((event) {
  final data = event.notification.additionalData;

  switch (data['screen']) {
    case 'match_detail':
      router.go('/match/${data['match_id']}');
    case 'news_detail':
      router.go('/news/${data['news_id']}');
    default:
      router.go('/');
  }
});
```

**A cold start is handled for you.** If the tap launched the app from a
terminated state, the SDK holds the event until you add a listener and delivers
it then — you do not need the pending-data bookkeeping this usually requires.

`additionalData` is never null: an empty map when the send carried no data.

---

## Tags, users, opting out

```dart
await PushWave.user.addTagWithKey('team', 'barca');
await PushWave.user.addTags({'team': 'barca', 'tier': 'pro'});
await PushWave.user.removeTag('team');

await PushWave.user.addAlias('external_id', 'user-42');
await PushWave.user.removeAlias('external_id');

// Notification toggle in your settings screen. Opting out also leaves the
// broadcast topic, so the device is excluded even from a send to everyone.
await PushWave.user.pushSubscription.optOut();
await PushWave.user.pushSubscription.optIn();
final on = await PushWave.user.pushSubscription.optedIn;

PushWave.user.pushSubscription.id;   // this device on the server
await PushWave.notifications.permission;
PushWave.notifications.addPermissionObserver((granted) { });
```

Tags drive segments. Tag the device, define a segment on the server with
`[{"field":"tag","key":"team","relation":"=","value":"barca"}]`, and the
dashboard shows the live device count before you send.

---

## Foreground behaviour

```dart
PushWave.notifications.addForegroundListener((event) {
  showBanner(event.notification.title, event.notification.body);
});
```

**iOS** displays the notification itself — the SDK sets the presentation options.

**Android displays nothing while your app is on screen.** That is the platform's
behaviour, not a choice the SDK makes. If you want a banner there, draw one in
this listener, for example with `flutter_local_notifications`.

---

## iOS setup

1. Xcode → Runner target → **Signing & Capabilities** → add **Push
   Notifications**, and **Background Modes → Remote notifications**.
2. Upload your APNs `.p8` key to Firebase Console → Cloud Messaging → *APNs
   Authentication Key*, so FCM can relay to Apple.
3. Push does not work on the iOS Simulator. Test on a real device.

To bypass Google on iOS entirely, put the `.p8`, key id, team id and bundle id
into the PushWave app settings and switch **iOS delivery** to *Direct to Apple*.
Nothing changes in the app.

---

## What the app receives

`additionalData` carries whatever you put in the send's `data` object, plus:

| key | meaning |
|---|---|
| `post_id` | your own id — the usual deep-link key |
| `link` | URL to open, if one was set |
| `unique_id` | per-send random value |

Title, body, image and the message id are lifted into named fields
(`event.notification.title`, `.body`, `.bigPicture`, `.notificationId`) rather
than left in the map.

---

## Migrating from onesignal_flutter

The API was shaped to make this a rename.

| OneSignal | PushWave |
|---|---|
| `OneSignal.initialize(appId)` | `await PushWave.initialize(appId:, serviceUrl:)` |
| `OneSignal.Notifications.addClickListener` | `PushWave.notifications.addClickListener` |
| `OSNotificationClickEvent` | `PushWaveClickEvent` |
| `event.notification.additionalData ?? {}` | `event.notification.additionalData` |
| `OneSignal.Notifications.requestPermission(true)` | `PushWave.notifications.requestPermission(true)` |
| `OneSignal.Notifications.permission` | `PushWave.notifications.permission` |
| `OneSignal.User.addAlias('external_id', id)` | `PushWave.user.addAlias('external_id', id)` |
| `OneSignal.User.addTagWithKey(k, v)` | `PushWave.user.addTagWithKey(k, v)` |
| `OneSignal.User.pushSubscription.optIn()` | `PushWave.user.pushSubscription.optIn()` |
| `addForegroundWillDisplayListener` + `.display()` | `addForegroundListener` — you draw it |
| `OneSignal.User.addEmail` / `addSms` | not supported: push only |

Two real differences, not just renames:

- **One app id for both platforms** instead of one per platform.
- **Android foreground shows nothing** unless you draw it. OneSignal's
  `.display()` did that for you.

**Bringing your existing devices across:** OneSignal's CSV export puts the FCM
token in its `identifier` column, so the whole install base can be imported and
used immediately. If you had separate OneSignal apps for iOS and Android, export
both and import them into the one PushWave app.

---

## Troubleshooting

**The device never appears under Devices.** Wrong app id or service URL, or
`Firebase.initializeApp()` did not run before `PushWave.initialize()`. The
console prints `PushWave: …` on failure.

**Nothing arrives on iOS.** Almost always the APNs key missing from Firebase, or
the Push Notifications capability missing in Xcode. Simulator never works.

**Nothing arrives on Android when the app is closed.** Send with **Show it
anyway** so Android's tray draws it rather than waiting for your process to
start.

**Nothing at all on one phone.** Check whether the user force stopped the app in
Android settings — nothing gets through after that, for any provider, until they
open it again.

**`received` stays low.** Only devices running a build with the SDK can report
back, and only for messages that reached the app rather than being drawn by the
system tray. It is a floor on real delivery, not a measure of it — Firebase's own
delivery numbers in the dashboard cover the rest.
