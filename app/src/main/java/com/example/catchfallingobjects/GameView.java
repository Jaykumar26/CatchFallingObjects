package com.example.catchfallingobjects;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

/**
 * Core game view: draws the sky, the basket, and falling objects,
 * runs the game loop, handles touch input, scoring and lives.
 */
public class GameView extends View {

    /** Difficulty presets selected on the Home screen before starting a game. */
    public enum Difficulty { EASY, NORMAL, HARD, ZEN }

    private enum ObjectType { NORMAL, BOMB, STAR, SHIELD, SLOWMO }

    // ---- Tunable game constants that do not vary by difficulty ----
    private static final long FRAME_DELAY_MS = 16; // ~60 FPS
    private static final float BASKET_WIDTH_RATIO = 0.22f;   // relative to screen width
    private static final float BASKET_HEIGHT_RATIO = 0.06f;  // relative to screen height
    private static final float OBJECT_RADIUS_RATIO = 0.035f; // relative to screen width
    private static final float SPEED_INCREASE_PER_CATCH = 0.15f;
    private static final long MIN_SPAWN_INTERVAL_MS = 450;
    private static final float TIME_SPEED_INCREASE_PER_SEC = 0.06f; // extra fall speed per second played

    // ---- Power-up tuning ----
    private static final float STAR_SPAWN_CHANCE = 0.08f;
    private static final float SHIELD_SPAWN_CHANCE = 0.05f;
    private static final float SLOWMO_SPAWN_CHANCE = 0.05f;
    private static final long SLOWMO_DURATION_MS = 4000L;
    private static final float SLOWMO_MULTIPLIER = 0.5f;
    private static final int STAR_POINTS = 5;
    private static final int NORMAL_POINTS = 1;

    // ---- Combo tuning ----
    private static final int COMBO_STEP = 5; // catches needed per multiplier level

    // ---- Difficulty-dependent settings (defaults match the original NORMAL values) ----
    private Difficulty difficulty = Difficulty.NORMAL;
    private int initialLives = 3;
    private float baseFallSpeed = 6f;
    private float bombSpawnChance = 0.18f;
    private long baseSpawnIntervalMs = 1200L;
    private boolean livesEnabled = true;

    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private int screenWidth;
    private int screenHeight;

    // Basket
    private float basketX; // left edge of basket
    private float basketWidth;
    private float basketHeight;
    private float basketY; // top edge of basket

    // Falling objects
    private final ArrayList<FallingObject> fallingObjects = new ArrayList<>();
    private float objectRadius;
    private float currentFallSpeed;
    private long spawnIntervalMs;
    private long lastSpawnTime = 0L;
    private long gameStartTime;

    // Score / lives / state
    private int score = 0;
    private int lives = 3;
    private boolean gameOver = false;
    private boolean running = false;

    // Power-ups / combo
    private int comboCount = 0;
    private boolean shieldActive = false;
    private long slowMoEndTimeMs = 0L;

    // Paints
    private Paint basketPaint;
    private Paint objectPaint;
    private Paint textPaint;
    private Paint bigTextPaint;
    private Paint skyPaint;
    private Paint homeButtonPaint;
    private Paint homeButtonTextPaint;
    private Paint pauseIconPaint;
    private Paint pauseButtonBgPaint;
    private Paint fuseSparkPaint;
    private Paint secondaryButtonPaint;
    private Paint iconGlyphPaint;
    private Paint comboPaint;

    // System bar insets (status bar / nav bar), so content avoids drawing under them
    private float topInset = 0f;
    private float bottomInset = 0f;

    // Tap zone for the "Home" button shown on the Game Over screen
    private final RectF homeButtonRect = new RectF();
    // Pause state
    private boolean paused = false;
    private final RectF pauseButtonRect = new RectF();
    private final RectF resumeButtonRect = new RectF();
    private final RectF pauseHomeButtonRect = new RectF();

    private final Runnable gameLoop = new Runnable() {
        @Override
        public void run() {
            if (running) {
                update();
                invalidate();
                handler.postDelayed(this, FRAME_DELAY_MS);
            }
        }
    };

    public GameView(Context context) {
        super(context);
        init();
    }

