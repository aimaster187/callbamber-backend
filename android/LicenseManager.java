package com.callbamber.license;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * LicenseManager — license key gate + one device login.
 *
 * Kaam kaise karta hai:
 * 1. User license key daalta hai (PRANAV-XXXX-XXXX-XXXX)
 * 2. App apna device ID server ko bhejta hai
 * 3. Server key ko us device se bandh deta hai aur token deta hai
 * 4. Token SharedPreferences me save hota hai
 * 5. Dusre phone pe wahi key -> server reject kar deta hai
 *
 * Admin panel se "Reset" dabane pe device khul jata hai.
 */
public class LicenseManager {

    public interface Callback {
        void onSuccess(String token, String plan);
        void onError(String message);
    }

    public interface VerifyCallback {
        void onValid();
        void onInvalid(String message);
    }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    private final Context ctx;
    private final SharedPreferences prefs;

    public LicenseManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = this.ctx.getSharedPreferences(AppConfig.PREFS, Context.MODE_PRIVATE);
    }

    // ---------- device id ----------

    /** Har phone ka apna unique id — Settings.Secure.ANDROID_ID */
    public String getDeviceId() {
        String id = Settings.Secure.getString(ctx.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null || id.isEmpty()) id = "unknown-device";
        return id;
    }

    /** Device ka naam (model) — admin panel me dikhta hai */
    public String getDeviceName() {
        String man = android.os.Build.MANUFACTURER == null ? "" : android.os.Build.MANUFACTURER;
        String model = android.os.Build.MODEL == null ? "" : android.os.Build.MODEL;
        return (man + " " + model).trim();
    }

    // ---------- token ----------

    public String getToken() {
        return prefs.getString("license_token", null);
    }

    public boolean hasValidToken() {
        String t = getToken();
        return t != null && !t.isEmpty();
    }

    public void clearToken() {
        prefs.edit().remove("license_token").remove("license_plan").apply();
    }

    public String getPlan() {
        return prefs.getString("license_plan", "standard");
    }

    // ---------- activate ----------

    /**
     * License key se activate karo.
     * Main thread pe call kar sakte ho — network background me chalta hai.
     */
    public void activate(final String licenseKey, final Callback cb) {
        POOL.execute(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("licenseKey", licenseKey == null ? "" : licenseKey.trim().toUpperCase());
                    body.put("deviceId", getDeviceId());
                    body.put("deviceName", getDeviceName());

                    String raw = UpdateChecker.httpPost(AppConfig.BASE_URL + "/api/activate",
                            body.toString());
                    final JSONObject j = new JSONObject(raw);

                    if (j.optBoolean("ok", false)) {
                        final String token = j.optString("token", "");
                        final String plan = j.optString("plan", "standard");
                        prefs.edit()
                                .putString("license_token", token)
                                .putString("license_plan", plan)
                                .apply();
                        cb.onSuccess(token, plan);
                    } else {
                        cb.onError(j.optString("error", "Activation fail ho gaya"));
                    }
                } catch (final Exception e) {
                    cb.onError("Server se baat nahi hui. Internet check karein.");
                }
            }
        });
    }

    // ---------- verify ----------

    /** App resume pe token check karo — key block hui to app band */
    public void verify(final VerifyCallback cb) {
        final String token = getToken();
        if (token == null || token.isEmpty()) {
            cb.onInvalid("License nahi mili");
            return;
        }
        POOL.execute(new Runnable() {
            @Override public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("token", token);
                    body.put("deviceId", getDeviceId());

                    String raw = UpdateChecker.httpPost(AppConfig.BASE_URL + "/api/verify",
                            body.toString());
                    JSONObject j = new JSONObject(raw);

                    if (j.optBoolean("ok", false)) {
                        cb.onValid();
                    } else {
                        String err = j.optString("error", "License invalid");
                        // Device mismatch ya block -> token hata do
                        if (err.contains("block") || err.contains("mismatch")
                                || err.contains("expire") || err.contains("nahi mili")) {
                            clearToken();
                        }
                        cb.onInvalid(err);
                    }
                } catch (Exception e) {
                    // Internet nahi hai -> user ko rokna nahi, token rakho
                    cb.onValid();
                }
            }
        });
    }
}
