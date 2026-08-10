import 'dart:convert';
import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;

/// Thin wrapper over the PushWave REST endpoints the SDK is allowed to call.
///
/// These routes authenticate with the public app id alone — the same trust
/// model as the app id baked into an APK. They can register or update the
/// caller's own device and report events, and nothing else; sending requires
/// the REST key, which never leaves your server.
class PushWaveApi {
  PushWaveApi({required this.baseUrl, required this.appId});

  final String baseUrl;
  final String appId;

  Uri _uri(String path) => Uri.parse('${baseUrl.replaceAll(RegExp(r'/$'), '')}$path');

  Future<Map<String, dynamic>?> _post(String path, Map<String, dynamic> body) async {
    try {
      final response = await http
          .post(
            _uri(path),
            headers: const {'content-type': 'application/json'},
            body: jsonEncode(body),
          )
          .timeout(const Duration(seconds: 15));

      if (response.statusCode >= 400) {
        debugPrint('PushWave: $path failed (${response.statusCode}): ${response.body}');
        return null;
      }
      if (response.body.isEmpty) return <String, dynamic>{};
      return jsonDecode(response.body) as Map<String, dynamic>;
    } catch (error) {
      // Never let a registration or an analytics ping take the app down.
      debugPrint('PushWave: $path failed: $error');
      return null;
    }
  }

  /// Registers this device. Idempotent on the token, so calling it on every
  /// launch is fine and is what keeps `last_active_at` current.
  Future<Map<String, dynamic>?> register({
    required String token,
    String? appVersion,
    String? deviceModel,
    String? deviceOs,
    String? language,
    String? country,
    String? externalId,
    Map<String, String>? tags,
  }) {
    return _post('/api/v1/subscriptions', {
      'app_id': appId,
      'platform': Platform.isIOS ? 'ios' : 'android',
      'token': token,
      if (appVersion != null) 'app_version': appVersion,
      if (deviceModel != null) 'device_model': deviceModel,
      if (deviceOs != null) 'device_os': deviceOs,
      if (language != null) 'language': language,
      if (country != null) 'country': country,
      if (externalId != null) 'external_id': externalId,
      if (tags != null && tags.isNotEmpty) 'tags': tags,
      'sdk_version': '0.1.0-flutter',
    });
  }

  /// Partial update of an already-registered device.
  Future<void> update(String subscriptionId, Map<String, dynamic> fields) async {
    await _post('/api/v1/subscriptions/$subscriptionId', {'app_id': appId, ...fields});
  }

  /// `session`, `received` or `clicked`.
  Future<void> event(String subscriptionId, String type, {String? messageId}) async {
    await _post('/api/v1/events', {
      'app_id': appId,
      'subscription_id': subscriptionId,
      'type': type,
      if (messageId != null) 'message_id': messageId,
    });
  }
}
