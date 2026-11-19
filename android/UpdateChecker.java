package com.callbamber.license;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * UpdateChecker — app khulte hi server se version poochta hai.
 *
 * Force update   -> dialog band nahi hota, sirf "Update" button
 * Optional       -> "Update" + "Baad Me"
 * Maintenance    -> poora app band, sirf message
 *
 * Sab kuch admin panel se control hota hai — app me kuch change karne ki zarurat nahi.
 */
public class UpdateChecker {

    public interface Callback {
        /** Purana version + force on -> non-cancelable dialog dikhao */
        void onForceUpdate(String title, String message, String buttonText, String url);

        /** Naya version available par force off -> cancelable dialog */
        void onOptionalUpdate(String title, String message, String buttonText,
                              String laterText, String url);

        /** Sab theek hai -> app chalao */
        void onOk();

        /** Maintenance mode */
        void onMaintenance(String message);

        /** Server se baat nahi hui -> app chalao (offline me user ko rokna nahi) */
        void onError(String error);
    }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /**
     * @param activity current activity (dialog dikhane ke liye)
     * @param cb       callback
     */
    public static void check(final Activity activity, final Callback cb) {
        final int currentVersionCode = getVersionCode(activity);

        POOL.execute(new Runnable() {
            @Override public void run() {
                try {
                    String raw = httpGet(AppConfig.BASE_URL + "/api/version");
                    final JSONObject j = new JSONObject(raw);

                    MAIN.post(new Runnable() {
                        @Override public void run() {
                            try {
                                // 1. Maintenance mode sabse pehle
                                if (j.optBoolean("maintenance", false)) {
                                    cb.onMaintenance(j.optString("maintenanceMsg",
                                            "App abhi maintenance me hai. Thodi der baad try karein."));
                                    return;
                                }

                                int latest = j.optInt("latestVersionCode", currentVersionCode);
                                int minVersion = j.optInt("minVersionCode", 0);
                                boolean forceOn = j.optBoolean("forceUpdate", false);
                                boolean optionalOn = j.optBoolean("optionalUpdate", true);

                                String title = j.optString("updateTitle", "Naya Update Aa Gaya!");
                                String msg = j.optString("updateMessage",
                                        "App ka naya version available hai.");
                                String btn = j.optString("updateButtonText", "Update Now");
                                String later = j.optString("laterButtonText", "Baad Me");
                                String url = j.optString("updateUrl", "");
                                boolean showLater = j.optBoolean("showLaterButton", true);

                                // 2. Force update — version latest se kam hai aur force on hai
                                if (forceOn && currentVersionCode < latest) {
                                    cb.onForceUpdate(title, msg, btn, url);
                                    return;
                                }

                                // 3. Minimum version se neeche -> hamesha force
                                if (currentVersionCode < minVersion) {
                                    cb.onForceUpdate(title, msg, btn, url);
                                    return;
                                }

                                // 4. Optional update
                                if (optionalOn && currentVersionCode < latest) {
                                    if (showLater) {
                                        cb.onOptionalUpdate(title, msg, btn, later, url);
                                    } else {
                                        cb.onForceUpdate(title, msg, btn, url);
                                    }
                                    return;
                                }

                                cb.onOk();
                            } catch (Exception e) {
                                cb.onError(e.getMessage());
                            }
                        }
                    });
                } catch (final Exception e) {
                    MAIN.post(new Runnable() {
                        @Override public void run() { cb.onError(e.getMessage()); }
                    });
                }
            }
        });
    }

    /** Force update dialog — cancel nahi hota */
    public static void showForceUpdateDialog(final Activity activity, String title,
                                             String message, String buttonText,
                                             final String url) {
        if (activity == null || activity.isFinishing()) return;
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(buttonText, (d, w) -> {
                    openUrl(activity, url);
                    activity.finishAffinity();
                });
        AlertDialog d = b.create();
        d.setCanceledOnTouchOutside(false);
        d.show();
    }

    /** Optional update dialog — "Baad Me" ka option */
    public static void showOptionalUpdateDialog(final Activity activity, String title,
                                                String message, String buttonText,
                                                String laterText, final String url,
                                                final Runnable onLater) {
        if (activity == null || activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(true)
                .setPositiveButton(buttonText, (d, w) -> openUrl(activity, url))
                .setNegativeButton(laterText, (d, w) -> {
                    if (onLater != null) onLater.run();
                })
                .show();
    }

    /** Maintenance screen */
    public static void showMaintenanceDialog(final Activity activity, String message) {
        if (activity == null || activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle("Maintenance")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK", (d, w) -> activity.finishAffinity())
                .show();
    }

    public static void openUrl(Context ctx, String url) {
        try {
            if (url == null || url.trim().isEmpty()) return;
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) { }
    }

    // ---------- helpers ----------

    public static int getVersionCode(Context ctx) {
        try {
            return ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String getVersionName(Context ctx) {
        try {
            return ctx.getPackageManager()
                    .getPackageInfo(ctx.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0";
        }
    }

    static String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        try {
            int code = c.getResponseCode();
            java.io.InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    static String httpPost(String urlStr, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try {
            OutputStream os = c.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.close();
            int code = c.getResponseCode();
            java.io.InputStream is = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }
}
