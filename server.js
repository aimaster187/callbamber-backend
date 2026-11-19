// CallHomewor Backend — license gate + one device login + force update + web admin panel
const express = require('express');
const crypto = require('crypto');
const path = require('path');
const fs = require('fs');
const jwt = require('jsonwebtoken');

const app = express();
app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: true }));

const PORT = process.env.PORT || 3000;
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'pranav@admin123';
const JWT_SECRET = process.env.JWT_SECRET || 'callhomewor-secret-change-me';
const DATA_DIR = process.env.DATA_DIR || path.join(__dirname, 'data');
const DB_FILE = path.join(DATA_DIR, 'db.json');

// ---------- tiny JSON DB ----------
function defaultDB() {
  return {
    config: {
      forceUpdate: true,
      optionalUpdate: true,
      maintenance: false,
      maintenanceMsg: 'App abhi maintenance me hai. Thodi der baad try karein.',
      latestVersionCode: 2,
      latestVersionName: '1.1',
      minVersionCode: 1,
      updateTitle: 'Naya Update Aa Gaya!',
      updateMessage: 'App ka naya version available hai. Better experience ke liye abhi update karein.',
      updateButtonText: 'Update Now',
      laterButtonText: 'Baad Me',
      updateUrl: 'https://play.google.com/store/apps/details?id=com.callhomewor.app',
      showLaterButton: true
    },
    keys: [],
    logs: []
  };
}

function loadDB() {
  try {
    if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
    if (!fs.existsSync(DB_FILE)) {
      fs.writeFileSync(DB_FILE, JSON.stringify(defaultDB(), null, 2));
    }
    const raw = JSON.parse(fs.readFileSync(DB_FILE, 'utf8'));
    const d = defaultDB();
    return { config: { ...d.config, ...(raw.config || {}) }, keys: raw.keys || [], logs: raw.logs || [] };
  } catch (e) {
    return defaultDB();
  }
}

let db = loadDB();
function saveDB() {
  try {
    if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });
    fs.writeFileSync(DB_FILE, JSON.stringify(db, null, 2));
  } catch (e) { console.error('saveDB failed', e.message); }
}

function logEvent(type, detail) {
  db.logs.unshift({ t: Date.now(), type, detail });
  if (db.logs.length > 500) db.logs.length = 500;
  saveDB();
}

// ---------- helpers ----------
function genKey() {
  const part = () => crypto.randomBytes(2).toString('hex').toUpperCase();
  return `PRANAV-${part()}-${part()}-${part()}`;
}

function adminAuth(req, res, next) {
  const h = req.headers.authorization || '';
  const token = h.startsWith('Bearer ') ? h.slice(7) : (req.query.token || '');
  try {
    jwt.verify(token, JWT_SECRET);
    return next();
  } catch (e) {
    return res.status(401).json({ ok: false, error: 'unauthorized' });
  }
}

// ---------- public API (app calls these) ----------
app.get('/api/health', (req, res) => res.json({ ok: true, service: 'callhomewor', time: Date.now() }));

// version / update check
app.get('/api/version', (req, res) => {
  const c = db.config;
  res.json({
    ok: true,
    maintenance: c.maintenance,
    maintenanceMsg: c.maintenanceMsg,
    latestVersionCode: c.latestVersionCode,
    latestVersionName: c.latestVersionName,
    minVersionCode: c.minVersionCode,
    forceUpdate: c.forceUpdate,
    optionalUpdate: c.optionalUpdate,
    updateTitle: c.updateTitle,
    updateMessage: c.updateMessage,
    updateButtonText: c.updateButtonText,
    laterButtonText: c.laterButtonText,
    showLaterButton: c.showLaterButton,
    updateUrl: c.updateUrl
  });
});

// license activate (one device)
app.post('/api/activate', (req, res) => {
  const { licenseKey, deviceId, deviceName } = req.body || {};
  if (!licenseKey || !deviceId) {
    return res.status(400).json({ ok: false, error: 'licenseKey aur deviceId zaroori hai' });
  }
  const key = db.keys.find(k => k.key.toUpperCase() === String(licenseKey).trim().toUpperCase());
  if (!key) return res.status(404).json({ ok: false, error: 'License key galat hai' });
  if (key.blocked) return res.status(403).json({ ok: false, error: 'Ye license block kar di gayi hai' });
  if (key.expiresAt && Date.now() > key.expiresAt) {
    return res.status(403).json({ ok: false, error: 'License expire ho gayi hai' });
  }
  if (key.deviceId && key.deviceId !== deviceId) {
    return res.status(409).json({ ok: false, error: 'Ye key kisi aur device pe active hai. Admin se device reset karwayein.' });
  }
  if (!key.deviceId) {
    key.deviceId = deviceId;
    key.deviceName = deviceName || 'Unknown device';
    key.activatedAt = Date.now();
    logEvent('activate', `${key.key} -> ${key.deviceName}`);
  }
  key.lastSeen = Date.now();
  saveDB();

  const token = jwt.sign({ key: key.key, deviceId }, JWT_SECRET, { expiresIn: '365d' });
  res.json({ ok: true, token, expiresAt: key.expiresAt || null, plan: key.plan || 'standard' });
});

