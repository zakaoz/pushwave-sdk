# Changelog

Versions are git tags. JitPack builds a tag on demand for Android; Flutter
consumes the same tag through `ref:`.

## v0.1.1

**Fixed — Flutter: `version solving failed` for apps on firebase_messaging 16.x.**

The package declared `firebase_messaging: ^15.1.5`. A caret range means
`>=15.1.5 <16.0.0`, so any app already on 16.x could not resolve:

```
Because pushwave_flutter depends on firebase_messaging ^15.1.5 and
your_app depends on firebase_messaging ^16.5.0, version solving failed.
```

The range is now `>=15.1.5 <17.0.0`. The APIs this package uses —
`FirebaseMessaging.instance`, `onMessage`, `onMessageOpenedApp`, `RemoteMessage`
— have been stable across those majors, so the app's own constraint decides the
version rather than the library forcing one.

Verified resolving to 16.5.0 with the analyzer clean.

Android is unaffected: Gradle treats a version as a floor and picks the highest
in the graph, which is why `firebase-messaging:24.1.0` here already resolves to
whatever the app's Firebase BOM pins.

## v0.1.0

First release. Android library and Flutter package for a self-hosted PushWave
service.

- Device registration, token refresh, tags, external ids, opt-out
- `received` and `clicked` reporting, and a session ping for monthly active users
- Notifications drawn by the SDK or by the platform, chosen per send
- Cold-start taps buffered until the app attaches a listener
- API shaped after the OneSignal SDK it replaces
