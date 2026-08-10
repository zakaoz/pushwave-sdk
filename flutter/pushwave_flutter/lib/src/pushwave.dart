import 'dart:io' show Platform;

import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'api.dart';
import 'models.dart';

/// PushWave Flutter client.
///
/// The surface mirrors the OneSignal v5 SDK it replaces — `PushWave.notifications`,
/// `PushWave.user`, `pushSubscription` — so migrating an app is mostly a matter
/// of renaming the calls.
///
/// ```dart
/// await Firebase.initializeApp();
/// await PushWave.initialize(
///   appId: 'your-app-id',
///   serviceUrl: 'https://push.yourdomain.com',
/// );
/// PushWave.notifications.addClickListener((event) {
///   route(event.notification.additionalData);
/// });
/// ```
class PushWave {
  PushWave._();

  static PushWaveApi? _api;
  static String? _subscriptionId;
  static String? _topic;
  static bool _initialized = false;

  static final PushWaveNotifications notifications = PushWaveNotifications._();
  static final PushWaveUser user = PushWaveUser._();

  static const _keySubscriptionId = 'pushwave_subscription_id';
  static const _keyTopic = 'pushwave_topic';
  static const _keyOptedIn = 'pushwave_opted_in';
  static const _keyExternalId = 'pushwave_external_id';

  /// This device's id on the server, once registration has completed.
  static String? get subscriptionId => _subscriptionId;

  /// Call once, after `Firebase.initializeApp()`.
  ///
  /// Registration happens in the background: this returns as soon as the
  /// listeners are attached, so it never delays your first frame.
  static Future<void> initialize({
    required String appId,
    required String serviceUrl,
  }) async {
    if (_initialized) return;
    _initialized = true;

    _api = PushWaveApi(baseUrl: serviceUrl, appId: appId);

    final prefs = await SharedPreferences.getInstance();
    _subscriptionId = prefs.getString(_keySubscriptionId);
    _topic = prefs.getString(_keyTopic);

    // A tap that launched the app from a terminated state is waiting here.
    // Read it before anything else so a cold start is not missed.
    final initial = await FirebaseMessaging.instance.getInitialMessage();
    if (initial != null) notifications._dispatchClick(initial);

    FirebaseMessaging.onMessageOpenedApp.listen(notifications._dispatchClick);
    FirebaseMessaging.onMessage.listen(notifications._dispatchForeground);
    FirebaseMessaging.instance.onTokenRefresh.listen(_registerToken);

    // iOS shows nothing in the foreground unless asked to.
    await FirebaseMessaging.instance.setForegroundNotificationPresentationOptions(
      alert: true,
      badge: true,
      sound: true,
    );

    await _registerCurrentToken();
  }

  static Future<void> _registerCurrentToken() async {
    try {
      final token = await FirebaseMessaging.instance.getToken();
      if (token == null) {
        debugPrint('PushWave: no FCM token yet');
        return;
      }
      await _registerToken(token);
    } catch (error) {
      debugPrint('PushWave: could not obtain an FCM token: $error');
    }
  }

  static Future<void> _registerToken(String token) async {
    final api = _api;
    if (api == null) return;

    final prefs = await SharedPreferences.getInstance();
    final locale = Platform.localeName.split(RegExp('[_-]'));

    final result = await api.register(
      token: token,
      deviceOs: Platform.operatingSystemVersion,
      language: locale.isNotEmpty ? locale.first : null,
      country: locale.length > 1 ? locale[1] : null,
      externalId: prefs.getString(_keyExternalId),
    );
    if (result == null) return;

    _subscriptionId = result['subscription_id'] as String?;
    if (_subscriptionId != null) {
      await prefs.setString(_keySubscriptionId, _subscriptionId!);
    }

    final topic = result['topic'] as String?;
    if (topic != null) {
      _topic = topic;
      await prefs.setString(_keyTopic, topic);
      // One request on the server reaches everyone on this topic, which is what
      // makes a broadcast to the whole install base cheap.
      if (prefs.getBool(_keyOptedIn) ?? true) {
        await FirebaseMessaging.instance.subscribeToTopic(topic);
      }
    }

    if (_subscriptionId != null) {
      await api.event(_subscriptionId!, 'session');
    }
  }

  static Future<void> _report(String type, String? messageId) async {
    final api = _api;
    final id = _subscriptionId;
    if (api == null || id == null) return;
    await api.event(id, type, messageId: messageId);
  }

  static Future<void> _update(Map<String, dynamic> fields) async {
    final api = _api;
    final id = _subscriptionId;
    if (api == null || id == null) {
      debugPrint('PushWave: device not registered yet, ignoring update');
      return;
    }
    await api.update(id, fields);
  }
}

