package com.pushwave.sdk;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything the SDK has to remember between launches. */
final class PushWavePrefs {

    private static final String FILE = "pushwave";

    private static final String KEY_APP_ID = "app_id";
    private static final String KEY_SERVICE_URL = "service_url";
    private static final String KEY_SUBSCRIPTION_ID = "subscription_id";
    private static final String KEY_TOPIC = "topic";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_EXTERNAL_ID = "external_id";

    private final SharedPreferences prefs;

    PushWavePrefs(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    String getAppId() {
        return prefs.getString(KEY_APP_ID, "");
    }

    String getServiceUrl() {
        return prefs.getString(KEY_SERVICE_URL, "");
    }

    void setConfig(String appId, String serviceUrl) {
        prefs.edit().putString(KEY_APP_ID, appId).putString(KEY_SERVICE_URL, serviceUrl).apply();
    }

    String getSubscriptionId() {
        return prefs.getString(KEY_SUBSCRIPTION_ID, "");
    }

    void setSubscriptionId(String value) {
        prefs.edit().putString(KEY_SUBSCRIPTION_ID, value).apply();
    }

    String getTopic() {
        return prefs.getString(KEY_TOPIC, "");
    }

    void setTopic(String value) {
        prefs.edit().putString(KEY_TOPIC, value).apply();
    }

    String getToken() {
        return prefs.getString(KEY_TOKEN, "");
    }

    void setToken(String value) {
        prefs.edit().putString(KEY_TOKEN, value).apply();
    }

    boolean isEnabled() {
        return prefs.getBoolean(KEY_ENABLED, true);
    }

    void setEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply();
    }

    String getExternalId() {
        return prefs.getString(KEY_EXTERNAL_ID, "");
    }

    void setExternalId(String value) {
        prefs.edit().putString(KEY_EXTERNAL_ID, value).apply();
    }
}
