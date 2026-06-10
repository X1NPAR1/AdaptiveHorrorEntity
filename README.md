# Adaptive Horror Entity

**Version 2.7.1** · Minecraft **1.21.1** · **NeoForge + Fabric** (one codebase, Architectury
multi-loader). The design goal is a single, intelligent supernatural presence — *null* — that makes
the player feel *watched*: tension, doubt and paranoia over cheap jumpscares.

> **Status: production.** Disclaimer gate, the `null` presence (joins/leaves like a real player),
> the contextual stalker, a post-night-5 watcher group, day/night aggression, a weighted + time-day
> -gated event framework (sixteen concrete events incl. approaching footsteps, a whispered countdown,
> a fake stranger "joining", your own name in chat), the mob-lock and assault set-pieces, the
> personalised sign, jumpscare window-torment/crash, cross-loader networking and full Turkish
> localisation are all implemented and building. See [What can happen](#what-can-happen).

## Project layout

```
common/    loader-agnostic core — all logic lives here, written against interfaces only
neoforge/  NeoForge entrypoint + platform/registry/network service impls + client renderer binding
fabric/    Fabric entrypoint + platform/registry/network service impls + client renderer binding
tools/     convert-assets.ps1 — turns the raw mp3/jpg/jfif media into engine-compatible ogg/png
```

### How the multi-loader abstraction works
The common module **never imports a Forge or Fabric type**. Loader-specific behaviour is reached
through a small `ServiceLoader`-based abstraction in `com.adaptivehorror.platform`:

- `IPlatformHelper` — environment queries (config dir, dev vs prod, client vs server).
- `IRegistryHelper` — content registration (Forge defers to its registry events; Fabric registers
  eagerly — both hidden behind one call-site).

Each loader ships a `META-INF/services` entry pointing at its implementation. This keeps the core
trivially compilable and portable, independent of any Architectury-API version drift.

## Assets — IMPORTANT

Minecraft's engine **only** loads `.ogg` audio and `.png` textures. The provided media is `.mp3`,
`.jpg`, and `.jfif`, which the engine **cannot** use directly. Convert once with:

```powershell
./tools/convert-assets.ps1   # requires ffmpeg on PATH
```

This writes:
- audio → `common/src/main/resources/assets/adaptivehorror/sounds/*.ogg`
- jumpscare images → `.../textures/gui/jumpscare/*.png`

Still needed (art): the entity textures `textures/entity/stalker_white.png` and
`stalker_black.png`, plus the five `120blocksoundN.mp3` source files (not yet present).

## Building

**Toolchain:** Minecraft 1.21.1 targets **Java 21**, and Architectury Loom 1.7 + Gradle 8.x run on
Java 21 too — so a single JDK 21 is all you need (no toolchain juggling).

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.x-hotspot"
gradle build         # Gradle 8.10.x; outputs the two loader jars (see below)
```

Or use the helper:

```powershell
./build.ps1          # locates JDK 21 + Gradle, runs a clean build
```

### Output jars (drop into your `mods` folder)
- **NeoForge:** `neoforge/build/libs/adaptive-horror-entity-<version>.jar`
- **Fabric:**   `fabric/build/libs/adaptive-horror-entity-<version>.jar`  (also needs Fabric API)

Pick the jar matching your loader. Ignore the `-sources.jar` and `-dev*.jar` files.

## The "null" presence & how a session unfolds

1. Enter a world → accept the fullscreen disclaimer (once per world).
2. The mod arms a timer. **5–10 minutes later, `null` "joins"**: a yellow `null sunucuya katıldı`
   chat line, and a `null` entry appears in the tab list.
3. Only *after* null joins does the haunting begin — the stalking entity (pure white by day, pure
   black by night) and every event are gated behind it.
4. null then comes and goes like a flaky player: it occasionally "leaves the server" (the haunting
   pauses) and rejoins minutes later. After the **5th night** a watcher group appears; from **day 10**
   it turns permanently aggressive.

All in-game text is **Turkish**. The mod ships fully functional and automatic (not a demo); the
operator commands below are for verification/showcasing, not a prerequisite.

> The `null` tab-list head is black by default (a client mixin maps its skin to `stalker_black.png`).

## In-game operator commands

All commands require permission level 2 (single-player: enable cheats). Base command
`/adaptivehorror`, alias `/ahe`:

| Command | What it does |
| --- | --- |
| `/ahe spawn` | Force-spawn the stalker nearby (10-28 blocks) and print its coordinates |
| `/ahe jumpscare [1-8]` | Trigger a full-screen jumpscare (random image if omitted) |
| `/ahe event <id>` | Force-run an event (tab-completes: `footsteps`, `countdown`, `fake_join`, `whisper`, `chat`, `sign`, `world_manipulation`, `shadow_ghost`, `fake_player`, `global`, …) |
| `/ahe sound <name>` | Play a registered sound (`scary_ambient`, `iseeyou`, `travel1`, …) |
| `/ahe nulljoin` | Force `null` to join now (skip the 5–10 min wait), unlocking the haunting |
| `/ahe moblock` | Force the "everything stares" lock event right now |
| `/ahe assault` | Force a mob assault (nearby mobs turn hostile and attack) |
| `/ahe status` | Print your live state: day, intensity, whether null joined, vigilance, AFK, active entity |
| `/ahe day` | Print the in-game day and current intensity |
| `/ahe disclaimer` | Re-show the disclaimer screen |
| `/ahe reload` | Reload `config/adaptivehorror.json` from disk |

Quick smoke test: join a world → accept the disclaimer → `/ahe nulljoin` → `/ahe spawn` (it now
spawns ~10–25 blocks away and prints the coordinates) → `/ahe jumpscare` → `/ahe event sign`.

> **Version note:** `gradle.properties` pins the known-good 1.21.1 toolchain versions. If Gradle
> fails to resolve a dependency, bump to the latest 1.21.1-compatible patch — the layout is stable.

## Configuration
A pretty-printed JSON config is written to `config/adaptivehorror.json` on first run. Every
behavioural value (distances, probabilities, cadences, per-subsystem on/off toggles, day/night
attack chances, the null join/leave timing, global intensity, debug mode) lives there — there are no
hardcoded timers in the logic.

## Design fidelity notes
- **"Frame-perfect" audio/image sync:** Minecraft streams audio asynchronously, so exact frame sync
  isn't guaranteed by the engine. Events are triggered same-tick, which is as tight as it allows.

## What can happen

**The stalker (single, per player).** Spawns *contextually*: by day always **75-175 blocks** off,
watching (vanishes when you come within 25); by night it may loom **directly behind** you (vanishes
when you look at it or back into it), appear just outside your **window** when you're sheltered, or
stand **right in front of you while you sleep** (10%, gone when you wake). On trigger it is **95% just
vanish, 5% strike**. After it vanishes there is a **15-60 s gap** before the next; wander **200+
blocks** away and it relocates to a fresh far spot. **Pure white by day, pure black by night.** Night
nulls are far more aggressive (5% strike by day → **18% at night**).

**A strike** teleports it in, plays a sting, inflicts slowness/blindness/nausea, and fires a
jumpscare — and even then it only **kills 20% of the time** (80% it just scares). So deaths are rare.

**Underground** — in caves the stalker is **always black, regardless of the time of day**, spawns at
**your own level** in a real cave pocket (never on the distant surface, never in water or inside a
block), is **more aggressive** (30% strike), and the hauntings come **twice as often** — phantom
block-breaking, snuffed torches and whispers crowd in around you in the dark.

**The watcher group** — after the **5th night**, 3-8 extra nulls stand **50-200 blocks** off and
stare; 95% peaceful, 5% (18% at night) strike when you approach within 25.

**The event framework** — a weighted, day/night-gated roll once null is present. Sixteen events:
- **footsteps** approaching from behind, closer and closer, then silence;
- a whispered **countdown** `<null> 3 … 2 … 1 …` then a jumpscare;
- a **stranger "joins the game"** then leaves seconds later;
- **chat whispers** as `<null> …` in white — including, rarely, **your own name**;
- the **personal sign**: a lightning strike leaves a sign with your **computer name, city and
  country** and *"yakınındayım"* (or one of 25 ominous one-liners);
- **world tampering**: a door swings on its own, a block crumbles, a **torch snuffs out**, or a
  **skull** appears on the ground;
- random **jumpscares** (10% also shake/resize the OS window, **1% crash the game**);
- **screen effects** (blackout, vignette, glitch, real **camera shake**);
- **music distortion**, **shadow ghost**, **fake player** (wearing *your* skin), **sound illusions**,
  an over-the-shoulder **whisper**, and a rare **global blackout**.

**Set-pieces & escalation**
- **Mob lock** — every 5 min / 25%, every mob within 4 chunks freezes and stares for 30 s while chat
  floods (~3 lines/s, corrupted glyphs + hidden hints) and `scary_ambient` loops.
- **Night assault** — 3%/min at night, nearby mobs turn hostile for ~30-90 s (weak); a mob kill →
  jumpscare. **From day 10** it's permanent, day *and* night, with triple spawns.
- **Inventory drop** — from day 4, every 10 min / 15%, null flings your held stack or empties your
  inventory onto the ground.
- **Ambient dread** — `travel1/2`, `scary_ambient`, `iseeyou` drift in from random directions; a
  travel sound every 120 blocks with a 10% escalation to the travel jumpscare.

## Atmosphere (always on)
The whole game is rendered as if through an **old CRT television**: resolution-aware black
**pillarbox bars** down each side, **scanlines**, a soft tube **vignette**, a faint flicker and a
slow rolling line — even jumpscares play out inside the TV frame. The world also carries a permanent
**light haze** (the distance fog is pulled in ~20%). Both are deliberately **not** disableable.

## Architecture
- **Cross-loader networking** — one S2C effect channel + C2S control as 1.21 typed payloads, behind
  `INetworkHelper` (NeoForge `PacketDistributor` / Fabric `ServerPlayNetworking`).
- **Client presentation** (`ClientHorrorManager`) — fullscreen jumpscares + sound, blackout, vignette,
  glitch, music silencing, GLFW window-torment; per-loader tick + HUD hooks.
- **Camera shake** — NeoForge via `ViewportEvent.ComputeCameraAngles`, Fabric via a `Camera` mixin.
  **Black `null` tab head** — a `PlayerInfo` mixin on both loaders maps the null UUID to
  `stalker_black.png`.
- **Server scheduler** — O(players), no polling. Per-player state; per-server managers for null,
  mob-lock, assault. Deferred multi-step beats run through a per-player `ScheduledAction` queue.
- **Disclaimer** — once-per-world `SavedData` gate; nothing fires until accepted *and* null has joined.
- **Config-driven & localised** — every probability/cadence/toggle in `config/adaptivehorror.json`;
  all in-game text Turkish.

## Notes
- The 75-175 block far spawn occasionally lands outside the loaded area; the spawn simply retries the
  next tick until it finds loaded ground (so the 15-60 s gap never stalls).
- The personal sign's city/country uses a best-effort, time-boxed IP-geolocation lookup on a daemon
  thread; it fails silently to `?` offline and only ever displays to the player themselves.
- The 1% jumpscare crash is intentional and rare; disable it (and any feature) in the config.
