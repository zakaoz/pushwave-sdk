package com.pushwave.sdk;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Invisible activity the notification tap lands on.
 *
 * It fills {@link PushWave.Data} / {@link PushWave.AdditionalData}, reports the
 * click, then hands control to the app's own callback — which is what keeps the
 * host app's existing "open this screen" code working unchanged.
 */
public class PushWaveOpenActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PushWave.ensureInitialized(this);

        Intent intent = getIntent();
        String messageId = extra(intent, PushWave.EXTRA_ID);

        PushWave.Data.id = messageId;
        PushWave.Data.title = extra(intent, PushWave.EXTRA_TITLE);
        PushWave.Data.message = extra(intent, PushWave.EXTRA_MESSAGE);
        PushWave.Data.bigImage = extra(intent, PushWave.EXTRA_IMAGE);
        PushWave.Data.launchUrl = extra(intent, PushWave.EXTRA_LAUNCH_URL);

        PushWave.AdditionalData.uniqueId = extra(intent, PushWave.EXTRA_UNIQUE_ID);
        PushWave.AdditionalData.postId = extra(intent, PushWave.EXTRA_POST_ID);
        PushWave.AdditionalData.link = extra(intent, PushWave.EXTRA_LINK);

        if (!messageId.isEmpty()) PushWave.reportEvent("clicked", messageId);

        PushWave.notifyOpened();
        finish();
    }

    private static String extra(Intent intent, String key) {
        String value = intent == null ? null : intent.getStringExtra(key);
        return value == null ? "" : value;
    }
}
