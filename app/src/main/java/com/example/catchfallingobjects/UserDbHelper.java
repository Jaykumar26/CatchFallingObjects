package com.example.catchfallingobjects;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * SQLite-backed store for user accounts. Passwords are never stored in plain
 * text - see {@link PasswordUtils} for the salted PBKDF2 hashing.
 */
public class UserDbHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "catchfallingobjects.db";
    private static final int DATABASE_VERSION = 1;

    private static final String TABLE_USERS = "users";
    private static final String COL_ID = "_id";
    private static final String COL_USERNAME = "username";
    private static final String COL_SALT = "salt";
    private static final String COL_PASSWORD_HASH = "password_hash";

    public UserDbHelper(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_USERS + " (" +
                COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COL_USERNAME + " TEXT UNIQUE NOT NULL, " +
                COL_SALT + " TEXT NOT NULL, " +
                COL_PASSWORD_HASH + " TEXT NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        onCreate(db);
    }

    /**
     * Creates a new account with a salted, hashed password.
     *
     * @return true if the account was created, false if the username is already taken.
     */
    public boolean registerUser(String username, String password) {
        String normalizedUsername = normalize(username);
        if (usernameExists(normalizedUsername)) {
            return false;
        }

        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hash(password, salt);

        ContentValues values = new ContentValues();
        values.put(COL_USERNAME, normalizedUsername);
        values.put(COL_SALT, salt);
        values.put(COL_PASSWORD_HASH, hash);

        SQLiteDatabase db = getWritableDatabase();
        long result = db.insertWithOnConflict(TABLE_USERS, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        return result != -1;
    }

    /**
     * Verifies a username/password pair against the stored, hashed credentials.
     */
    public boolean checkCredentials(String username, String password) {
        String normalizedUsername = normalize(username);
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS,
                new String[]{COL_SALT, COL_PASSWORD_HASH},
                COL_USERNAME + " = ?",
                new String[]{normalizedUsername},
                null, null, null);

        try {
            if (!cursor.moveToFirst()) {
                return false;
            }
            String salt = cursor.getString(cursor.getColumnIndexOrThrow(COL_SALT));
            String expectedHash = cursor.getString(cursor.getColumnIndexOrThrow(COL_PASSWORD_HASH));
            return PasswordUtils.matches(password, salt, expectedHash);
        } finally {
            cursor.close();
        }
    }

    public boolean usernameExists(String username) {
        String normalizedUsername = normalize(username);
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE_USERS,
                new String[]{COL_ID},
                COL_USERNAME + " = ?",
                new String[]{normalizedUsername},
                null, null, null);
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private static String normalize(String username) {
        return username.trim().toLowerCase();
    }
}