    private void init() {
        setFocusable(true);

        basketPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        basketPaint.setColor(Color.parseColor("#FFB74D"));
        basketPaint.setStyle(Paint.Style.FILL);

        objectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        objectPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(56f);
        textPaint.setFakeBoldText(true);

        bigTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bigTextPaint.setColor(Color.WHITE);
        bigTextPaint.setTextAlign(Paint.Align.CENTER);
        bigTextPaint.setFakeBoldText(true);

        skyPaint = new Paint();

        homeButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        homeButtonPaint.setColor(Color.parseColor("#FFB74D"));
        homeButtonPaint.setStyle(Paint.Style.FILL);

        homeButtonTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        homeButtonTextPaint.setColor(Color.parseColor("#1A2A6C"));
        homeButtonTextPaint.setTextAlign(Paint.Align.CENTER);
        homeButtonTextPaint.setFakeBoldText(true);

        pauseIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pauseIconPaint.setColor(Color.WHITE);

        pauseButtonBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pauseButtonBgPaint.setColor(Color.parseColor("#55000000"));

        fuseSparkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fuseSparkPaint.setStrokeWidth(4f);
        fuseSparkPaint.setStyle(Paint.Style.STROKE);

        secondaryButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        secondaryButtonPaint.setColor(Color.parseColor("#33FFFFFF"));

        iconGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        iconGlyphPaint.setColor(Color.WHITE);
        iconGlyphPaint.setTextAlign(Paint.Align.CENTER);
        iconGlyphPaint.setFakeBoldText(true);

        comboPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        comboPaint.setColor(Color.parseColor("#FFD54F"));
        comboPaint.setTextSize(48f);
        comboPaint.setFakeBoldText(true);
    }

    /**
     * Selects a difficulty preset. Must be called before the view starts the
     * game (e.g. right after construction) to take effect; calling it later
     * only affects the next {@link #startGame()}.
     */
    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
        switch (difficulty) {
            case EASY:
                initialLives = 4;
                baseFallSpeed = 4.5f;
                bombSpawnChance = 0.10f;
                baseSpawnIntervalMs = 1400L;
                livesEnabled = true;
                break;
            case HARD:
                initialLives = 2;
                baseFallSpeed = 8f;
                bombSpawnChance = 0.26f;
                baseSpawnIntervalMs = 1000L;
                livesEnabled = true;
                break;
            case ZEN:
                initialLives = 0;
                baseFallSpeed = 5f;
                bombSpawnChance = 0f;
                baseSpawnIntervalMs = 1200L;
                livesEnabled = false;
                break;
            case NORMAL:
            default:
                initialLives = 3;
                baseFallSpeed = 6f;
                bombSpawnChance = 0.18f;
                baseSpawnIntervalMs = 1200L;
                livesEnabled = true;
                break;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        screenWidth = w;
        screenHeight = h;

        basketWidth = screenWidth * BASKET_WIDTH_RATIO;
        basketHeight = screenHeight * BASKET_HEIGHT_RATIO;
        layoutBasket();
        basketX = (screenWidth - basketWidth) / 2f;

        objectRadius = screenWidth * OBJECT_RADIUS_RATIO;

        bigTextPaint.setTextSize(screenWidth * 0.09f);
        homeButtonTextPaint.setTextSize(screenWidth * 0.045f);
        iconGlyphPaint.setTextSize(objectRadius * 1.3f);

        skyPaint.setShader(new LinearGradient(
                0, 0, 0, screenHeight,
                Color.parseColor("#1A2A6C"), Color.parseColor("#3B5998"),
                Shader.TileMode.CLAMP));

        startGame();
    }

    /**
     * Called by GameActivity once it knows the system bar insets (status bar
     * height, navigation bar height), so the HUD and basket can be positioned
     * to avoid them instead of drawing underneath.
     */
    public void setSystemInsets(int topPx, int bottomPx) {
        this.topInset = topPx;
        this.bottomInset = bottomPx;
        layoutBasket();
        invalidate();
    }

    private void layoutBasket() {
        if (screenHeight == 0) return;
        basketY = screenHeight - basketHeight - 40f - bottomInset;
    }

    /** Resets all state and starts the loop. */
    public void startGame() {
        fallingObjects.clear();
        score = 0;
        lives = initialLives;
        comboCount = 0;
        shieldActive = false;
        slowMoEndTimeMs = 0L;
        currentFallSpeed = baseFallSpeed;
        spawnIntervalMs = baseSpawnIntervalMs;
        gameOver = false;
        lastSpawnTime = System.currentTimeMillis();
        gameStartTime = System.currentTimeMillis();
        basketX = (screenWidth - basketWidth) / 2f;
        running = true;
        handler.removeCallbacks(gameLoop);
        handler.post(gameLoop);
    }

    public void pauseGame() {
        running = false;
        handler.removeCallbacks(gameLoop);
    }

