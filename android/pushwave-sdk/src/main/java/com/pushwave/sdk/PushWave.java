package com.pushwave.sdk;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * PushWave client SDK.
 *
 * The public surface deliberately mirrors the OneSignal wrapper it replaces —
 * same builder shape, same EXTRA_* keys, same {@link Data} / {@link AdditionalData}
 * holders — so migrating an existing app is an import change plus two lines,
 * not a rewrite of the notification handling.
 *
 * <pre>
 * new PushWave.Builder(this)
 *     .setAppId(getString(R.string.pushwave_app_id))
 *     .setServiceUrl(getString(R.string.pushwave_service_url))
 *     .build(() -> { ... open your activity ... });
 * </pre>
 */
public final class PushWave {

    public static final String TAG = "PushWave";

    public static final String EXTRA_ID = "pw_extra_id";
    public static final String EXTRA_TITLE = "pw_extra_title";
    public static final String EXTRA_MESSAGE = "pw_extra_message";
    public static final String EXTRA_IMAGE = "pw_extra_image";
    public static final String EXTRA_LAUNCH_URL = "pw_extra_launch_url";
    public static final String EXTRA_UNIQUE_ID = "pw_extra_unique_id";
    public static final String EXTRA_POST_ID = "pw_extra_post_id";
    public static final String EXTRA_LINK = "pw_extra_link";

    static final int NOTIFICATION_PERMISSION_REQUEST = 4531;

    /** Invoked when the user taps a notification. */
    public interface OnNotificationOpened {
        void onOpened();
    }

    /** Fields of the notification that was just opened. */
    public static final class Data {
        public static String id = "";
        public static String title = "";
        public static String message = "";
        public static String bigImage = "";
        public static String launchUrl = "";

        private Data() {
        }
    }

    /** Custom key/value payload of the notification that was just opened. */
    public static final class AdditionalData {
        public static String uniqueId = "";
        public static String postId = "";
        public static String link = "";

        private AdditionalData() {
        }
    }

    private static Context applicationContext;
    private static OnNotificationOpened openedCallback;

    private PushWave() {
    }

    // ------------------------------------------------------------------ builder

    public static final class Builder {

        private final Context context;
        private String appId = "";
        private String serviceUrl = "";

        public Builder(Context context) {
            this.context = context;
        }

        public Builder setAppId(String appId) {
            this.appId = appId == null ? "" : appId.trim();
            return this;
        }

        /** Base URL of your PushWave server, e.g. {@code https://push.example.com}. */
        public Builder setServiceUrl(String serviceUrl) {
            this.serviceUrl = serviceUrl == null ? "" : serviceUrl.trim();
            return this;
        }

        /** Call from {@code Application.onCreate()}. */
        public void build(OnNotificationOpened callback) {
            applicationContext = context.getApplicationContext();
            openedCallback = callback;

            if (appId.isEmpty() || serviceUrl.isEmpty()) {
                Log.e(TAG, "setAppId() and setServiceUrl() are both required");
                return;
            }

            new PushWavePrefs(applicationContext).setConfig(appId, serviceUrl);
            registerDevice();
        }

