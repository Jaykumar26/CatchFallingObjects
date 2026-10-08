# Catch the Falling Objects

A simple Android app with three screens:

1. **Login** (`LoginActivity`) - enter any non-empty username/password to
   continue (there's no backend server, so it just checks the fields aren't
   empty and remembers the username locally). It auto-logs-in on the next
   app launch until you tap Log Out.
2. **Home** (`HomeActivity`) - greets you by name, shows your best score
   (saved locally), and has **Play Game** and **Log Out** buttons.
3. **Game** (`GameActivity` + `GameView`) - the actual game: colored balls
   fall from the top of the screen and you drag a basket left/right to catch
   them. Miss 3 and it's game over; your best score is saved automatically
   and shown back on the Home screen.

## Toolchain requirements (Android API 37 / Android 17)

This project targets `compileSdk`/`targetSdk` **37** (Android 17), which needs
a newer build toolchain than older projects:

- **Android Gradle Plugin 9.1.1** or newer (AGP 9.0 tops out at API 36; API 37
  needs 9.1+) — already set in the top-level `build.gradle`.
- **Gradle 9.3.0** or newer — already set in
  `gradle/wrapper/gradle-wrapper.properties`.
- **JDK 17** or newer to run Gradle itself (Android Studio's bundled JDK
  already satisfies this on recent versions).
- **Android SDK Platform 37** installed via **Tools > SDK Manager > SDK
  Platforms** (check "Show Package Details" if it's not visible under the
  stable tab yet — API 37 is very new).
- A recent Android Studio release (2025.2+ / "Otter" or newer) that knows
  about AGP 9 and API 37; older Android Studio versions may not offer the
  SDK 37 platform in the SDK Manager at all.

If Gradle sync fails with something like "Failed to find Platform SDK
android-37," open the SDK Manager and install API 37 first.

## How to open in Android Studio

1. Unzip this project.
2. Open Android Studio -> **File > Open** -> select the unzipped
   `CatchFallingObjects` folder (the one containing `settings.gradle`).
3. Let Gradle sync (Android Studio will download the Android Gradle Plugin
   and dependencies automatically the first time — needs internet access).
4. Click the green **Run** button (or Shift+F10) with an emulator or
   physical device connected.

## How it works / where to look

- `LoginActivity.java` — the login form. Accepts any non-empty
  username/password, saves the username with `SharedPreferences`, and
  auto-skips to Home next time the app opens (until Log Out is tapped).
  Swap in real authentication in `attemptLogin()` if you add a backend later.
- `HomeActivity.java` — shows a welcome message and the saved best score,
  and has the Play/Log Out buttons.
- `GameActivity.java` — just creates and shows the `GameView`, and
  pauses/resumes the game loop with the activity lifecycle.
- `GameView.java` — everything else:
  - `onSizeChanged` sizes the basket and objects relative to screen size.
  - `gameLoop` (a `Handler.postDelayed` loop, ~60 FPS) calls `update()`
    then `invalidate()` to redraw.
  - `update()` moves each falling object down, checks collision against the
    basket rectangle, increments score on a catch, and decrements lives on
    a miss. Falling speed and spawn rate increase gradually as you score.
  - `onTouchEvent` drags the basket to follow your finger (clamped to the
    screen edges). Tapping after Game Over restarts.
  - `onDraw` draws the sky gradient, the falling circles, the basket, the
    score/lives HUD, and the Game Over overlay.

## Easy things to customize

- `INITIAL_LIVES`, `BASE_FALL_SPEED`, `SPEED_INCREASE_PER_CATCH`,
  `BASE_SPAWN_INTERVAL_MS` at the top of `GameView.java` control difficulty.
- Swap the plain circles for `Bitmap`s (e.g. fruit/star images) in
  `onDraw`'s object-drawing loop and `spawnObject()` if you want real
  artwork instead of colored dots.
- Add a sound effect on catch/miss using `SoundPool` in `update()`.
- On the Game Over screen, tapping anywhere else restarts the game, and
  tapping the drawn **🏠 Home** button (see `homeButtonRect` in
  `GameView.java`) finishes `GameActivity`, revealing `HomeActivity`
  underneath on the back stack. The system Back button does the same thing
  at any point during a game.
  (`saveHighScoreIfNeeded()` in `GameView.java`) and read back in
  `HomeActivity.onResume()` — a good reference if you want to save more
  stats (games played, total catches, etc.).
- Replace the hardcoded "any username/password works" check in
  `LoginActivity.attemptLogin()` with a real backend call (e.g. Firebase
  Auth or your own API) when you're ready to add real accounts.
