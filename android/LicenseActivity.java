package com.callbamber.license;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * LicenseActivity — license key maangne wali screen.
 *
 * Ise apne app ki pehli screen banao (ya MainActivity ke start pe check karo).
 * Token valid ho to MainActivity khul jayegi.
 */
public class LicenseActivity extends Activity {

    private EditText keyInput;
    private Button activateBtn;
    private ProgressBar progress;
    private TextView errorText;
    private LicenseManager lm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lm = new LicenseManager(this);

        // Agar token pehle se hai to verify karke seedha aage bhejo
        if (lm.hasValidToken()) {
            progressOnly();
            lm.verify(new LicenseManager.VerifyCallback() {
                @Override public void onValid() { goToApp(); }
                @Override public void onInvalid(String msg) { buildUi(msg); }
            });
            return;
        }
        buildUi(null);
    }

    private void progressOnly() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0F1117);
        ProgressBar p = new ProgressBar(this);
        root.addView(p);
        setContentView(root);
    }

    private void buildUi(String initialError) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.setBackgroundColor(0xFF0F1117);

        TextView title = new TextView(this);
        title.setText("Call Bamber");
        title.setTextSize(26);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("License key daalein");
        sub.setTextSize(14);
        sub.setTextColor(0xFF8B93A7);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, dp(28));
        root.addView(sub);

        keyInput = new EditText(this);
        keyInput.setHint("PRANAV-XXXX-XXXX-XXXX");
        keyInput.setTextSize(15);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        keyInput.setTextColor(0xFFFFFFFF);
        keyInput.setHintTextColor(0xFF5A6274);
        keyInput.setPadding(dp(14), dp(14), dp(14), dp(14));
        keyInput.setBackgroundColor(0xFF171A23);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(16);
        root.addView(keyInput, lp);

        errorText = new TextView(this);
        errorText.setTextSize(13);
        errorText.setTextColor(0xFFF87171);
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(0, 0, 0, dp(12));
        if (initialError != null) errorText.setText(initialError);
        root.addView(errorText);

        activateBtn = new Button(this);
        activateBtn.setText("Activate");
        activateBtn.setTextSize(15);
        activateBtn.setTextColor(0xFFFFFFFF);
        activateBtn.setBackgroundColor(0xFF4F7CFF);
        root.addView(activateBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        progress = new ProgressBar(this);
        progress.setVisibility(ProgressBar.GONE);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.topMargin = dp(16);
        root.addView(progress, plp);

        activateBtn.setOnClickListener(v -> doActivate());
        setContentView(root);
    }

    private void doActivate() {
        String key = keyInput.getText().toString().trim();
        if (key.isEmpty()) {
            errorText.setText("License key daalein");
            return;
        }
        activateBtn.setEnabled(false);
        progress.setVisibility(ProgressBar.VISIBLE);
        errorText.setText("");

        lm.activate(key, new LicenseManager.Callback() {
            @Override public void onSuccess(String token, String plan) {
                runOnUiThread(() -> goToApp());
            }
            @Override public void onError(String message) {
                runOnUiThread(() -> {
                    activateBtn.setEnabled(true);
                    progress.setVisibility(ProgressBar.GONE);
                    errorText.setText(message);
                });
            }
        });
    }

    private void goToApp() {
        // Apni main activity ka naam yahan daalo
        try {
            Intent i = new Intent(this, Class.forName(getPackageName() + ".MainActivity"));
            startActivity(i);
            finish();
        } catch (Exception e) {
            finish();
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() { /* license ke bina bahar nahi */ }
}
