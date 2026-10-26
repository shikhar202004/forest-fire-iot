const express = require('express');
const http = require('http');
const WebSocket = require('ws');
const path = require('path');

// ─── FIREBASE ───
const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

const app = express();
const server = http.createServer(app);
const wss = new WebSocket.Server({ server });

app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// ─── ZONES ───
const SENSORS = [
  { id: 'S001', name: 'Zone Alpha',   location: 'North Sector', zone: 'alpha',   lat: 0, lng: 0 },
  { id: 'S002', name: 'Zone Beta',    location: 'South Sector', zone: 'beta',    lat: 0, lng: 0 },
  { id: 'S003', name: 'Zone Gamma',   location: 'East Sector',  zone: 'gamma',   lat: 0, lng: 0 },
  { id: 'S004', name: 'Zone Delta',   location: 'West Sector',  zone: 'delta',   lat: 0, lng: 0 },
  { id: 'S005', name: 'Zone Epsilon', location: 'Central',      zone: 'epsilon', lat: 0, lng: 0 },
];

// ─── STATE ───
const sensorState = {};
const alertLog = [];

SENSORS.forEach(s => {
  sensorState[s.id] = {
    temperature: 30,
    humidity:    50,
    smoke:       10,
    co2:         400,
    windSpeed:   5,
    riskScore:   0,
    alertLevel:  0,
    alertLabel:  '✅ NORMAL',
    lastReading: new Date().toISOString(),
    history:     [],
    manualFire:  false
  };
});

// ─── FCM COOLDOWN (60s per sensor) ───
const fcmCooldown = {};

// ─── FIRE LOGIC ───
function calculateFireRisk(data) {
  let score = 0;

  if (data.temperature >= 80) score += 40;
  else if (data.temperature >= 65) score += 30;
  else if (data.temperature >= 50) score += 20;
  else if (data.temperature >= 38) score += 10;

  if (data.humidity <= 15) score += 25;
  else if (data.humidity <= 25) score += 18;
  else if (data.humidity <= 40) score += 10;

  if (data.smoke >= 80) score += 25;
  else if (data.smoke >= 50) score += 18;
  else if (data.smoke >= 30) score += 10;

  if (data.co2 >= 1500) score += 10;
  else if (data.co2 >= 700) score += 5;

  return Math.min(score, 100);
}

function getAlertLevel(score) {
  if (score >= 75) return { level: 3, label: '🔥 FIRE DETECTED' };
  if (score >= 50) return { level: 2, label: '⚠️ HIGH RISK' };
  if (score >= 25) return { level: 1, label: '⚡ MODERATE' };
  return { level: 0, label: '✅ NORMAL' };
}

// ─── SEND FCM PUSH NOTIFICATION ───
async function sendFCMNotification(sensorObj, risk, alert) {
  const now = Date.now();
  const lastSent = fcmCooldown[sensorObj.id] || 0;
  if (now - lastSent < 60000) return;
  fcmCooldown[sensorObj.id] = now;

  try {
    const tokensSnapshot = await db.collection('fcm_tokens').get();
    if (tokensSnapshot.empty) {
      console.log('📱 No FCM tokens found');
      return;
    }

    const tokens = [];
    tokensSnapshot.forEach(doc => {
      if (doc.data().token) tokens.push(doc.data().token);
    });

    if (tokens.length === 0) return;

    const emoji = risk >= 75 ? '🔥' : risk >= 50 ? '⚠️' : '⚡';

    const message = {
      notification: {
        title: `${emoji} ${sensorObj.name} — ${alert.label}`,
        body:  `Risk: ${risk}/100 | Location: ${sensorObj.location}`
      },
      data: {
        sensorId:   sensorObj.id,
        riskScore:  String(risk),
        alertLabel: alert.label
      },
      tokens: tokens
    };

    const response = await admin.messaging().sendEachForMulticast(message);
    console.log(`📱 FCM sent: ${response.successCount} success, ${response.failureCount} failed`);

  } catch (err) {
    console.error('❌ FCM failed:', err.message);
  }
}

