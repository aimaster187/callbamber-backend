package com.callbamber.license;

import android.app.Activity;
import android.os.Bundle;

/**
 * MainActivity ka example — update check + license check dono.
 *
 * Apni asli MainActivity me sirf onCreate wala hissa copy karo.
 */
public class MainActivityExample extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ===== STEP 1: UPDATE CHECK =====
        UpdateChecker.check(this, new UpdateChecker.Callback() {

            @Override
            public void onForceUpdate(String title, String message, String buttonText, String url) {
                // Purana version + force on -> app band, sirf Update button
                UpdateChecker.showForceUpdateDialog(
                        MainActivityExample.this, title, message, buttonText, url);
            }

            @Override
            public void onOptionalUpdate(String title, String message, String buttonText,
                                         String laterText, String url) {
                // Naya version hai par force nahi -> "Baad Me" ka option
                UpdateChecker.showOptionalUpdateDialog(
                        MainActivityExample.this, title, message, buttonText, laterText, url,
                        () -> checkLicense());
            }

            @Override
            public void onOk() {
                // Update ki zarurat nahi -> ab license check
                checkLicense();
            }

            @Override
            public void onMaintenance(String message) {
                UpdateChecker.showMaintenanceDialog(MainActivityExample.this, message);
            }

            @Override
            public void onError(String error) {
                // Server se baat nahi hui -> user ko rokna nahi
                checkLicense();
            }
        });
    }

    // ===== STEP 2: LICENSE CHECK =====
    private void checkLicense() {
        LicenseManager lm = new LicenseManager(this);

        if (!lm.hasValidToken()) {
            // License nahi hai -> license screen
            startActivity(new android.content.Intent(this, LicenseActivity.class));
            finish();
            return;
        }

        lm.verify(new LicenseManager.VerifyCallback() {
            @Override
            public void onValid() {
                // Sab theek -> asli app content kholo
                // setContentView(R.layout.activity_main);
            }

            @Override
            public void onInvalid(String message) {
                // Key block hui / device mismatch -> license screen
                startActivity(new android.content.Intent(
                        MainActivityExample.this, LicenseActivity.class));
                finish();
            }
        });
    }
}
