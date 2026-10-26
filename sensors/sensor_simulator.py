#!/usr/bin/env python3
"""
Forest Fire Detection System — Python IoT Sensor Simulator
==========================================================
Simulates multiple IoT sensor nodes sending data via HTTP
to the Node.js server. Use this to test without hardware.

Usage:
  pip install requests
  python sensor_simulator.py
"""

import random
import time
import math
import json
import threading
import requests
from datetime import datetime

SERVER_URL = "http://localhost:3000/api/ingest"

SENSOR_NODES = [
    {"id": "S001", "name": "Zone Alpha",   "location": "North Sector"},
    {"id": "S002", "name": "Zone Beta",    "location": "South Sector"},
    {"id": "S003", "name": "Zone Gamma",   "location": "East Sector"},
    {"id": "S004", "name": "Zone Delta",   "location": "West Sector"},
    {"id": "S005", "name": "Zone Epsilon", "location": "Central"},
]

class SensorNode:
    def __init__(self, config):
        self.id       = config["id"]
        self.name     = config["name"]
        self.location = config["location"]

        # Initial baseline values
        self.temperature = 28 + random.uniform(0, 5)
        self.humidity    = 55 + random.uniform(0, 10)
        self.smoke       = 10 + random.uniform(0, 5)
        self.co2         = 400 + random.uniform(0, 50)
        self.wind_speed  = 5 + random.uniform(0, 3)
        self.fire_sim    = False
        self.tick        = 0

    def drift(self, val, mn, mx, delta):
        return min(mx, max(mn, val + (random.random() - 0.5) * delta))

    def read(self):
        self.tick += 1
        if self.fire_sim:
            self.temperature = min(120, self.temperature + random.uniform(1, 5))
            self.humidity    = max(5,   self.humidity    - random.uniform(0.5, 2.5))
            self.smoke       = min(100, self.smoke       + random.uniform(2, 10))
            self.co2         = min(2000, self.co2        + random.uniform(20, 120))
        else:
            self.temperature = self.drift(self.temperature, 20, 45, 1.5)
            self.humidity    = self.drift(self.humidity,    20, 90, 2.0)
            self.smoke       = self.drift(self.smoke,        5, 25, 1.0)
            self.co2         = self.drift(self.co2,        380, 600, 20)
        self.wind_speed  = self.drift(self.wind_speed, 0, 20, 1.0)

        return {
            "sensorId":    self.id,
            "name":        self.name,
            "location":    self.location,
            "temperature": round(self.temperature, 1),
            "humidity":    round(self.humidity, 1),
            "smoke":       round(self.smoke, 1),
            "co2":         round(self.co2, 0),
            "windSpeed":   round(self.wind_speed, 1),
            "flameDetected": self.fire_sim and self.smoke > 60,
            "timestamp":   datetime.now().isoformat(),
        }

    def calculate_risk(self, data):
        score = 0
        t = data["temperature"]
        h = data["humidity"]
        s = data["smoke"]
        c = data["co2"]

        if t >= 80:   score += 40
        elif t >= 65: score += 30
        elif t >= 50: score += 20
        elif t >= 38: score += 10

        if h <= 15:   score += 25
        elif h <= 25: score += 18
        elif h <= 40: score += 10

        if s >= 80:   score += 25
        elif s >= 50: score += 18
        elif s >= 30: score += 10

        if c >= 1500: score += 10
        elif c >= 700: score += 5

        return min(score, 100)


def run_sensor(node):
    """Thread function for a single sensor node."""
    print(f"[{node.id}] {node.name} online at {node.location}")

    while True:
        data = node.read()
        risk = node.calculate_risk(data)
        data["riskScore"] = risk

        alert = "NORMAL"
        if risk >= 75:   alert = "🔥 FIRE DETECTED"
        elif risk >= 50: alert = "⚠️  HIGH RISK"
        elif risk >= 25: alert = "⚡ MODERATE"

        print(f"[{node.id}] Temp:{data['temperature']}°C  Hum:{data['humidity']}%  "
              f"Smoke:{data['smoke']}ppm  Risk:{risk}  {alert}")

        try:
            resp = requests.post(SERVER_URL, json=data, timeout=2)
            if resp.status_code != 200:
                print(f"[{node.id}] Server error: {resp.status_code}")
        except requests.exceptions.ConnectionError:
            pass  # Server might not be running in standalone mode

        # Randomly trigger fire simulation for demo (5% chance every 30s)
        if node.tick % 15 == 0 and not node.fire_sim and random.random() < 0.05:
            node.fire_sim = True
            print(f"\n🔥 [{node.id}] FIRE SIMULATION TRIGGERED at {node.name}!\n")
        # Auto-reset after ~40 readings
        if node.fire_sim and node.tick % 40 == 0:
            node.fire_sim = False
            node.temperature = 28 + random.uniform(0, 5)
            node.humidity    = 55 + random.uniform(0, 10)
            node.smoke       = 10 + random.uniform(0, 5)
            node.co2         = 400 + random.uniform(0, 50)
            print(f"\n✅ [{node.id}] Fire extinguished — {node.name} reset to normal\n")

        time.sleep(2)


if __name__ == "__main__":
    print("=" * 60)
    print("  🌲 Forest Fire Detection — IoT Sensor Simulator")
    print("=" * 60)
    print(f"  Server: {SERVER_URL}")
    print(f"  Nodes:  {len(SENSOR_NODES)}")
    print("=" * 60)
    print()

    nodes = [SensorNode(cfg) for cfg in SENSOR_NODES]
    threads = [threading.Thread(target=run_sensor, args=(n,), daemon=True) for n in nodes]

    for t in threads:
        t.start()

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n\n🛑 Simulator stopped.")