        /**
         * Asks for POST_NOTIFICATIONS on Android 13+. Call from an Activity;
         * a no-op on older versions, where the permission is implicit.
         */
        public void requestNotificationPermission() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
            if (!(context instanceof Activity)) {
                Log.w(TAG, "requestNotificationPermission() needs an Activity context");
                return;
            }
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) {
                return;
            }
            ActivityCompat.requestPermissions(
                    (Activity) context,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST);
        }
    }

    // ------------------------------------------------------------ public API

    /** Attaches a tag used for segmentation, e.g. {@code setTag("team", "barca")}. */
    public static void setTag(String key, String value) {
        JSONObject tags = new JSONObject();
        try {
            tags.put(key, value);
        } catch (Exception error) {
            Log.w(TAG, "invalid tag: " + error.getMessage());
            return;
        }
        updateSubscription(field("tags", tags));
    }

    public static void setTags(Map<String, String> values) {
        updateSubscription(field("tags", new JSONObject(values)));
    }

    /** Links this device to your own user id, so you can target it directly. */
    public static void setExternalId(String externalId) {
        if (applicationContext == null) return;
        new PushWavePrefs(applicationContext).setExternalId(externalId);
        updateSubscription(field("external_id", externalId));
    }

    /**
     * Turns notifications on or off for this device. Also leaves the broadcast
     * topic, so an opted-out device is excluded even from "send to everyone".
     */
    public static void setEnabled(boolean enabled) {
        if (applicationContext == null) return;
        PushWavePrefs prefs = new PushWavePrefs(applicationContext);
        prefs.setEnabled(enabled);

        String topic = prefs.getTopic();
        if (!topic.isEmpty()) {
            if (enabled) FirebaseMessaging.getInstance().subscribeToTopic(topic);
            else FirebaseMessaging.getInstance().unsubscribeFromTopic(topic);
        }

        updateSubscription(field("enabled", enabled));
    }

    public static boolean isEnabled() {
        return applicationContext != null && new PushWavePrefs(applicationContext).isEnabled();
    }

    /** This device's id on the PushWave server, or an empty string before registration. */
    public static String getSubscriptionId() {
        return applicationContext == null ? "" : new PushWavePrefs(applicationContext).getSubscriptionId();
    }

    /**
     * Call from your launcher Activity's {@code onCreate}, before you read the
     * notification extras.
     *
     * When Android's own tray draws the notification — which is what keeps it
     * visible on a phone that killed the app process — the tap opens the
     * launcher Activity with the raw message data as extras, and
     * {@link PushWaveOpenActivity} never runs. This reads those extras, fills
     * {@link Data} / {@link AdditionalData}, reports the click, and copies the
     * values onto the intent under the EXTRA_* keys, so the rest of the app's
     * handling works exactly as it does for an SDK-drawn notification.
     *
     * @return true when the Activity was opened from one of our notifications
     */
    public static boolean handleLaunchIntent(Activity activity) {
        if (activity == null) return false;
        ensureInitialized(activity);

        Intent intent = activity.getIntent();
        if (intent == null || intent.getExtras() == null) return false;

        String messageId = intent.getStringExtra("pw_message_id");
        String postId = intent.getStringExtra("post_id");
        // Not one of ours: a plain launch, or another sender's payload.
        if (messageId == null && postId == null) return false;
        // PushWaveOpenActivity already handled this one; do not count it twice.
        if (intent.hasExtra(EXTRA_ID)) return true;

        Data.id = orEmpty(messageId);
        Data.title = orEmpty(intent.getStringExtra("title"));
        Data.message = orEmpty(intent.getStringExtra("message"));
        Data.bigImage = orEmpty(intent.getStringExtra("big_image"));
        Data.launchUrl = orEmpty(intent.getStringExtra("launch_url"));

        AdditionalData.uniqueId = orEmpty(intent.getStringExtra("unique_id"));
        AdditionalData.postId = orEmpty(postId);
        AdditionalData.link = orEmpty(intent.getStringExtra("link"));

        intent.putExtra(EXTRA_ID, Data.id);
        intent.putExtra(EXTRA_TITLE, Data.title);
        intent.putExtra(EXTRA_MESSAGE, Data.message);
        intent.putExtra(EXTRA_IMAGE, Data.bigImage);
        intent.putExtra(EXTRA_LAUNCH_URL, Data.launchUrl);
        intent.putExtra(EXTRA_UNIQUE_ID, AdditionalData.uniqueId);
        intent.putExtra(EXTRA_POST_ID, AdditionalData.postId);
        intent.putExtra(EXTRA_LINK, AdditionalData.link);

        if (!Data.id.isEmpty()) reportEvent("clicked", Data.id);
        return true;
    }

    // -------------------------------------------------------------- internals

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    static Context context() {
        return applicationContext;
    }

    /**
     * Rebuilds state after the process was killed and restarted by an incoming
     * message, when {@code Application.onCreate()} may not have run our builder.
     */
    static void ensureInitialized(Context context) {
        if (applicationContext == null) applicationContext = context.getApplicationContext();
    }

    static void notifyOpened() {
        OnNotificationOpened callback = openedCallback;
        if (callback != null) callback.onOpened();
        else Log.w(TAG, "notification opened but no callback is registered");
    }

    /** Fetches the FCM token and registers this device with the server. */
    static void registerDevice() {
        if (applicationContext == null) return;
        PushWavePrefs prefs = new PushWavePrefs(applicationContext);

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null) {
                Log.e(TAG, "could not obtain an FCM token", task.getException());
                return;
            }
            registerToken(task.getResult());
        });
    }

    static void registerToken(String token) {
        if (applicationContext == null) return;
        PushWavePrefs prefs = new PushWavePrefs(applicationContext);
        if (prefs.getAppId().isEmpty()) return;

        JSONObject body = new JSONObject();
        try {
            body.put("app_id", prefs.getAppId());
            body.put("platform", "android");
            body.put("token", token);
            body.put("device_model", Build.MANUFACTURER + " " + Build.MODEL);
            body.put("device_os", "Android " + Build.VERSION.RELEASE);
            body.put("app_version", appVersion());
            body.put("sdk_version", BuildConfig.PUSHWAVE_SDK_VERSION);
            body.put("language", Locale.getDefault().getLanguage());
            body.put("country", Locale.getDefault().getCountry());
            body.put("timezone_offset", TimeZone.getDefault().getRawOffset() / 1000);
            if (!prefs.getExternalId().isEmpty()) body.put("external_id", prefs.getExternalId());
        } catch (Exception error) {
            Log.e(TAG, "could not build the registration payload", error);
            return;
        }

        PushWaveApi.post(prefs.getServiceUrl(), "/api/v1/subscriptions", body, (response, error) -> {
            if (error != null || response == null) return;

            prefs.setToken(token);
            prefs.setSubscriptionId(response.optString("subscription_id", prefs.getSubscriptionId()));

            String topic = response.optString("topic", "");
            if (!topic.isEmpty()) {
                prefs.setTopic(topic);
                // One request on the server reaches every device on this topic,
                // which is what makes a broadcast to the whole install base cheap.
                if (prefs.isEnabled()) FirebaseMessaging.getInstance().subscribeToTopic(topic);
            }

            reportEvent("session", null);
        });
    }

    /** Reports a device event: session, received or clicked. */
    static void reportEvent(String type, String messageId) {
        if (applicationContext == null) return;
        PushWavePrefs prefs = new PushWavePrefs(applicationContext);
        if (prefs.getSubscriptionId().isEmpty() || prefs.getServiceUrl().isEmpty()) return;

        JSONObject body = new JSONObject();
        try {
            body.put("app_id", prefs.getAppId());
            body.put("subscription_id", prefs.getSubscriptionId());
            body.put("type", type);
            if (messageId != null && !messageId.isEmpty()) body.put("message_id", messageId);
        } catch (Exception error) {
            return;
        }

        PushWaveApi.post(prefs.getServiceUrl(), "/api/v1/events", body, null);
    }

    private static void updateSubscription(JSONObject fields) {
        if (applicationContext == null || fields == null) return;
        PushWavePrefs prefs = new PushWavePrefs(applicationContext);
        if (prefs.getSubscriptionId().isEmpty()) {
            Log.w(TAG, "device is not registered yet, ignoring update");
            return;
        }

        try {
            fields.put("app_id", prefs.getAppId());
        } catch (Exception error) {
            return;
        }

        PushWaveApi.post(
                prefs.getServiceUrl(),
                "/api/v1/subscriptions/" + prefs.getSubscriptionId(),
                fields,
                null);
    }

    private static JSONObject field(String key, Object value) {
        JSONObject object = new JSONObject();
        try {
            object.put(key, value);
        } catch (Exception error) {
            return null;
        }
        return object;
    }

    private static String appVersion() {
        try {
            return applicationContext.getPackageManager()
                    .getPackageInfo(applicationContext.getPackageName(), 0).versionName;
        } catch (Exception error) {
            return "";
        }
    }
}
