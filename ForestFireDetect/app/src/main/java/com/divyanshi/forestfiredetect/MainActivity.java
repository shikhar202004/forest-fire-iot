package com.divyanshi.forestfiredetect;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    FirebaseFirestore db;
    FirebaseAuth mAuth;
    RecyclerView rvSensors;
    SensorAdapter adapter;
    List<SensorModel> sensorList = new ArrayList<>();
    TextView tvActiveAlerts, tvMaxRisk, tvTotalSensors;
    SwipeRefreshLayout swipeRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db    = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        rvSensors      = findViewById(R.id.rvSensors);
        tvActiveAlerts = findViewById(R.id.tvActiveAlerts);
        tvMaxRisk      = findViewById(R.id.tvMaxRisk);
        tvTotalSensors = findViewById(R.id.tvTotalSensors);
        swipeRefresh   = findViewById(R.id.swipeRefresh);

        rvSensors.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SensorAdapter(sensorList);
        rvSensors.setAdapter(adapter);

        // ─── Request notification permission (Android 13+) ───
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        listenToSensors();

        swipeRefresh.setOnRefreshListener(() -> {
            sensorList.clear();
            adapter.notifyDataSetChanged();
            listenToSensors();
            swipeRefresh.setRefreshing(false);
        });

        findViewById(R.id.btnAlerts).setOnClickListener(v ->
                startActivity(new Intent(this, AlertHistoryActivity.class))
        );

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });
    }

    void listenToSensors() {
        String[] sensorIds = {"S001", "S002", "S003", "S004", "S005"};

        for (String sensorId : sensorIds) {
            db.collection("readings")
                    .document(sensorId)
                    .addSnapshotListener((doc, e) -> {
                        if (e != null || doc == null || !doc.exists()) return;

                        SensorModel s = new SensorModel();
                        s.id          = doc.getString("sensorId");
                        s.name        = doc.getString("sensorName");
                        s.location    = doc.getString("location");
                        s.alertLabel  = doc.getString("alertLabel");
                        s.temperature = doc.getDouble("temperature") != null ? doc.getDouble("temperature") : 0;
                        s.humidity    = doc.getDouble("humidity")    != null ? doc.getDouble("humidity")    : 0;
                        s.smoke       = doc.getDouble("smoke")       != null ? doc.getDouble("smoke")       : 0;
                        s.co2         = doc.getDouble("co2")         != null ? doc.getDouble("co2")         : 0;
                        s.lat         = doc.getDouble("lat")         != null ? doc.getDouble("lat")         : 0;
                        s.lng         = doc.getDouble("lng")         != null ? doc.getDouble("lng")         : 0;
                        s.riskScore   = doc.getLong("riskScore")     != null ? doc.getLong("riskScore").intValue()  : 0;
                        s.alertLevel  = doc.getLong("alertLevel")    != null ? doc.getLong("alertLevel").intValue() : 0;

                        // Update or add sensor in list
                        boolean found = false;
                        for (int i = 0; i < sensorList.size(); i++) {
                            if (sensorList.get(i).id != null &&
                                    sensorList.get(i).id.equals(s.id)) {
                                sensorList.set(i, s);
                                found = true;
                                break;
                            }
                        }
                        if (!found) sensorList.add(s);

                        // Update stats
                        int maxRisk    = 0;
                        int alertCount = 0;
                        for (SensorModel sensor : sensorList) {
                            if (sensor.riskScore > maxRisk) maxRisk = sensor.riskScore;
                            if (sensor.alertLevel >= 2) alertCount++;
                        }

                        adapter.notifyDataSetChanged();
                        tvTotalSensors.setText(String.valueOf(sensorList.size()));
                        tvActiveAlerts.setText(String.valueOf(alertCount));
                        tvMaxRisk.setText(String.valueOf(maxRisk));
                    });
        }
    }
}