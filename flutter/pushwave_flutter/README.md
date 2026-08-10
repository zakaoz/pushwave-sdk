# pushwave_flutter

Flutter client for a self-hosted PushWave service. Android and iOS, one app id
for both.

The API deliberately mirrors the OneSignal v5 SDK — `PushWave.notifications`,
`PushWave.user`, `pushSubscription` — so migrating an app is mostly renaming
call sites rather than rewriting logic.

## Install

```yaml
dependencies:
  pushwave_flutter:
    path: ../path/to/pushwave/sdk/flutter/pushwave_flutter
```

It pulls in `firebase_messaging`, which is the transport: PushWave sends through
FCM, and FCM relays to APNs on iOS.

You still need Firebase set up in the app — `google-services.json` for Android,
`GoogleService-Info.plist` for iOS — and `Firebase.initializeApp()` before
`PushWave.initialize()`.

## Use

```dart
await Firebase.initializeApp();

await PushWave.initialize(
  appId: 'your-app-id',                       // from the PushWave dashboard
  serviceUrl: 'https://push.yourdomain.com',
);

PushWave.notifications.addClickListener((event) {
  // Whatever you put in `data` when sending arrives here.
  final screen = event.notification.additionalData['screen'];
  router.go('/$screen');
});

await PushWave.notifications.requestPermission();
```

Registration runs in the background, so `initialize` never delays your first
frame. A tap that launched the app from a terminated state is held until you add
a click listener, so a cold start is never lost.

### Everything else

```dart
PushWave.user.addAlias('external_id', userId);   // link to your own user id
PushWave.user.removeAlias('external_id');

PushWave.user.addTagWithKey('team', 'barca');    // drives segments
PushWave.user.addTags({'team': 'barca', 'tier': 'pro'});
PushWave.user.removeTag('team');

PushWave.user.pushSubscription.optOut();         // also leaves the broadcast topic
PushWave.user.pushSubscription.optIn();
PushWave.user.pushSubscription.id;

await PushWave.notifications.permission;         // current OS permission
PushWave.notifications.addPermissionObserver((granted) { });
PushWave.notifications.addForegroundListener((event) { });
```

## Coming from onesignal_flutter

| OneSignal | PushWave |
|---|---|
| `OneSignal.initialize(appId)` | `await PushWave.initialize(appId:, serviceUrl:)` |
| `OneSignal.Notifications.addClickListener` | `PushWave.notifications.addClickListener` |
| `OSNotificationClickEvent` | `PushWaveClickEvent` |
| `event.notification.additionalData ?? {}` | `event.notification.additionalData` (never null) |
| `OneSignal.Notifications.requestPermission(true)` | `PushWave.notifications.requestPermission(true)` |
| `OneSignal.Notifications.permission` | `PushWave.notifications.permission` |
| `OneSignal.User.addAlias('external_id', id)` | `PushWave.user.addAlias('external_id', id)` |
| `OneSignal.User.addTagWithKey(k, v)` | `PushWave.user.addTagWithKey(k, v)` |
| `OneSignal.User.pushSubscription.optIn()` | `PushWave.user.pushSubscription.optIn()` |
| `addForegroundWillDisplayListener` + `.display()` | `addForegroundListener` — see below |
| `OneSignal.User.addEmail` / `addSms` | not supported: push only |

## Foreground behaviour

A message that arrives while the app is on screen reaches
`addForegroundListener`. iOS displays it too, because the SDK sets the
foreground presentation options. **Android displays nothing in this state** — by
platform design, not by choice. Draw one yourself there if you want it, for
example with `flutter_local_notifications`.

## iOS setup

1. Xcode → Runner target → Signing & Capabilities → add **Push Notifications**
   and **Background Modes → Remote notifications**.
2. Upload your APNs `.p8` key to Firebase Console → Cloud Messaging, so FCM can
   relay to Apple. (Or configure the key in PushWave and switch the app to
   direct-to-Apple delivery.)

## What is reported back

The SDK posts three events, which is where the dashboard's numbers come from:

- **session** on registration — feeds monthly active users
- **received** when a message arrives with the app in the foreground
- **clicked** when a notification is tapped

A notification drawn by the Android system while the app is closed cannot report
`received` — nothing in your app ran. That is a property of the platform, and it
is the trade for the notification appearing at all on a device that killed the
app process.