    public void resumeGame() {
        if (!gameOver && screenWidth > 0) {
            running = true;
            handler.removeCallbacks(gameLoop);
            handler.post(gameLoop);
        }
    }

    // ---- Game loop update ----
    private void update() {
        if (gameOver || paused) return;

        long now = System.currentTimeMillis();

        // Fall speed ramps up continuously with time played, plus a bit more per point scored.
        float elapsedSeconds = (now - gameStartTime) / 1000f;
        currentFallSpeed = baseFallSpeed
                + (elapsedSeconds * TIME_SPEED_INCREASE_PER_SEC)
                + (score * SPEED_INCREASE_PER_CATCH);

        boolean slowMoActive = now < slowMoEndTimeMs;
        float activeFallSpeed = slowMoActive ? currentFallSpeed * SLOWMO_MULTIPLIER : currentFallSpeed;

        // Keep every object currently on screen in sync with the new speed,
        // so the whole screen visibly speeds up (or slows down) together.
        for (FallingObject obj : fallingObjects) {
            obj.speed = activeFallSpeed;
        }

        if (now - lastSpawnTime >= spawnIntervalMs) {
            spawnObject(activeFallSpeed);
            lastSpawnTime = now;
        }

        RectF basketRect = new RectF(basketX, basketY, basketX + basketWidth, basketY + basketHeight);

        for (int i = fallingObjects.size() - 1; i >= 0; i--) {
            FallingObject obj = fallingObjects.get(i);
            obj.y += obj.speed;

            boolean withinVerticalRange = obj.y + objectRadius >= basketY && obj.y - objectRadius <= basketY + basketHeight;
            boolean withinHorizontalRange = obj.x >= basketRect.left - objectRadius && obj.x <= basketRect.right + objectRadius;

            if (withinVerticalRange && withinHorizontalRange) {
                fallingObjects.remove(i);
                handleCatch(obj);
                continue;
            }

            if (obj.y - objectRadius > screenHeight) {
                fallingObjects.remove(i);
                handleMiss(obj);
            }
        }
    }

    /** Applies the effect of catching one falling object in the basket. */
    private void handleCatch(FallingObject obj) {
        if (obj.type == ObjectType.BOMB) {
            if (shieldActive) {
                // Shield absorbs the bomb entirely - no life lost, streak untouched.
                shieldActive = false;
            } else {
                comboCount = 0;
                loseLife();
            }
            return;
        }

        comboCount++;
        spawnIntervalMs = Math.max(MIN_SPAWN_INTERVAL_MS, spawnIntervalMs - 15);

        switch (obj.type) {
            case STAR:
                addScore(STAR_POINTS);
                break;
            case SHIELD:
                shieldActive = true;
                break;
            case SLOWMO:
                slowMoEndTimeMs = System.currentTimeMillis() + SLOWMO_DURATION_MS;
                break;
            case NORMAL:
            default:
                addScore(NORMAL_POINTS);
                break;
        }
    }

    /** Applies the effect of letting one falling object pass the basket. */
    private void handleMiss(FallingObject obj) {
        if (obj.type != ObjectType.BOMB) {
            comboCount = 0;
            loseLife();
        }
        // Missing a bomb is harmless, same as before.
    }

    private void addScore(int basePoints) {
        int multiplier = 1 + comboCount / COMBO_STEP;
        score += basePoints * multiplier;
    }

    private void loseLife() {
        if (!livesEnabled) return;
        lives--;
        if (lives <= 0) {
            gameOver = true;
            running = false;
            saveHighScoreIfNeeded();
        }
    }

    private void saveHighScoreIfNeeded() {
        SharedPreferences prefs = getContext().getSharedPreferences(
                LoginActivity.PREFS_NAME, Context.MODE_PRIVATE);
        int best = prefs.getInt(HomeActivity.KEY_HIGH_SCORE, 0);
        if (score > best) {
            prefs.edit().putInt(HomeActivity.KEY_HIGH_SCORE, score).apply();
        }
    }

    /**
     * Returns to the Home screen. GameActivity was launched from HomeActivity
     * without clearing the back stack, so simply finishing it reveals Home.
     */
    private void goHome() {
        Context context = getContext();
        if (context instanceof android.app.Activity) {
            ((android.app.Activity) context).finish();
        }
    }

    private void spawnObject(float speed) {
        float margin = objectRadius * 1.5f;
        float x = margin + random.nextFloat() * (screenWidth - 2 * margin);
        ObjectType type = rollObjectType();
        int color = colorForType(type);
        FallingObject obj = new FallingObject(x, -objectRadius, speed, color, type);
        fallingObjects.add(obj);
    }

