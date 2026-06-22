# Hero Quest

A real-time action platformer for Android. Move with a virtual joystick, jump
across platforms, and fight enemies with a melee attack — built entirely with
procedurally-animated humanoid figures (no sprite images needed).

## What this is

Unlike the turn-based Realm of Heroes RPG, this is a true action game: you
directly control a character that runs, jumps, and swings an attack in real
time, rendered at ~60fps on a `SurfaceView` game loop (same engine pattern as
Dodge Drop).

## Controls

- **Virtual joystick** (bottom-left): drag to move left/right
- **JUMP button** (bottom-right, blue): jump — only works while grounded
- **ATK button** (bottom-right, red): swing a melee attack — commits you to
  the swing animation, you can't move or jump again until it finishes
- **Tap anywhere** on the start/game-over/victory screen to begin or restart

## How the character animation works

There are no image files or sprite sheets. Every character — player and
enemies — is drawn each frame as a set of connected line segments (head,
torso, 2 arms, 2 legs) by `HumanoidRig.kt`. Each limb's angle is computed
procedurally based on:

- The character's current `AnimState` (IDLE, RUN, JUMP, FALL, ATTACK, HIT, DEFEATED)
- A running animation-time counter, fed into sine waves for natural-looking
  cyclical motion (running legs swing back and forth, idle sways gently)
- The character's facing direction (flips the whole pose horizontally)

This means animation works immediately with zero art assets, and is easy to
tune — open `HumanoidRig.kt` and adjust the angles/timing in `poseFor()` to
change how any pose looks or feels.

## Architecture

```
entity/   Player, Enemy, Platform, HumanoidRig, AnimState — pure game logic + rendering, no Android Activity dependencies
input/    VirtualJoystick, TouchButton — touch input widgets drawn directly to the game canvas
game/     GameView (orchestrates everything), GameThread (the ~60fps loop)
```

`GameView` owns the level layout (platforms, enemy placement), routes raw
touch events to the joystick/buttons, runs physics + AI updates each frame,
resolves attack-hitbox collisions, and renders everything with a horizontally
scrolling camera that follows the player.

## Current level (Phase 1)

One level, roughly 4 screens wide: a long ground platform with two raised
platforms partway through, and 4 enemies spaced along the path. Defeat all
enemies and reach the end of the level to win.

## How to build & run

Same process as the previous two projects:

1. Open this folder in **Android Studio**, let Gradle sync, hit Run — or
2. Push to GitHub and use the included **GitHub Actions** workflow
   (`.github/workflows/build.yml`) to build the APK in the cloud. Check the
   **Actions** tab after pushing for a downloadable `hero-quest-debug-apk`
   artifact.

This app is set to **landscape orientation** (`AndroidManifest.xml`), since a
side-scrolling platformer needs horizontal screen space — rotate your phone
sideways when testing.

## Easy tweaks

- **Jump height / gravity feel** → `Player.kt`, `gravity` and `jumpVelocity`
- **Move speed** → `Player.kt` (`moveSpeed`) and `Enemy.kt` (`moveSpeed`)
- **Attack damage / reach** → `GameView.resolveCombat()` for damage numbers,
  `attackHitbox()` in `Player.kt`/`Enemy.kt` for reach/timing
- **Enemy aggression** → `Enemy.kt`, `attackRange`, `chaseRange`, `attackCooldownMax`
- **Character proportions/look** → `HumanoidRig.kt`, the proportion constants
  near the top (`headRadius`, `torsoLength`, etc.) and colors passed into each
  `HumanoidRig(...)` constructor call in `Player.kt`/`Enemy.kt`
- **Level layout** → `GameView.setupLevel()`: platform positions, enemy count
  and placement, level length (`levelEndX`)

## What's NOT in Phase 1 (known limitations)

- Only one level — `setupLevel()` is hardcoded, not data-driven yet
- Enemies don't jump or use platforms — they're ground-bound chasers
- No sound
- No ranged/projectile attacks, just melee
- No checkpoints — dying restarts the whole level from the beginning
- Minor known issue: touch input (UI thread) and the game loop (GameThread)
  aren't synchronized on the same lock, so there's a theoretical but very
  unlikely race condition on button-press flags. Hasn't caused any visible
  issue in this architecture style (same as Dodge Drop), but noting it for
  transparency.
