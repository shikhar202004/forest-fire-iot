package com.divyanshi.forestfiredetect;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class SensorAdapter extends RecyclerView.Adapter<SensorAdapter.VH> {

    List<SensorModel> list;

    public SensorAdapter(List<SensorModel> list) {
        this.list = list;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sensor, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        SensorModel s = list.get(pos);

        h.tvName.setText(s.name);
        h.tvLocation.setText(s.location);
        h.tvTemp.setText(String.format("%.1f°C", s.temperature));
        h.tvHumidity.setText(String.format("%.1f%%", s.humidity));
        h.tvSmoke.setText(String.format("%.1f", s.smoke));
        h.tvCo2.setText(String.format("%.0f", s.co2));
        h.tvBadge.setText(s.alertLabel);
        h.progressRisk.setProgress(s.riskScore);
        h.tvRiskScore.setText(s.riskScore + "/100");

        int riskColor;
        if (s.riskScore >= 75)      riskColor = Color.parseColor("#FF2244");
        else if (s.riskScore >= 50) riskColor = Color.parseColor("#FF6600");
        else if (s.riskScore >= 25) riskColor = Color.parseColor("#FFCC00");
        else                        riskColor = Color.parseColor("#00CC66");

        h.tvRiskScore.setTextColor(riskColor);

        if (s.alertLevel >= 3)      h.tvBadge.setTextColor(Color.parseColor("#FF2244"));
        else if (s.alertLevel == 2) h.tvBadge.setTextColor(Color.parseColor("#FF6600"));
        else if (s.alertLevel == 1) h.tvBadge.setTextColor(Color.parseColor("#FFCC00"));
        else                        h.tvBadge.setTextColor(Color.parseColor("#00CC66"));

        if (s.alertLevel >= 3) {
            h.card.setCardBackgroundColor(Color.parseColor("#2A0A0A"));
        } else {
            h.card.setCardBackgroundColor(Color.parseColor("#162119"));
        }

        boolean hasGPS = s.lat != 0 && s.lng != 0;
        if (hasGPS) {
            h.tvGps.setText(String.format("📍 %.5f°N, %.5f°E — Open Map", s.lat, s.lng));
            h.tvGps.setTextColor(Color.parseColor("#00CCFF"));
            h.tvGps.setOnClickListener(v -> {
                Uri uri = Uri.parse("geo:" + s.lat + "," + s.lng +
                        "?q=" + s.lat + "," + s.lng + "(" + s.name + ")");
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                v.getContext().startActivity(intent);
            });
        } else {
            h.tvGps.setText("📡 No GPS fix yet");
            h.tvGps.setTextColor(Color.parseColor("#5A8A6A"));
            h.tvGps.setOnClickListener(null);
        }
    }

    @Override
    public int getItemCount() { return list.size(); }

    static class VH extends RecyclerView.ViewHolder {
        CardView card;
        TextView tvName, tvLocation, tvBadge, tvTemp, tvHumidity,
                tvSmoke, tvCo2, tvRiskScore, tvGps;
        ProgressBar progressRisk;

        VH(View v) {
            super(v);
            card         = (CardView) v;
            tvName       = v.findViewById(R.id.tvSensorName);
            tvLocation   = v.findViewById(R.id.tvLocation);
            tvBadge      = v.findViewById(R.id.tvBadge);
            tvTemp       = v.findViewById(R.id.tvTemp);
            tvHumidity   = v.findViewById(R.id.tvHumidity);
            tvSmoke      = v.findViewById(R.id.tvSmoke);
            tvCo2        = v.findViewById(R.id.tvCo2);
            tvRiskScore  = v.findViewById(R.id.tvRiskScore);
            tvGps        = v.findViewById(R.id.tvGps);
            progressRisk = v.findViewById(R.id.progressRisk);
        }
    }
}