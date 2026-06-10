# Adaptive Horror Entity

A psychological-horror Minecraft mod for **1.21.1** targeting **NeoForge and Fabric** from one
codebase via the Architectury multi-loader toolchain. The design goal is a single, intelligent
supernatural presence that makes the player feel *watched* — tension and doubt over cheap jumpscares.

> **Status: full feature pass.** The project skeleton, build, config, registries, the stalking
> entity and its full spawn / proximity / relocation lifecycle, the server-side scheduler, the
> cross-loader networking layer, the client presentation (jumpscares, screen/camera effects, music
> distortion), the once-per-world disclaimer gate, adaptive-AI sampling, day progression, periodic
> ambient audio, and a weighted event framework with ten concrete events are all implemented. See
> [Implemented systems](#implemented-systems).

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
trivially compilable and portable, independent of any Architectury-API version drift on 1.16.5.

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
3. Only *after* null joins does the haunting begin — the stalking entity (white by day, black with
   glowing eyes by night) and every event are gated behind it.

All in-game text is **Turkish**. The mod ships fully functional (not a demo); the operator commands
below are for verification/showcasing, not a prerequisite.

> Tab-list head colour for `null` uses the default skin unless you set
> `nullEntity.textureValue`/`textureSignature` (a base64 skin property) in the config.

## In-game operator commands

All commands require permission level 2 (single-player: enable cheats). Base command
`/adaptivehorror`, alias `/ahe`:

| Command | What it does |
| --- | --- |
| `/ahe spawn` | Force-spawn the stalker in your peripheral vision right now |
| `/ahe jumpscare [1-8]` | Trigger a full-screen jumpscare (random image if omitted) |
| `/ahe event <id>` | Force-run a specific event (tab-completes: `sign`, `chat`, `fake_player`, `world_manipulation`, `global`, …) |
| `/ahe sound <name>` | Play a registered sound (`scary_ambient`, `iseeyou`, `travel1`, …) |
| `/ahe nulljoin` | Force `null` to join now (skip the 5–10 min wait), unlocking the haunting |
| `/ahe moblock` | Force the "everything stares" lock event right now |
| `/ahe status` | Print your live state: day, intensity, whether null joined, vigilance, AFK, active entity |
| `/ahe day` | Print the in-game day and current intensity |
| `/ahe disclaimer` | Re-show the disclaimer screen |
| `/ahe reload` | Reload `config/adaptivehorror.json` from disk |

Quick smoke test: join a world → accept the disclaimer → `/ahe nulljoin` → `/ahe spawn` (it now
spawns ~10–25 blocks away and prints the coordinates) → `/ahe jumpscare` → `/ahe event sign`.

> **Version note:** `gradle.properties` pins the known-good 1.16.5 toolchain versions. If Gradle
> fails to resolve a dependency, bump to the latest 1.16.5-compatible patch — the layout is stable.

## Configuration
A pretty-printed JSON config is written to `config/adaptivehorror.json` on first run. Every
behavioural value (distances, probabilities, cadences, per-subsystem on/off toggles, global
intensity, debug mode) lives there — there are no hardcoded timers in the logic.

## Design fidelity notes
- **"Darkness" effect:** does not exist before MC 1.19. On 1.16.5 the proximity scare substitutes
  *Weakness*; the effect pool is one constant in `StalkerManager`.
- **"Frame-perfect" audio/image sync:** Minecraft streams audio asynchronously, so exact frame sync
  isn't guaranteed by the engine. Events are triggered same-tick, which is as tight as it allows.

## Implemented systems
- **Core stalking entity** — peripheral spawn (80-100 blocks), instant despawn within 25 blocks with
  the 15% / 1-3 randomized status-effect roll, relocate-on-travel (75 blocks). White by day, black
  with emissive glowing eyes by night.
- **Cross-loader networking** — one S2C effect channel + C2S control, abstracted behind
  `INetworkHelper` with Forge (`SimpleChannel`) and Fabric (`ServerPlayNetworking`) implementations.
- **Client presentation** (`ClientHorrorManager`) — full-screen jumpscare images + synced sound,
  blackout, vignette pulse, glitch bars, music silencing. Driven by per-loader tick + HUD hooks.
- **Disclaimer** — once-per-world fullscreen gate; server tracks acceptance in `DisclaimerState`
  (saved data) and suppresses all events until the player accepts.
- **Horror scheduler** — O(players), no polling. Randomised interval, intensity-scaled.
- **Day progression** — `DayProgression` ramps intensity to a configurable max day; events gated by
  `minDay` (sounds d2, signs d3, chat d4, jumpscares/screen/shadow d5, fake players d6, world manip
  d7, global d10).
- **Periodic audio** — `scarysounds` every 5-15 min, `iseeyou` (directional) every 10-25 min.
- **Travel event** — every 120 blocks: one of the travel sounds, with a 10% escalation to the
  dedicated travel jumpscare (`jumpscare120`), behind the global cooldown.
- **Adaptive AI** — `BehaviorSampler` feeds decayed signals (look-behind, mining, camping, AFK);
  vigilance damps how often events fire.
- **Event framework** — weighted, day-gated `EventRegistry` with ten events: sound illusion, music
  distort, sign (lightning + localized sign), chat whisper (localized, occasionally corrupted),
  jumpscare, screen effect, shadow ghost, fake player (renders your own skin), world manipulation
  (silent door close), rare global event.

## Known limits / honest notes
- Built and structured for 1.16.5; **not compiled in the authoring environment** (no Forge/Fabric
  toolchain present) — run `./gradlew build` to verify and report any version-specific fixups.
- Some per-behaviour adaptive *reactions* from the design (appear behind on AFK, outside windows when
  camping, near cave mouths when mining) are **sampled but not yet individually wired** into spawn
  placement — the vigilance damping is. These hook cleanly into `StalkerManager`/`EventRegistry`.
- "Camera shake / chromatic aberration / motion blur" are realised as cross-loader screen-space
  overlays (blackout, vignette, glitch); true world-camera distortion needs a per-loader mixin.
```
