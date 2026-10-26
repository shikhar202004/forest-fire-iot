package com.divyanshi.forestfiredetect;

public class SensorModel {
    public String id, name, location, alertLabel;
    public double temperature, humidity, smoke, co2, lat, lng;
    public int riskScore, alertLevel;
    public boolean manualFire;

    public SensorModel() {}
}