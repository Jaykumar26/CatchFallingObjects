package com.example.catchfallingobjects;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {

    public static final String EXTRA_USERNAME = "extra_username";
    public static final String KEY_HIGH_SCORE = "high_score";
    public static final String KEY_DIFFICULTY = "selected_difficulty";

    private TextView tvHighScore;
    private Button btnDifficultyEasy;
    private Button btnDifficultyNormal;
    private Button btnDifficultyHard;
    private Button btnDifficultyZen;
    private GameView.Difficulty selectedDifficulty = GameView.Difficulty.NORMAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        TextView tvWelcome = findViewById(R.id.tvWelcome);
        tvHighScore = findViewById(R.id.tvHighScore);
        Button btnPlay = findViewById(R.id.btnPlay);
        Button btnLogout = findViewById(R.id.btnLogout);
        btnDifficultyEasy = findViewById(R.id.btnDifficultyEasy);
        btnDifficultyNormal = findViewById(R.id.btnDifficultyNormal);
        btnDifficultyHard = findViewById(R.id.btnDifficultyHard);
        btnDifficultyZen = findViewById(R.id.btnDifficultyZen);

        String username = getIntent().getStringExtra(EXTRA_USERNAME);
        if (username == null || username.isEmpty()) {
            username = "Player";
        }
        tvWelcome.setText("Welcome, " + username + "!");

        SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        String savedDifficulty = prefs.getString(KEY_DIFFICULTY, GameView.Difficulty.NORMAL.name());
        try {
            selectedDifficulty = GameView.Difficulty.valueOf(savedDifficulty);
        } catch (IllegalArgumentException e) {
            selectedDifficulty = GameView.Difficulty.NORMAL;
        }
        updateDifficultyButtons();

        btnDifficultyEasy.setOnClickListener(v -> selectDifficulty(GameView.Difficulty.EASY));
        btnDifficultyNormal.setOnClickListener(v -> selectDifficulty(GameView.Difficulty.NORMAL));
        btnDifficultyHard.setOnClickListener(v -> selectDifficulty(GameView.Difficulty.HARD));
        btnDifficultyZen.setOnClickListener(v -> selectDifficulty(GameView.Difficulty.ZEN));

        btnPlay.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, GameActivity.class);
            intent.putExtra(GameActivity.EXTRA_DIFFICULTY, selectedDifficulty.name());
            startActivity(intent);
        });

        btnLogout.setOnClickListener(v -> logout());
    }

    private void selectDifficulty(GameView.Difficulty difficulty) {
        selectedDifficulty = difficulty;
        getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE)
                .edit().putString(KEY_DIFFICULTY, difficulty.name()).apply();
        updateDifficultyButtons();
    }

    private void updateDifficultyButtons() {
        setDifficultyButtonStyle(btnDifficultyEasy, selectedDifficulty == GameView.Difficulty.EASY);
        setDifficultyButtonStyle(btnDifficultyNormal, selectedDifficulty == GameView.Difficulty.NORMAL);
        setDifficultyButtonStyle(btnDifficultyHard, selectedDifficulty == GameView.Difficulty.HARD);
        setDifficultyButtonStyle(btnDifficultyZen, selectedDifficulty == GameView.Difficulty.ZEN);
    }

    private void setDifficultyButtonStyle(Button button, boolean selected) {
        if (selected) {
            button.setBackgroundResource(R.drawable.bg_button);
            button.setTextColor(Color.parseColor("#1A2A6C"));
        } else {
            button.setBackgroundColor(Color.parseColor("#33FFFFFF"));
            button.setTextColor(Color.WHITE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh in case a new high score was just set during a game.
        SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        int best = prefs.getInt(KEY_HIGH_SCORE, 0);
        tvHighScore.setText("Best score: " + best);
    }

    private void logout() {
        SharedPreferences prefs = getSharedPreferences(LoginActivity.PREFS_NAME, MODE_PRIVATE);
        prefs.edit().remove(LoginActivity.KEY_USERNAME).apply();

        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
