import 'package:firebase_messaging/firebase_messaging.dart';

/// A notification as the app sees it.
///
/// Field names follow the OneSignal SDK this replaces, so a migrating app's
/// existing handling reads the same.
class PushWaveNotification {
  PushWaveNotification({
    required this.notificationId,
    required this.title,
    required this.body,
    required this.additionalData,
    this.bigPicture,
    this.launchUrl,
  });

  /// The PushWave message id. Delivery and click counts are keyed on it.
  final String? notificationId;
  final String? title;
  final String? body;
  final String? bigPicture;
  final String? launchUrl;

  /// Custom key/value payload — `post_id`, `screen`, `match_id`, and so on.
  final Map<String, dynamic> additionalData;

  /// Everything PushWave sends rides in the FCM data map. The reserved keys are
  /// lifted into named fields and the rest is handed over as additionalData, so
  /// routing code keeps working against the same keys it used before.
  factory PushWaveNotification.fromRemoteMessage(RemoteMessage message) {
    final data = Map<String, dynamic>.from(message.data);

    const reserved = {
      'pw_message_id',
      'title',
      'message',
      'big_image',
      'launch_url',
    };
    final extra = <String, dynamic>{
      for (final entry in data.entries)
        if (!reserved.contains(entry.key)) entry.key: entry.value,
    };

    return PushWaveNotification(
      notificationId: data['pw_message_id'] as String?,
      // A system-rendered notification carries the text in the notification
      // block; a data-only one carries it in the data map. Prefer whichever
      // arrived.
      title: message.notification?.title ?? data['title'] as String?,
      body: message.notification?.body ?? data['message'] as String?,
      bigPicture: data['big_image'] as String?,
      launchUrl: data['launch_url'] as String?,
      additionalData: extra,
    );
  }
}

/// Passed to a click listener.
class PushWaveClickEvent {
  PushWaveClickEvent(this.notification);
  final PushWaveNotification notification;
}

/// Passed to a foreground listener, for a message that arrived while the app
/// was on screen.
class PushWaveForegroundEvent {
  PushWaveForegroundEvent(this.notification);
  final PushWaveNotification notification;
}
