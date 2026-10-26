package com.divyanshi.forestfiredetect;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

public class LoginActivity extends AppCompatActivity {

    FirebaseAuth mAuth;
    TextInputEditText etEmail, etPassword;
    Button btnLogin;
    TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth      = FirebaseAuth.getInstance();
        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin   = findViewById(R.id.btnLogin);
        tvError    = findViewById(R.id.tvError);

        if (mAuth.getCurrentUser() != null) {
            saveFCMTokenAndGo();
            return;
        }

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String pass  = etPassword.getText().toString().trim();

            if (email.isEmpty() || pass.isEmpty()) {
                tvError.setText("Please enter email and password");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            btnLogin.setEnabled(false);
            btnLogin.setText("Logging in...");

            mAuth.signInWithEmailAndPassword(email, pass)
                    .addOnSuccessListener(result -> saveFCMTokenAndGo())
                    .addOnFailureListener(e -> {
                        tvError.setText("Login failed: " + e.getMessage());
                        tvError.setVisibility(View.VISIBLE);
                        btnLogin.setEnabled(true);
                        btnLogin.setText("LOGIN");
                    });
        });
    }

    void saveFCMTokenAndGo() {
        // Go to dashboard immediately
        startActivity(new Intent(this, MainActivity.class));
        finish();

        // Save FCM token in background after navigation
        try {
            FirebaseMessaging.getInstance().getToken()
                    .addOnSuccessListener(token -> {
                        try {
                            String uid = FirebaseAuth.getInstance()
                                    .getCurrentUser().getUid();
                            java.util.Map<String, Object> data = new java.util.HashMap<>();
                            data.put("token", token);
                            data.put("updatedAt",
                                    com.google.firebase.firestore.FieldValue.serverTimestamp());
                            FirebaseFirestore.getInstance()
                                    .collection("fcm_tokens")
                                    .document(uid)
                                    .set(data);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    })
                    .addOnFailureListener(e -> e.printStackTrace());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}