// verify token (app opens / resume)
app.post('/api/verify', (req, res) => {
  const { token, deviceId } = req.body || {};
  try {
    const p = jwt.verify(token, JWT_SECRET);
    const key = db.keys.find(k => k.key === p.key);
    if (!key) return res.status(404).json({ ok: false, error: 'License nahi mili' });
    if (key.blocked) return res.status(403).json({ ok: false, error: 'License block hai' });
    if (key.expiresAt && Date.now() > key.expiresAt) return res.status(403).json({ ok: false, error: 'License expire' });
    if (key.deviceId && deviceId && key.deviceId !== deviceId) {
      return res.status(409).json({ ok: false, error: 'Device mismatch' });
    }
    key.lastSeen = Date.now();
    saveDB();
    res.json({ ok: true, config: { maintenance: db.config.maintenance, latestVersionCode: db.config.latestVersionCode } });
  } catch (e) {
    res.status(401).json({ ok: false, error: 'Token invalid ya expire' });
  }
});

// ---------- admin API ----------
app.post('/api/admin/login', (req, res) => {
  const { password } = req.body || {};
  if (!password || password !== ADMIN_PASSWORD) {
    return res.status(401).json({ ok: false, error: 'Password galat hai' });
  }
  const token = jwt.sign({ admin: true }, JWT_SECRET, { expiresIn: '7d' });
  res.json({ ok: true, token });
});

app.get('/api/admin/config', adminAuth, (req, res) => res.json({ ok: true, config: db.config }));

app.post('/api/admin/config', adminAuth, (req, res) => {
  const allowed = Object.keys(db.config);
  for (const k of Object.keys(req.body || {})) {
    if (allowed.includes(k)) db.config[k] = req.body[k];
  }
  saveDB();
  logEvent('config', 'Settings update ki gayi');
  res.json({ ok: true, config: db.config });
});

app.get('/api/admin/keys', adminAuth, (req, res) => res.json({ ok: true, keys: db.keys }));

app.post('/api/admin/keys', adminAuth, (req, res) => {
  const { count, days, plan, note } = req.body || {};
  const n = Math.min(Math.max(parseInt(count) || 1, 1), 100);
  const made = [];
  for (let i = 0; i < n; i++) {
    let k;
    do { k = genKey(); } while (db.keys.some(x => x.key === k));
    const rec = {
      key: k,
      plan: plan || 'standard',
      note: note || '',
      createdAt: Date.now(),
      expiresAt: days ? Date.now() + Number(days) * 86400000 : null,
      deviceId: null,
      deviceName: null,
      blocked: false
    };
    db.keys.push(rec);
    made.push(rec);
  }
  saveDB();
  logEvent('keys', `${n} key banaayi`);
  res.json({ ok: true, keys: made });
});

app.post('/api/admin/keys/:key/block', adminAuth, (req, res) => {
  const k = db.keys.find(x => x.key === req.params.key);
  if (!k) return res.status(404).json({ ok: false, error: 'not found' });
  k.blocked = !!req.body.blocked;
  saveDB();
  logEvent('block', `${k.key} -> ${k.blocked ? 'blocked' : 'unblocked'}`);
  res.json({ ok: true, key: k });
});

app.post('/api/admin/keys/:key/reset-device', adminAuth, (req, res) => {
  const k = db.keys.find(x => x.key === req.params.key);
  if (!k) return res.status(404).json({ ok: false, error: 'not found' });
  k.deviceId = null;
  k.deviceName = null;
  k.activatedAt = null;
  saveDB();
  logEvent('reset', `${k.key} device reset`);
  res.json({ ok: true, key: k });
});

app.delete('/api/admin/keys/:key', adminAuth, (req, res) => {
  const i = db.keys.findIndex(x => x.key === req.params.key);
  if (i < 0) return res.status(404).json({ ok: false, error: 'not found' });
  const [removed] = db.keys.splice(i, 1);
  saveDB();
  logEvent('delete', `${removed.key} delete`);
  res.json({ ok: true });
});

app.get('/api/admin/logs', adminAuth, (req, res) => res.json({ ok: true, logs: db.logs.slice(0, 200) }));

// ---------- admin panel (single page) ----------
app.get('/', (req, res) => res.sendFile(path.join(__dirname, 'admin.html')));
app.get('/admin', (req, res) => res.sendFile(path.join(__dirname, 'admin.html')));

app.listen(PORT, () => console.log(`CallHomewor backend chal raha hai port ${PORT} pe`));
