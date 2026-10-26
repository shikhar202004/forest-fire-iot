# 🌲 Forest Fire Detection System — IoT Project

A **real-time IoT-based forest fire detection system** with live sensor simulation, WebSocket streaming, fire risk scoring, and an interactive web dashboard.

---

## 📁 Project Structure

```
forest-fire-detection-iot/
├── server.js                  # Node.js backend — WebSocket + REST API
├── package.json               # Dependencies
├── public/
│   └── index.html             # Live dashboard (HTML/CSS/JS)
├── sensors/
│   ├── arduino_sensor_node.ino  # Arduino ESP8266 firmware
│   └── sensor_simulator.py    # Python sensor simulator
└── README.md
```

---

## 🚀 Quick Start

### 1. Install Dependencies
```bash
npm install
```

### 2. Run the Server
```bash
npm start
```

### 3. Open the Dashboard
```
http://localhost:3000
```

The system immediately starts simulating 5 IoT sensor zones. Use the **"Simulate Fire"** button on any zone to watch a fire event unfold in real time!

---

## 🏗️ System Architecture

```
┌─────────────────────────────────────────────────┐
│                 IoT Sensor Nodes                 │
│  [DHT22]  [MQ-2]  [MQ-135]  [Flame]  [Wind]    │
│  Temp/Hum  Smoke    CO2     Detector  Speed      │
└──────────────┬──────────────────────────────────┘
               │  WiFi / MQTT / HTTP POST
               ▼
┌─────────────────────────────────────────────────┐
│           Node.js Backend Server                │
│  ┌─────────────┐  ┌──────────────────────────┐ │
│  │  REST API   │  │    Fire Risk Engine       │ │
│  │  /api/...   │  │  Score 0–100 calculation  │ │
│  └─────────────┘  └──────────────────────────┘ │
│  ┌─────────────────────────────────────────────┐│
│  │        WebSocket Broadcast (2s interval)    ││
│  └─────────────────────────────────────────────┘│
└──────────────┬──────────────────────────────────┘
               │  WebSocket ws://
               ▼
┌─────────────────────────────────────────────────┐
│            Web Dashboard (Browser)              │
│  • 5 Sensor Zone Cards with live metrics        │
│  • Risk Score Ring Meter (max zone)             │
│  • Temperature & Risk Score Trend Charts        │
│  • Alert Log with push notifications            │
│  • Fire simulation & sensor reset controls      │
└─────────────────────────────────────────────────┘
```

---

## 🔥 Fire Risk Scoring Algorithm

The system calculates a **risk score (0–100)** for each sensor zone using weighted factors:

| Parameter       | Threshold       | Score |
|-----------------|-----------------|-------|
| Temperature     | ≥ 80°C          | +40   |
| Temperature     | 65–79°C         | +30   |
| Temperature     | 50–64°C         | +20   |
| Temperature     | 38–49°C         | +10   |
| Humidity        | ≤ 15%           | +25   |
| Humidity        | 15–25%          | +18   |
| Humidity        | 25–40%          | +10   |
| Smoke (ppm)     | ≥ 80 ppm        | +25   |
| Smoke (ppm)     | 50–79 ppm       | +18   |
| Smoke (ppm)     | 30–49 ppm       | +10   |
| CO2             | ≥ 1500 ppm      | +10   |
| CO2             | 1000–1499 ppm   | +7    |
| **Flame sensor**| Detected        | +20   |

### Alert Levels

| Score   | Level | Label         | Action                    |
|---------|-------|---------------|---------------------------|
| 0–24    | 0     | NORMAL        | Monitor                   |
| 25–49   | 1     | MODERATE      | Increased monitoring      |
| 50–74   | 2     | HIGH RISK     | Alert rangers, prepare    |
| 75–100  | 3     | FIRE DETECTED | Emergency response!       |

---

## 📡 IoT Sensor Hardware

### Required Components (per node)
| Component        | Model    | Purpose                       |
|------------------|----------|-------------------------------|
| Microcontroller  | Arduino Uno + ESP8266 or ESP32 | Processing + WiFi |
| Temperature/Humidity | DHT22 | Temp & relative humidity |
| Smoke Sensor     | MQ-2     | Smoke, LPG, CO detection      |
| Air Quality      | MQ-135   | CO2, NH3, benzene detection   |
| Flame Sensor     | IR Module| Infrared flame detection      |
| Anemometer       | Analog   | Wind speed measurement        |
| Power            | LiPo + Solar panel | Off-grid operation |
| Enclosure        | IP67 case | Weatherproof outdoor mounting |

### Wiring Diagram
```
Arduino Uno / ESP32
┌──────────────────────────────────┐
│  Pin 4    → DHT22 Data           │
│  Pin 7    → Flame Sensor (OUT)   │
│  Pin 11   → Buzzer (+)           │
│  Pin 12   → Alert LED (+)        │
│  Pin 13   → Status LED (+)       │
│  A0       → MQ-2 Analog Out      │
│  A1       → MQ-135 Analog Out    │
│  A2       → Anemometer Signal    │
│  5V       → Sensor VCC rails     │
│  GND      → All GND rails        │
└──────────────────────────────────┘
```

---

## 🌐 REST API Reference

| Method | Endpoint                              | Description              |
|--------|---------------------------------------|--------------------------|
| GET    | `/api/sensors`                        | All sensor current data  |
| GET    | `/api/alerts`                         | Alert history log        |
| POST   | `/api/sensors/:id/simulate-fire`      | Toggle fire simulation   |
| POST   | `/api/sensors/:id/reset`              | Reset sensor to normal   |

### Example — Trigger fire simulation
```bash
curl -X POST http://localhost:3000/api/sensors/S001/simulate-fire \
     -H "Content-Type: application/json" \
     -d '{"active": true}'
```

### WebSocket Events
Connect to `ws://localhost:3000` and receive JSON messages:

```json
{
  "type": "sensorUpdate",
  "sensors": [{
    "id": "S001",
    "temperature": 75.2,
    "humidity": 18.5,
    "smoke": 62.1,
    "co2": 1240,
    "riskScore": 78,
    "alertLevel": 3,
    "alertLabel": "FIRE DETECTED",
    "history": [...]
  }],
  "alerts": [...]
}
```

---

## 🐍 Python Simulator

Test the system without hardware:

```bash
pip install requests
python sensors/sensor_simulator.py
```

Simulates all 5 sensor nodes with realistic data drift, and randomly triggers fire events for demo purposes.

---

## 🔧 Configuration

Edit the top of `server.js` to customize:
- **SENSORS** array — add/remove zones, change lat/lng for map integration
- **Update interval** — currently 2 seconds (line: `setInterval`)
- **Risk thresholds** — tune the `calculateFireRisk()` function

---

## 🌍 Deployment

### Local Network (Raspberry Pi)
```bash
# On Raspberry Pi
npm install
npm start  # Runs on port 3000

# Access from any device on the network
http://raspberrypi.local:3000
```

### Cloud (Heroku / Railway)
```bash
# Set PORT environment variable — server auto-uses it
PORT=80 npm start
```

---

## 📊 Technologies Used

| Layer      | Technology                          |
|------------|-------------------------------------|
| Hardware   | Arduino/ESP32, DHT22, MQ-2, MQ-135 |
| Firmware   | C++ (Arduino framework)             |
| Backend    | Node.js, Express, WebSocket (ws)    |
| Frontend   | HTML5, CSS3, Vanilla JS, Chart.js   |
| Simulator  | Python 3, requests, threading       |
| Protocol   | HTTP REST + WebSocket               |

---

## 📄 License

MIT License — Free to use, modify, and distribute.