    private ObjectType rollObjectType() {
        float roll = random.nextFloat();
        float cumulative = bombSpawnChance;
        if (roll < cumulative) return ObjectType.BOMB;
        cumulative += STAR_SPAWN_CHANCE;
        if (roll < cumulative) return ObjectType.STAR;
        cumulative += SHIELD_SPAWN_CHANCE;
        if (roll < cumulative) return ObjectType.SHIELD;
        cumulative += SLOWMO_SPAWN_CHANCE;
        if (roll < cumulative) return ObjectType.SLOWMO;
        return ObjectType.NORMAL;
    }

    private int colorForType(ObjectType type) {
        switch (type) {
            case BOMB:
                return Color.parseColor("#212121");
            case STAR:
                return Color.parseColor("#FFD54F");
            case SHIELD:
                return Color.parseColor("#4FC3F7");
            case SLOWMO:
                return Color.parseColor("#BA68C8");
            case NORMAL:
            default:
                return randomColor();
        }
    }

    private int randomColor() {
        int[] palette = {
                Color.parseColor("#EF5350"),
                Color.parseColor("#66BB6A"),
                Color.parseColor("#42A5F5"),
                Color.parseColor("#FFCA28"),
                Color.parseColor("#AB47BC"),
                Color.parseColor("#FF7043")
        };
        return palette[random.nextInt(palette.length)];
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawRect(0, 0, screenWidth, screenHeight, skyPaint);

        for (FallingObject obj : fallingObjects) {
            objectPaint.setColor(obj.color);
            canvas.drawCircle(obj.x, obj.y, objectRadius, objectPaint);

            switch (obj.type) {
                case BOMB:
                    fuseSparkPaint.setColor(Color.parseColor("#FF7043"));
                    canvas.drawLine(obj.x, obj.y - objectRadius,
                            obj.x + objectRadius * 0.5f, obj.y - objectRadius * 1.5f, fuseSparkPaint);
                    fuseSparkPaint.setColor(Color.WHITE);
                    canvas.drawCircle(obj.x - objectRadius * 0.3f, obj.y - objectRadius * 0.3f,
                            objectRadius * 0.18f, fuseSparkPaint);
                    break;
                case STAR:
                    canvas.drawText("★", obj.x, obj.y + objectRadius * 0.35f, iconGlyphPaint);
                    break;
                case SHIELD:
                    fuseSparkPaint.setColor(Color.WHITE);
                    canvas.drawCircle(obj.x, obj.y, objectRadius * 0.6f, fuseSparkPaint);
                    break;
                case SLOWMO:
                    fuseSparkPaint.setColor(Color.WHITE);
                    canvas.drawLine(obj.x, obj.y, obj.x, obj.y - objectRadius * 0.55f, fuseSparkPaint);
                    canvas.drawLine(obj.x, obj.y, obj.x + objectRadius * 0.4f, obj.y, fuseSparkPaint);
                    break;
                case NORMAL:
                default:
                    break;
            }
        }

        RectF basketRect = new RectF(basketX, basketY, basketX + basketWidth, basketY + basketHeight);
        canvas.drawRoundRect(basketRect, 18f, 18f, basketPaint);

        float hudY = topInset + 90f;
        canvas.drawText("Score: " + score, 30, hudY, textPaint);
        if (livesEnabled) {
            canvas.drawText("Lives: " + lives, screenWidth - 260, hudY, textPaint);
        } else {
            canvas.drawText("Zen Mode", screenWidth - 400, hudY, textPaint);
        }

        StringBuilder statusLine = new StringBuilder();
        if (comboCount >= 3) {
            statusLine.append("Combo x").append(1 + comboCount / COMBO_STEP);
        }
        if (shieldActive) {
            if (statusLine.length() > 0) statusLine.append("   ");
            statusLine.append("🛡 Shield");
        }
        if (System.currentTimeMillis() < slowMoEndTimeMs) {
            if (statusLine.length() > 0) statusLine.append("   ");
            statusLine.append("⏱ Slow-mo");
        }
        if (statusLine.length() > 0) {
            canvas.drawText(statusLine.toString(), 30, hudY + 50f, comboPaint);
        }

        // Pause button - only shown while actively playing
        if (!gameOver) {
            float btnSize = 70f;
            pauseButtonRect.set(screenWidth - btnSize - 20f, topInset + 20f,
                    screenWidth - 20f, topInset + 20f + btnSize);
            canvas.drawRoundRect(pauseButtonRect, 14f, 14f, pauseButtonBgPaint);
            float barW = btnSize * 0.14f;
            float barH = btnSize * 0.45f;
            float cy = pauseButtonRect.centerY();
            float cx = pauseButtonRect.centerX();
            canvas.drawRect(cx - barW * 2.2f, cy - barH / 2f, cx - barW * 0.4f, cy + barH / 2f, pauseIconPaint);
            canvas.drawRect(cx + barW * 0.4f, cy - barH / 2f, cx + barW * 2.2f, cy + barH / 2f, pauseIconPaint);
        }

        if (gameOver) {
            canvas.drawARGB(150, 0, 0, 0);
            bigTextPaint.setTextSize(screenWidth * 0.11f);
            canvas.drawText("GAME OVER", screenWidth / 2f, screenHeight / 2f - 40, bigTextPaint);
            bigTextPaint.setTextSize(screenWidth * 0.06f);
            canvas.drawText("Score: " + score, screenWidth / 2f, screenHeight / 2f + 40, bigTextPaint);
            bigTextPaint.setTextSize(screenWidth * 0.05f);
            canvas.drawText("Tap here to play again", screenWidth / 2f, screenHeight / 2f + 110, bigTextPaint);

            float hbW = screenWidth * 0.5f;
            float hbH = 90f;
            homeButtonRect.set(screenWidth / 2f - hbW / 2f, screenHeight / 2f + 150,
                    screenWidth / 2f + hbW / 2f, screenHeight / 2f + 150 + hbH);
            canvas.drawRoundRect(homeButtonRect, 16f, 16f, basketPaint);
            bigTextPaint.setTextSize(screenWidth * 0.045f);
            canvas.drawText("🏠 Home", screenWidth / 2f, homeButtonRect.centerY() + 14f, bigTextPaint);
        }

        if (paused) {
            canvas.drawARGB(170, 0, 0, 0);
            bigTextPaint.setTextSize(screenWidth * 0.1f);
            canvas.drawText("PAUSED", screenWidth / 2f, screenHeight / 2f - 120, bigTextPaint);

            float btnW = screenWidth * 0.55f;
            float btnH = 90f;

            resumeButtonRect.set(screenWidth / 2f - btnW / 2f, screenHeight / 2f - 20,
                    screenWidth / 2f + btnW / 2f, screenHeight / 2f - 20 + btnH);
            canvas.drawRoundRect(resumeButtonRect, 16f, 16f, basketPaint);
            bigTextPaint.setTextSize(screenWidth * 0.045f);
            canvas.drawText("▶ Resume", screenWidth / 2f, resumeButtonRect.centerY() + 14f, bigTextPaint);

            pauseHomeButtonRect.set(screenWidth / 2f - btnW / 2f, screenHeight / 2f - 20 + btnH + 24,
                    screenWidth / 2f + btnW / 2f, screenHeight / 2f - 20 + btnH + 24 + btnH);
            canvas.drawRoundRect(pauseHomeButtonRect, 16f, 16f, secondaryButtonPaint);
            bigTextPaint.setTextSize(screenWidth * 0.045f);
            canvas.drawText("🏠 Home", screenWidth / 2f, pauseHomeButtonRect.centerY() + 14f, bigTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (paused) {
                if (resumeButtonRect.contains(x, y)) {
                    paused = false;
                    lastSpawnTime = System.currentTimeMillis(); // avoid a spawn burst on resume
                    invalidate();
                    return true;
                }
                if (pauseHomeButtonRect.contains(x, y)) {
                    goHome();
                    return true;
                }
                return true; // swallow other taps while paused
            }

            if (gameOver) {
                if (homeButtonRect.contains(x, y)) {
                    goHome();
                } else {
                    startGame();
                }
                return true;
            }

            if (pauseButtonRect.contains(x, y)) {
                paused = true;
                invalidate();
                return true;
            }
        }

        if (!paused && !gameOver &&
                (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE)) {
            basketX = x - basketWidth / 2f;
            if (basketX < 0) basketX = 0;
            if (basketX + basketWidth > screenWidth) basketX = screenWidth - basketWidth;
            invalidate();
            return true;
        }

        return super.onTouchEvent(event);
    }

    /** Simple data holder for a falling object. */
    private static class FallingObject {
        float x, y;
        float speed;
        int color;
        ObjectType type;

        FallingObject(float x, float y, float speed, int color, ObjectType type) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.color = color;
            this.type = type;
        }
    }
}
