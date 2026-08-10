package com.pushwave.sdk;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Receives PushWave messages from FCM.
 *
 * Declared in the SDK manifest so the host app does not need a
 * FirebaseMessagingService of its own — two services listening for
 * MESSAGING_EVENT means only one of them wins, which is exactly the ambiguity
 * that makes "my notifications stopped arriving" so hard to debug.
 */
public class PushWaveMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        PushWave.ensureInitialized(this);
        // Tokens rotate on reinstall, restore and app-data clear. Re-registering
        // here is what stops the device list filling up with dead tokens.
        PushWave.registerToken(token);
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        PushWave.ensureInitialized(this);

        Map<String, String> data = remoteMessage.getData();
        if (data.isEmpty()) return;

        if (!new PushWavePrefs(this).isEnabled()) {
            Log.d(PushWave.TAG, "notifications are disabled on this device, dropping message");
            return;
        }

        PushWaveNotification.show(this, data);

        String messageId = data.get("pw_message_id");
        if (messageId != null && !messageId.isEmpty()) {
            PushWave.reportEvent("received", messageId);
        }
    }
}