// ─── WRITE TO FIRESTORE ───
async function writeToFirestore(sensorObj, st, risk, alert) {
  try {
    const reading = {
      sensorId:    sensorObj.id,
      sensorName:  sensorObj.name,
      location:    sensorObj.location,
      temperature: Number(st.temperature.toFixed(1)),
      humidity:    Number(st.humidity.toFixed(1)),
      smoke:       Number(st.smoke.toFixed(1)),
      co2:         Number(st.co2.toFixed(0)),
      lat:         sensorObj.lat || 0,
      lng:         sensorObj.lng || 0,
      riskScore:   risk,
      alertLevel:  alert.level,
      alertLabel:  alert.label,
      timestamp:   admin.firestore.FieldValue.serverTimestamp()
    };

    console.log('📝 Writing to Firestore:', sensorObj.id, '| Risk:', risk);

    await db.collection('readings').doc(sensorObj.id).set(reading);

    if (risk >= 25) {
      await db.collection('alerts').add({
        ...reading,
        label: alert.label
      });
      console.log('🚨 Alert written for:', sensorObj.name);
    }

  } catch (err) {
    console.error('❌ Firestore write failed:', err.message);
  }
}

// ─── HISTORY HELPER ───
function pushHistory(id) {
  const st = sensorState[id];
  st.history.push({
    time:        new Date().toLocaleTimeString(),
    temperature: Number(st.temperature.toFixed(1)),
    humidity:    Number(st.humidity.toFixed(1)),
    smoke:       Number(st.smoke.toFixed(1)),
    co2:         Number(st.co2.toFixed(0)),
    riskScore:   st.riskScore
  });
  if (st.history.length > 20) st.history.shift();
}

// ─── ALERT LOG HELPER ───
function pushAlert(sensorObj, st, risk, alert) {
  const color = risk >= 75 ? '#ff2244' : risk >= 50 ? '#ff6600' : '#ffcc00';
  const now   = new Date().toLocaleTimeString();

  const last = alertLog.find(a => a.sensor === sensorObj.name);
  if (last && last.time === now && last.riskScore === risk) return;

  alertLog.unshift({
    sensor:      sensorObj.name,
    location:    sensorObj.location,
    label:       alert.label,
    riskScore:   risk,
    temperature: Number(st.temperature.toFixed(1)),
    smoke:       Number(st.smoke.toFixed(1)),
    time:        now,
    color
  });

  if (alertLog.length > 20) alertLog.pop();

  // ─── SEND FCM PUSH NOTIFICATION ───
  sendFCMNotification(sensorObj, risk, alert);
}

// ─── CLEAR ALERTS FOR ONE SENSOR ───
function clearAlerts(sensorName) {
  for (let i = alertLog.length - 1; i >= 0; i--) {
    if (alertLog[i].sensor === sensorName) alertLog.splice(i, 1);
  }
}

// ─── BROADCAST ───
function broadcast() {
  const payload = SENSORS.map(s => ({
    ...s,
    ...sensorState[s.id]
  }));

  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(JSON.stringify({
        type:    'sensorUpdate',
        sensors: payload,
        alerts:  alertLog.slice(0, 10)
      }));
    }
  });
}

// ─── RECEIVE DATA (ESP32) ───
app.post('/api/ingest', (req, res) => {

  const data = req.body;
  const id   = data.sensorId;

  console.log('📡 Received from ESP32:', id, data);

  if (!sensorState[id]) {
    console.log('❌ Unknown sensor id:', id);
    return res.status(404).json({ error: 'Sensor not found' });
  }

  if (sensorState[id].manualFire) {
    return res.json({ skipped: true });
  }

  sensorState[id].temperature = data.temperature;
  sensorState[id].humidity    = data.humidity;
  sensorState[id].smoke       = data.smoke;
  sensorState[id].co2         = data.co2;

  if (data.lat != null && data.lng != null) {
    const sensor = SENSORS.find(x => x.id === id);
    if (sensor) {
      sensor.lat = data.lat;
      sensor.lng = data.lng;
    }
  }

  const risk  = calculateFireRisk(sensorState[id]);
  const alert = getAlertLevel(risk);

  sensorState[id].riskScore   = risk;
  sensorState[id].alertLevel  = alert.level;
  sensorState[id].alertLabel  = alert.label;
  sensorState[id].lastReading = new Date().toISOString();

  pushHistory(id);

  const sensorObj = SENSORS.find(x => x.id === id);

  if (!sensorObj) {
    console.log('❌ sensorObj not found for id:', id);
    return res.status(404).json({ error: 'Sensor config not found' });
  }

  // ─── ALWAYS WRITE TO FIRESTORE ───
  writeToFirestore(sensorObj, sensorState[id], risk, alert);

  if (risk >= 25) {
    pushAlert(sensorObj, sensorState[id], risk, alert);
  }

  broadcast();
  res.json({ success: true });
});

