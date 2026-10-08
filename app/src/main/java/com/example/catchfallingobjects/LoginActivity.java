package com.example.catchfallingobjects;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Login screen. Credentials are checked against accounts stored in
 * {@link UserDbHelper} (SQLite, passwords salted + hashed via
 * {@link PasswordUtils}). The signed-in username is remembered locally
 * (via SharedPreferences) to greet the player on the Home screen and to
 * support auto-login on the next app launch.
 */
public class LoginActivity extends AppCompatActivity {

    public static final String PREFS_NAME = "CatchFallingObjectsPrefs";
    public static final String KEY_USERNAME = "logged_in_username";

    private EditText etUsername;
    private EditText etPassword;
    private TextView tvError;
    private UserDbHelper userDbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        userDbHelper = new UserDbHelper(this);

        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        tvError = findViewById(R.id.tvError);
        Button btnLogin = findViewById(R.id.btnLogin);
        TextView tvGoToRegister = findViewById(R.id.tvGoToRegister);

        // If already logged in from a previous session, skip straight to Home.
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String savedUsername = prefs.getString(KEY_USERNAME, null);
        if (savedUsername != null) {
            goToHome(savedUsername);
            return;
        }

        btnLogin.setOnClickListener(v -> attemptLogin());
        tvGoToRegister.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            showError("Please enter both username and password.");
            return;
        }

        if (!userDbHelper.checkCredentials(username, password)) {
            showError("Invalid username or password.");
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString(KEY_USERNAME, username).apply();

        goToHome(username);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(TextView.VISIBLE);
    }

    private void goToHome(String username) {
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
        intent.putExtra(HomeActivity.EXTRA_USERNAME, username);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
