# Call Bamber — Backend + Admin Panel

License gate + one device login + force update, ek hi server pe.

## Kya-kya hai

**App ke liye API:**
- `GET /api/version` — update check (force/optional, maintenance mode, dialog ka text)
- `POST /api/activate` — license key se login, device bandh ho jata hai
- `POST /api/verify` — token check (app resume pe)
- `GET /api/health` — server zinda hai ya nahi

**Admin panel:** `/` ya `/admin` kholo, password daalo.

## Admin me kya control hai

- Force update on/off
- Optional update on/off
- "Baad Me" button dikhana ya nahi
- Maintenance mode on/off
- Latest version code / name, minimum version code
- Update dialog ka title, message, button text
- Update URL (Play Store / APK link)
- License keys — banao, block, delete, device reset
- Activity log

## Render pe deploy

1. Is folder ko GitHub pe push karo
2. Render pe **New → Web Service** → repo chuno
3. Build Command: `npm install`
4. Start Command: `node server.js`
5. Environment variables set karo:
   - `ADMIN_PASSWORD` — apna strong password
   - `JWT_SECRET` — koi lamba random string
   - `DATA_DIR` — `/data` (Render pe persistent disk lagao `/data` pe, warna restart pe data ud jayega)

## Android app me kya add karna hai

`android/` folder me `LicenseManager.java` aur `UpdateChecker.java` hain — dono copy karke apne app me daalo, aur `AppConfig.java` me apna server URL daalo.

App start hone pe:

```java
// 1. update check
UpdateChecker.check(this, new UpdateChecker.Callback() {
    @Override public void onForceUpdate(String title, String msg, String btn, String url) {
        // non-cancelable dialog, sirf Update button
    }
    @Override public void onOptionalUpdate(String title, String msg, String btn, String later, String url) {
        // cancelable dialog
    }
    @Override public void onOk() { /* app chalao */ }
    @Override public void onMaintenance(String msg) { /* maintenance screen */ }
});

// 2. license check
LicenseManager lm = new LicenseManager(this);
if (!lm.hasValidToken()) {
    // license screen dikhao -> lm.activate(key, callback)
} else {
    lm.verify(token -> { /* ok to app kholo */ });
}
```

## License key format

`PRANAV-XXXX-XXXX-XXXX` — admin panel khud banata hai.
