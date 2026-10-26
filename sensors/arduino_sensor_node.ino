#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <DHT.h>
#include <TinyGPS++.h>
#include <HardwareSerial.h>

// ───────── WIFI ─────────
const char* WIFI_SSID = "Shikharr";
const char* WIFI_PASSWORD = "shikhar123";

// ───────── SERVER ─────────
const char* SERVER_URL = "http://10.198.211.15:3000/api/ingest";

// ───────── SENSOR ─────────
#define DHT_PIN 15
#define DHT_TYPE DHT11

#define MQ2_PIN 34
#define MQ135_PIN 35

#define SAMPLE_INTERVAL 3000

DHT dht(DHT_PIN, DHT_TYPE);

// ───────── GPS ─────────
TinyGPSPlus gps;
HardwareSerial gpsSerial(1);

// ───────── GLOBALS ─────────
WiFiClient client;
HTTPClient http;

unsigned long lastSample = 0;

int lastMQ2 = 0;
int lastMQ135 = 0;

// 🔥 Simulated DHT values
float fakeTemp = 28.0;
float fakeHum  = 60.0;

// ───────── MQ READ ─────────
int readMQ(int pin) {
  int sum = 0;
  for (int i = 0; i < 10; i++) {
    sum += analogRead(pin);
    delay(10);
  }
  return sum / 10;
}

bool isValid(int val) {
  if (val <= 0) return false;
  if (val == 2047) return false;
  if (val > 4095) return false;
  return true;
}

// ───────── SETUP ─────────
void setup() {
  Serial.begin(115200);

  dht.begin();
  delay(2000);

  // GPS Serial (RX, TX)
  gpsSerial.begin(9600, SERIAL_8N1, 16, 17);

  // WiFi
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting WiFi");

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\n✅ Connected");
  Serial.println(WiFi.localIP());

  Serial.println("⏳ Warming MQ...");
  delay(60000);
}

// ───────── LOOP ─────────
void loop() {

  // Read GPS continuously
  while (gpsSerial.available()) {
    gps.encode(gpsSerial.read());
  }

  if (millis() - lastSample > SAMPLE_INTERVAL) {

    lastSample = millis();

    // ─── DHT (Simulated) ───
    fakeTemp += random(-5, 6) * 0.1;
    fakeHum  += random(-5, 6) * 0.1;

    if (fakeTemp < 20) fakeTemp = 20;
    if (fakeTemp > 45) fakeTemp = 45;

    if (fakeHum < 30) fakeHum = 30;
    if (fakeHum > 90) fakeHum = 90;

    float temp = fakeTemp;
    float hum  = fakeHum;

    // ─── MQ ───
    int mq2 = readMQ(MQ2_PIN) - 80;
    int mq135 = readMQ(MQ135_PIN) - 500;

    if (mq2 < 0) mq2 = 0;
    if (mq135 < 0) mq135 = 0;

    if (isValid(mq2)) lastMQ2 = mq2;
    if (isValid(mq135)) lastMQ135 = mq135;

    if (lastMQ2 == 0 || lastMQ135 == 0) {
      Serial.println("⚠️ MQ unstable");
      return;
    }

    // ─── Fire logic ───
    if (lastMQ2 > 1500) {
      temp += 5;
    }

    // ─── GPS ───
    double lat = 0;
    double lng = 0;

    if (gps.location.isValid()) {
      lat = gps.location.lat();
      lng = gps.location.lng();
    } else {
      Serial.println("📡 Waiting for GPS...");
    }

    // ─── PRINT ───
    Serial.println("------ DATA ------");
    Serial.print("Temp: "); Serial.println(temp);
    Serial.print("Hum: "); Serial.println(hum);
    Serial.print("MQ2: "); Serial.println(lastMQ2);
    Serial.print("MQ135: "); Serial.println(lastMQ135);
    Serial.print("Lat: "); Serial.println(lat, 6);
    Serial.print("Lng: "); Serial.println(lng, 6);

    // ─── SEND ───
    sendData(temp, hum, lastMQ2, lastMQ135, lat, lng);
  }
}

// ───────── SEND ─────────
void sendData(float t, float h, int smoke, int air, double lat, double lng) {

  if (WiFi.status() != WL_CONNECTED) return;

  StaticJsonDocument<256> doc;

  doc["sensorId"] = "S001";
  doc["temperature"] = t;
  doc["humidity"] = h;
  doc["smoke"] = smoke;
  doc["co2"] = air;
  doc["lat"] = lat;
  doc["lng"] = lng;

  String payload;
  serializeJson(doc, payload);

  http.begin(client, SERVER_URL);
  http.addHeader("Content-Type", "application/json");

  int res = http.POST(payload);

  Serial.print("📡 Server Response: ");
  Serial.println(res);

  http.end();
}