// ─── SIMULATE FIRE ───
app.post('/api/sensors/:id/simulate-fire', (req, res) => {

  const { id } = req.params;

  if (!sensorState[id]) {
    return res.status(404).json({ error: 'Sensor not found' });
  }

  sensorState[id].manualFire  = true;
  sensorState[id].temperature = 120;
  sensorState[id].humidity    = 10;
  sensorState[id].smoke       = 3000;
  sensorState[id].co2         = 3000;

  const risk  = calculateFireRisk(sensorState[id]);
  const alert = getAlertLevel(risk);

  sensorState[id].riskScore   = risk;
  sensorState[id].alertLevel  = alert.level;
  sensorState[id].alertLabel  = alert.label;
  sensorState[id].lastReading = new Date().toISOString();

  pushHistory(id);

  const sensorObj = SENSORS.find(x => x.id === id);
  pushAlert(sensorObj, sensorState[id], risk, alert);

  writeToFirestore(sensorObj, sensorState[id], risk, alert);

  console.log('🔥 FIRE SIMULATED:', id);

  broadcast();
  res.json({ success: true });
});

// ─── RESET ───
app.post('/api/sensors/:id/reset', (req, res) => {

  const { id } = req.params;

  if (!sensorState[id]) {
    return res.status(404).json({ error: 'Sensor not found' });
  }

  const sensorObj = SENSORS.find(x => x.id === id);
  if (sensorObj) clearAlerts(sensorObj.name);

  sensorState[id].manualFire  = false;
  sensorState[id].temperature = 30;
  sensorState[id].humidity    = 50;
  sensorState[id].smoke       = 10;
  sensorState[id].co2         = 400;
  sensorState[id].riskScore   = 0;
  sensorState[id].alertLevel  = 0;
  sensorState[id].alertLabel  = '✅ NORMAL';
  sensorState[id].history     = [];

  writeToFirestore(sensorObj, sensorState[id], 0, { level: 0, label: '✅ NORMAL' });

  console.log('🔄 RESET:', id);

  broadcast();
  res.json({ success: true });
});

// ─── SIMULATION TICK (every 3s for S002–S005) ───
setInterval(() => {

  SENSORS.forEach(s => {
    if (s.id === 'S001') return;

    const st = sensorState[s.id];
    const previousRisk = st.riskScore;

    if (st.manualFire) {
      pushHistory(s.id);
      return; // Don't keep writing fire to Firestore every 3s
    }

    st.temperature = Math.max(20, Math.min(35, st.temperature + (Math.random() - 0.5)));
    st.smoke       = Math.max(5,  Math.min(20, st.smoke       + (Math.random() - 0.5) * 2));
    st.co2         = Math.max(380,Math.min(500,st.co2         + (Math.random() - 0.5) * 10));
    st.humidity    = Math.max(40, Math.min(70, st.humidity    + (Math.random() - 0.5)));

    const risk  = calculateFireRisk(st);
    const alert = getAlertLevel(risk);

    st.riskScore   = risk;
    st.alertLevel  = alert.level;
    st.alertLabel  = alert.label;
    st.lastReading = new Date().toISOString();

    pushHistory(s.id);

    // Only write to Firestore if risk changed
    if (risk !== previousRisk) {
      writeToFirestore(s, st, risk, alert);
    }

    if (risk >= 25) {
      pushAlert(s, st, risk, alert);
    }
  });

  broadcast();

}, 3000);

// ─── WS CONNECTION ───
wss.on('connection', ws => {
  const payload = SENSORS.map(s => ({
    ...s,
    ...sensorState[s.id]
  }));

  ws.send(JSON.stringify({
    type:    'init',
    sensors: payload,
    alerts:  alertLog.slice(0, 10)
  }));
});

// ─── START ───
const PORT = 3000;

server.listen(PORT, () => {
  console.log(`🌲 Server running at http://localhost:${PORT}`);
});