package com.adaptivehorror.config;

/**
 * Plain, serialisable configuration model. Every behavioural knob the design calls out lives here as
 * a named, defaulted field - no magic numbers buried in logic, no hardcoded timers. Logic classes
 * read from a {@link HorrorConfig} instance rather than from constants, which is what makes the mod
 * "config-driven" rather than scripted.
 *
 * <p>Grouped into nested static categories so the serialised JSON is human-navigable. All durations
 * are expressed in the unit named by the field (ticks, seconds, or blocks) - never ambiguous.
 */
public final class HorrorConfig {

    public int configVersion = 1;

    /** Master switch. When false the mod is inert (entity never spawns, no events fire). */
    public boolean enabled = true;

    /** Emits verbose scheduling/AI decisions to the log. Off by default. */
    public boolean debugMode = false;

    public final Features features = new Features();
    public final Entity entity = new Entity();
    public final Scheduler scheduler = new Scheduler();
    public final Audio audio = new Audio();
    public final Intensity intensity = new Intensity();
    public final Null nullEntity = new Null();

    /**
     * The "null" presence. After the player accepts the disclaimer, a randomised delay later a fake
     * player named {@code null} silently "joins" (a yellow chat line + a tab-list entry). Only after
     * that does the haunting begin - the stalking entity and all events are gated behind it.
     */
    public static final class Null {
        public boolean enabled = true;
        public String name = "null";
        /** Delay bounds (seconds) after disclaimer acceptance before null joins. Default 5-10 min. */
        public int joinDelayMinSeconds = 300;
        public int joinDelayMaxSeconds = 600;
        /**
         * Optional skin for the tab-list head. {@code textureValue} is the base64 "textures" property
         * value and {@code textureSignature} its signature (may be empty for unsigned). Leave blank to
         * use the default skin. A black-headed skin makes the tab entry read as the entity.
         */
        public String textureValue = "";
        public String textureSignature = "";
    }

    /** Coarse on/off toggles per subsystem, as required by the design's configuration section. */
    public static final class Features {
        public boolean jumpscares = true;
        public boolean audioEvents = true;
        public boolean chatMessages = true;
        public boolean signEvents = true;
        public boolean worldManipulation = true;
        public boolean cameraEffects = true;
        public boolean musicDistortion = true;
        public boolean fakePlayers = true;
        public boolean shadowEntities = true;
        public boolean globalEvents = true;
        public boolean mobLock = true;
    }

    /** Core stalking entity tuning. */
    public static final class Entity {
        /** Minimum / maximum spawn distance from the player, in blocks. */
        public int spawnDistanceMin = 80;
        public int spawnDistanceMax = 100;
        /** Player approaches within this radius -> entity instantly despawns. */
        public int despawnTriggerRadius = 25;
        /** Player travels this far -> current entity despawns and re-spawns elsewhere. */
        public int relocateTravelBlocks = 75;
        /** Chance (0-1) to apply status effects after a proximity despawn. */
        public double proximityEffectChance = 0.15;
        /** Effect duration bounds, in seconds, randomised within the range. Kept short and punchy. */
        public int effectDurationSecondsMin = 2;
        public int effectDurationSecondsMax = 5;
        /** Seconds of being AFK after which the entity may appear directly behind the player. */
        public int afkAppearSeconds = 180;
        /** Chance (0-1) per check to appear beside a sleeping player's bed. */
        public double sleepAppearChance = 0.05;
    }

    /** Horror scheduler cadence. The scheduler decides WHEN events may fire; intensity decides which. */
    public static final class Scheduler {
        /** Base interval bounds, in seconds, between scheduler "ticks" that may roll an event. */
        public int baseIntervalSecondsMin = 45;
        public int baseIntervalSecondsMax = 120;
        /** Distance, in blocks of horizontal travel, between the recurring "120-block" sound event. */
        public int travelSoundBlocks = 120;
        /** Chance (0-1) that a travel-sound event escalates into the travel jumpscare. */
        public double travelJumpscareChance = 0.10;
        /** Minimum seconds between any two full-screen jumpscares (global cooldown). */
        public int jumpscareCooldownSeconds = 180;
    }

    /** Periodic ambient audio cadence, in seconds. */
    public static final class Audio {
        public int scarySoundIntervalMinSeconds = 300;  // 5 min
        public int scarySoundIntervalMaxSeconds = 900;  // 15 min
        public int iSeeYouIntervalMinSeconds = 600;     // 10 min
        public int iSeeYouIntervalMaxSeconds = 1500;    // 25 min
        /** Spatial volume (0-1) for low ambient cues. */
        public float ambientVolume = 0.25f;
    }

    /**
     * Global intensity multiplier and day-progression curve. {@code multiplier} scales every
     * probability; {@code dayProgressionEnabled} ramps intensity with the in-game day count.
     */
    public static final class Intensity {
        public double multiplier = 1.0;
        public boolean dayProgressionEnabled = true;
        /** Day index at which the mod reaches maximum intensity; capped beyond this. */
        public int maxIntensityDay = 10;
    }

    public final MobLock mobLock = new MobLock();

    /**
     * The "everything stares" set-piece. On an interval, with a probability, every mob within a few
     * chunks of any player freezes and turns to stare at the nearest player for a spell, while the
     * chat fills with corrupted glyphs and {@code iseeyou} plays. Then everything snaps back to normal.
     */
    public static final class MobLock {
        public int intervalSeconds = 300;   // check every 5 minutes
        public double chance = 0.25;        // 25% per check
        public int durationSeconds = 30;    // mobs stay locked for 30s
        public int radiusChunks = 4;        // within 4 chunks (64 blocks) of any player
        public int chatMessageCount = 40;   // corrupted messages spread across the lock
    }
}
