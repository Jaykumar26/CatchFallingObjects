package com.example.catchfallingobjects;

import android.os.Bundle;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class GameActivity extends AppCompatActivity {

    public static final String EXTRA_DIFFICULTY = "extra_difficulty";

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on while playing, hide the action bar for full game view
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        gameView = new GameView(this);

        String difficultyName = getIntent().getStringExtra(EXTRA_DIFFICULTY);
        if (difficultyName != null) {
            try {
                gameView.setDifficulty(GameView.Difficulty.valueOf(difficultyName));
            } catch (IllegalArgumentException ignored) {
                // Unknown value - GameView already defaults to NORMAL.
            }
        }

        setContentView(gameView);

        // On Android 15+ (targetSdk 35+) content draws edge-to-edge by
        // default, meaning the status bar can overlap the top of the screen.
        // Read the real system bar sizes so GameView can keep its HUD and
        // basket clear of them, on any device/notch.
        ViewCompat.setOnApplyWindowInsetsListener(gameView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            gameView.setSystemInsets(systemBars.top, systemBars.bottom);
            return insets;
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameView.pauseGame();
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.resumeGame();
    }
}