/// Notification events and permission.
class PushWaveNotifications {
  PushWaveNotifications._();

  final List<void Function(PushWaveClickEvent)> _clickListeners = [];
  final List<void Function(PushWaveForegroundEvent)> _foregroundListeners = [];
  final List<void Function(bool)> _permissionObservers = [];

  /// A tap that arrived before the app attached a listener — a cold start,
  /// usually. Held so the first listener still gets it instead of the tap
  /// silently doing nothing.
  PushWaveClickEvent? _pendingClick;

  void addClickListener(void Function(PushWaveClickEvent) handler) {
    _clickListeners.add(handler);
    final pending = _pendingClick;
    if (pending != null) {
      _pendingClick = null;
      handler(pending);
    }
  }

  void removeClickListener(void Function(PushWaveClickEvent) handler) =>
      _clickListeners.remove(handler);

  /// Fires for a message that arrives while the app is on screen. Android does
  /// not draw a notification in this state — display it yourself here if you
  /// want one (for example with flutter_local_notifications).
  void addForegroundListener(void Function(PushWaveForegroundEvent) handler) =>
      _foregroundListeners.add(handler);

  void addPermissionObserver(void Function(bool) handler) => _permissionObservers.add(handler);

  /// Asks the OS for notification permission. Android 13+ and iOS both need it;
  /// on older Android it is already granted and this returns true.
  Future<bool> requestPermission([bool fallbackToSettings = false]) async {
    final settings = await FirebaseMessaging.instance.requestPermission(
      alert: true,
      badge: true,
      sound: true,
    );
    final granted = settings.authorizationStatus == AuthorizationStatus.authorized ||
        settings.authorizationStatus == AuthorizationStatus.provisional;
    for (final observer in _permissionObservers) {
      observer(granted);
    }
    return granted;
  }

  Future<bool> get permission async {
    final settings = await FirebaseMessaging.instance.getNotificationSettings();
    return settings.authorizationStatus == AuthorizationStatus.authorized ||
        settings.authorizationStatus == AuthorizationStatus.provisional;
  }

  void _dispatchClick(RemoteMessage message) {
    final notification = PushWaveNotification.fromRemoteMessage(message);
    PushWave._report('clicked', notification.notificationId);

    final event = PushWaveClickEvent(notification);
    if (_clickListeners.isEmpty) {
      _pendingClick = event;
      return;
    }
    for (final listener in List.of(_clickListeners)) {
      listener(event);
    }
  }

  void _dispatchForeground(RemoteMessage message) {
    final notification = PushWaveNotification.fromRemoteMessage(message);
    PushWave._report('received', notification.notificationId);

    final event = PushWaveForegroundEvent(notification);
    for (final listener in List.of(_foregroundListeners)) {
      listener(event);
    }
  }
}

/// Identity, tags and opt-in state.
class PushWaveUser {
  PushWaveUser._();

  late final PushWavePushSubscription pushSubscription = PushWavePushSubscription._();

  /// Links this device to your own user id. Only `external_id` is meaningful;
  /// the parameter exists so OneSignal call sites port over unchanged.
  Future<void> addAlias(String label, String id) async {
    if (label != 'external_id') {
      debugPrint('PushWave: only the "external_id" alias is supported, ignoring "$label"');
      return;
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(PushWave._keyExternalId, id);
    await PushWave._update({'external_id': id});
  }

  Future<void> removeAlias(String label) async {
    if (label != 'external_id') return;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(PushWave._keyExternalId);
  }

  Future<void> addTagWithKey(String key, String value) => PushWave._update({
        'tags': {key: value},
      });

  Future<void> addTags(Map<String, String> tags) => PushWave._update({'tags': tags});

  /// An empty value deletes the tag, matching the server's merge rule.
  Future<void> removeTag(String key) => PushWave._update({
        'tags': {key: ''},
      });
}

/// This device's push subscription.
class PushWavePushSubscription {
  PushWavePushSubscription._();

  String? get id => PushWave._subscriptionId;

  Future<bool> get optedIn async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(PushWave._keyOptedIn) ?? true;
  }

  Future<void> optIn() => _setOptedIn(true);

  Future<void> optOut() => _setOptedIn(false);

  /// Opting out also leaves the broadcast topic, so a device that turned
  /// notifications off stays out even of a send addressed to everyone.
  Future<void> _setOptedIn(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(PushWave._keyOptedIn, value);

    final topic = PushWave._topic;
    if (topic != null) {
      if (value) {
        await FirebaseMessaging.instance.subscribeToTopic(topic);
      } else {
        await FirebaseMessaging.instance.unsubscribeFromTopic(topic);
      }
    }

    await PushWave._update({'enabled': value});
  }
}
