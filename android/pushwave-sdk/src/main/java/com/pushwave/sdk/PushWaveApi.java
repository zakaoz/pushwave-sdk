package com.pushwave.sdk;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tiny HTTP client for the PushWave REST API.
 *
 * Deliberately built on HttpURLConnection and org.json: the SDK drops into apps
 * that already pin their own OkHttp/Retrofit/Gson versions, and adding ours
 * would risk a conflict for three endpoints.
 */
final class PushWaveApi {

    interface Callback {
        void onResult(JSONObject response, Exception error);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int TIMEOUT_MS = 15000;

    private PushWaveApi() {
    }

    /**
     * Every call is a POST. HttpURLConnection refuses PATCH outright, so the
     * API exposes the update endpoint under both verbs.
     */
    static void post(String baseUrl, String path, JSONObject body, Callback callback) {
        request(baseUrl, path, "POST", body, callback);
    }

    private static void request(String baseUrl, String path, String method, JSONObject body, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(trimTrailingSlash(baseUrl) + path);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(TIMEOUT_MS);
                connection.setReadTimeout(TIMEOUT_MS);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestMethod(method);

                byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(payload);
                }

                int status = connection.getResponseCode();
                String responseBody = readAll(status >= 400 ? connection.getErrorStream() : connection.getInputStream());

                if (status >= 400) {
                    deliver(callback, null, new Exception("HTTP " + status + ": " + responseBody));
                    return;
                }

                deliver(callback, responseBody.isEmpty() ? new JSONObject() : new JSONObject(responseBody), null);
            } catch (Exception error) {
                Log.w(PushWave.TAG, "request to " + path + " failed: " + error.getMessage());
                deliver(callback, null, error);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static void deliver(Callback callback, JSONObject response, Exception error) {
        if (callback != null) callback.onResult(response, error);
    }

    private static String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
