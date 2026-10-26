package com.divyanshi.forestfiredetect;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AlertHistoryActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_history);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView rv = findViewById(R.id.rvAlerts);
        rv.setLayoutManager(new LinearLayoutManager(this));

        List<Map<String, Object>> alerts = new ArrayList<>();

        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter =
                new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    @Override
                    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                        View v = LayoutInflater.from(parent.getContext())
                                .inflate(R.layout.item_alert, parent, false);
                        return new RecyclerView.ViewHolder(v) {};
                    }
                    @Override
                    public void onBindViewHolder(RecyclerView.ViewHolder holder, int pos) {
                        Map<String, Object> a = alerts.get(pos);
                        View v = holder.itemView;
                        ((TextView) v.findViewById(R.id.tvAlertLabel))
                                .setText(String.valueOf(a.getOrDefault("label", "Alert")));
                        ((TextView) v.findViewById(R.id.tvAlertScore))
                                .setText(a.getOrDefault("riskScore", 0) + "/100");
                        ((TextView) v.findViewById(R.id.tvAlertSensor))
                                .setText(a.getOrDefault("sensor", "") + " — " + a.getOrDefault("location", ""));
                        ((TextView) v.findViewById(R.id.tvAlertMeta))
                                .setText("Temp: " + a.getOrDefault("temperature", "--") + "°C  " +
                                        "Smoke: " + a.getOrDefault("smoke", "--") + " ppm");
                        Object ts = a.get("timestamp");
                        ((TextView) v.findViewById(R.id.tvAlertTime))
                                .setText(ts != null ? ts.toString() : "");
                    }
                    @Override
                    public int getItemCount() { return alerts.size(); }
                };

        rv.setAdapter(adapter);

        FirebaseFirestore.getInstance()
                .collection("alerts")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(snapshots -> {
                    for (QueryDocumentSnapshot doc : snapshots) {
                        alerts.add(doc.getData());
                    }
                    adapter.notifyDataSetChanged();
                });
    }
}