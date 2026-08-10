# PushWave on Android

Native Android (Java or Kotlin). For Flutter see [flutter.md](flutter.md).

- [Before you start](#before-you-start)
- [Install](#install)
- [Initialise](#initialise)
- [Handling a tap](#handling-a-tap)
- [Tags, users, opting out](#tags-users-opting-out)
- [What the app receives](#what-the-app-receives)
- [Sending](#sending)
- [Migrating from OneSignal](#migrating-from-onesignal)
- [Troubleshooting](#troubleshooting)

---

## Before you start

**1. Firebase.** PushWave delivers through FCM, so the app needs Firebase:

- Firebase Console → add your Android app with its exact package name
- Download `google-services.json` into `app/`
- The `com.google.gms.google-services` plugin in `app/build.gradle`

**2. An app in the PushWave dashboard.** Create one and paste the Firebase
**service account JSON** into it (Firebase Console → Project settings → Service
accounts → Generate new private key). Without it the server has nothing to send
with.

Note the **App ID** it gives you. That value is public — it ships inside your
APK — and only lets a device register itself.

**3. If you are replacing an existing push provider**, put the FCM topic your
published build already subscribes to into the app's **Legacy FCM topic** field.
That is how you reach the current install base before anyone updates.

---

## Install

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
implementation 'com.github.zakaoz:pushwave-sdk:v0.1.0'
```

`firebase-messaging` arrives with it — do not declare it yourself. If your app
uses the Firebase BOM, the BOM's version wins, which is what you want.

Requires **minSdk 21** and **JDK 17** to build.

`res/values/pushwave.xml`:

```xml
<resources>
    <string name="pushwave_app_id">YOUR_APP_ID</string>
    <string name="pushwave_service_url">https://push.yourdomain.com</string>
</resources>
```

### Remove any FirebaseMessagingService of your own

The SDK ships one. Two services listening for `MESSAGING_EVENT` in the merged
manifest means Android delivers to exactly one of them and the other silently
never fires — a failure that looks like "notifications randomly stopped".

Delete your service class and its `<service>` block from `AndroidManifest.xml`.

### Icon and colour

The SDK reuses the Firebase meta-data your manifest probably already has:

```xml
<meta-data
    android:name="com.google.firebase.messaging.default_notification_icon"
    android:resource="@drawable/ic_stat_notify" />
<meta-data
    android:name="com.google.firebase.messaging.default_notification_color"
    android:resource="@color/colorPrimary" />
```

Without them it falls back to the launcher icon. Use a white, transparent-
background silhouette — Android masks the small icon.

---

## Initialise

In your `Application.onCreate()`:

```java
new PushWave.Builder(this)
        .setAppId(getString(R.string.pushwave_app_id))
        .setServiceUrl(getString(R.string.pushwave_service_url))
        .build(() -> {
            // Runs when the user taps a notification. Open whatever it points at.
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            intent.putExtra(PushWave.EXTRA_ID, PushWave.Data.id);
            intent.putExtra(PushWave.EXTRA_TITLE, PushWave.Data.title);
            intent.putExtra(PushWave.EXTRA_MESSAGE, PushWave.Data.message);
            intent.putExtra(PushWave.EXTRA_IMAGE, PushWave.Data.bigImage);
            intent.putExtra(PushWave.EXTRA_LAUNCH_URL, PushWave.Data.launchUrl);
            intent.putExtra(PushWave.EXTRA_UNIQUE_ID, PushWave.AdditionalData.uniqueId);
            intent.putExtra(PushWave.EXTRA_POST_ID, PushWave.AdditionalData.postId);
            intent.putExtra(PushWave.EXTRA_LINK, PushWave.AdditionalData.link);
            startActivity(intent);
        });
```

Registration runs off the main thread; the call returns immediately.

In your launcher Activity's `onCreate()`:

```java
new PushWave.Builder(this).requestNotificationPermission();
PushWave.handleLaunchIntent(this);
```

`requestNotificationPermission()` asks for `POST_NOTIFICATIONS` on Android 13+.
Users updating from an older build keep the permission automatically if they had
notifications on; a fresh install has to be asked.

`handleLaunchIntent()` is explained next.

---

## Handling a tap

There are two paths a tap can take, and the SDK makes them look the same to you.

**The SDK drew the notification.** The tap goes through the SDK's own activity,
which fills `PushWave.Data` / `PushWave.AdditionalData` and calls the callback
you passed to `build()`.

**Android's tray drew it.** This is what happens when the app process was killed
— the normal state on phones with an aggressive battery manager. The tap opens
your launcher Activity directly with the message data as raw intent extras, and
the SDK's activity never runs.

`PushWave.handleLaunchIntent(activity)` covers the second case: it reads those
raw extras, fills the same holders, reports the click, and copies the values onto
the intent under the same `EXTRA_*` keys. Your reading code needs no special
case:

```java
String postId = getIntent().getStringExtra(PushWave.EXTRA_POST_ID);
String link   = getIntent().getStringExtra(PushWave.EXTRA_LINK);
if (postId != null && !postId.equals("0")) {
    openPost(postId);
}
```

Which path a message takes is chosen per send, in the dashboard's
**When the app has been closed** control.

---

## Tags, users, opting out

```java
// Segmentation: "send to everyone whose team is barca"
PushWave.setTag("team", "barca");
PushWave.setTags(Map.of("team", "barca", "tier", "pro"));

// Link the device to your own user id, so you can target that person
PushWave.setExternalId("user-42");

// A notifications toggle in your settings screen.
// Opting out also leaves the broadcast topic, so the device is excluded even
// from a send addressed to everyone.
PushWave.setEnabled(false);
boolean on = PushWave.isEnabled();

// This device's id on the server
String id = PushWave.getSubscriptionId();
```

Tags are how segments work. `setTag("team", "barca")` on the device, then a
segment with `[{"field":"tag","key":"team","relation":"=","value":"barca"}]` on
the server, and the dashboard shows you the live device count before you send.

---

## What the app receives

Every send arrives as an FCM data map:

| key | meaning |
|---|---|
| `title` | notification title |
| `message` | notification body |
| `big_image` | large image URL, if one was set |
| `link` | URL to open, if one was set |
| `post_id` | your own id — the usual deep-link key |
| `unique_id` | per-send random value |
| `pw_message_id` | the PushWave message id; delivery and click counts key on it |

Anything else you put in the send's `data` object arrives alongside them, so you
can add `screen`, `match_id`, `category_id` — whatever your routing needs.

---

## Sending

From your own backend, with the **REST API key** (server-side only — never in
the app):

```bash
curl -X POST https://push.yourdomain.com/api/v1/notifications \
  -H "Authorization: Basic YOUR_REST_API_KEY" \
  -H 'content-type: application/json' \
  -d '{
    "included_segments": ["All"],
    "headings": {"en": "Match starting"},
    "contents": {"en": "Barcelona vs Madrid, live now"},
    "big_picture": "https://example.com/match.jpg",
    "priority": 10,
    "data": {"post_id": "42", "screen": "match_detail"}
  }'
```

`priority` 10 wakes a sleeping device now; 5 lets it wait. Keep promotions on 5 —
providers reduce an app's high-priority allowance when those messages routinely
lead to no interaction.

---

## Migrating from OneSignal

The API was shaped to make this mechanical.

| OneSignal wrapper | PushWave |
|---|---|
| `new OneSignalPush.Builder(this).setOneSignalAppId(id).build(cb)` | `new PushWave.Builder(this).setAppId(id).setServiceUrl(url).build(cb)` |
| `OneSignalPush.EXTRA_POST_ID` | `PushWave.EXTRA_POST_ID` (all `EXTRA_*` keys match) |
| `OneSignalPush.Data.title` | `PushWave.Data.title` |
| `OneSignalPush.AdditionalData.postId` | `PushWave.AdditionalData.postId` |
| `new OneSignalPush.Builder(this).requestNotificationPermission()` | same, on `PushWave.Builder` |

In practice: change the import, change the builder's two setters, delete your
`FirebaseMessagingService`, add `PushWave.handleLaunchIntent(this)`. Everything
downstream keeps working.

**Bringing your existing devices across:** OneSignal's CSV export puts the FCM
token in its `identifier` column, and those tokens belong to your Firebase
project — so they can be imported and used immediately, without waiting for
users to update. See the server's `scripts/import-onesignal.mjs`.

---

## Troubleshooting

**Nothing arrives at all.** Check the dashboard's **History** — if the message
says `failed`, the reason is on the row. `no FCM service account configured`
means the app has no credentials; `UNREGISTERED` means the token is dead.

**The device never appears under Devices.** The app id or service URL is wrong,
or the device has no network. `adb logcat | grep PushWave` prints the failure.

**It arrives but nothing shows.** Almost always the notification permission:

```
adb shell dumpsys package YOUR_PACKAGE | grep POST_NOTIFICATIONS
```

`granted=false` means Android is suppressing it. Call
`requestNotificationPermission()` from an Activity and let the user accept.

**Works in the foreground, not when closed.** The message was sent app-rendered
and the phone refused to start your process. Send it with **Show it anyway** so
Android's tray draws it instead.

**Nothing reaches one particular phone.** Check whether the user force stopped
the app in Android settings. Nothing gets through after that — for any push
provider — until they open the app again.

**Two notifications for one send.** You still have your own
`FirebaseMessagingService`. Remove it.
