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
        /** Delay bounds (seconds) after disclaimer acceptance before null joins (1-3 min). */
        public int joinDelayMinSeconds = 60;
        public int joinDelayMaxSeconds = 180;
        /** How long null stays before it considers leaving (seconds). It only ever leaves in daylight. */
        public int presentMinSeconds = 300;
        public int presentMaxSeconds = 600;
        /** How long it stays away before rejoining (seconds, 4-5 min - it leaves at dawn, returns later). */
        public int awayMinSeconds = 240;
        public int awayMaxSeconds = 300;
        /** From this in-game day on, null never leaves again - the presence becomes permanent. */
        public int stayForeverFromDay = 5;
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
        public boolean mobDeathHorror = true;
        /** When one player is jumpscared by the stalker, every player sees the jumpscare. Off: per-player. */
        public boolean sharedJumpscare = false;
        /** When the stalker kills one player, every player dies with them. Off: per-player. */
        public boolean sharedDeath = false;
    }

    /** Chance (0-1) that killing a mob births a black null from the corpse. */
    public double mobDeathChance = 0.05;

    /** Core stalking entity tuning. */
    public static final class Entity {
        /** Far spawn distance bounds from the player, in blocks (the "far" placement). */
        public int spawnDistanceMin = 75;
        public int spawnDistanceMax = 175;
        /** Base chance a jumpscare "attack" actually kills (else just scares). Scales up with the day. */
        public double jumpscareKillChance = 0.20;
        /** Chance the {@code iseeyou} whisper plays when a stalker vanishes on approach. */
        public double vanishWhisperChance = 0.10;
        /** Chance a passive white (day) null fires a jumpscare as it vanishes (else silent). */
        public double whiteVanishJumpscareChance = 0.10;
        /** Daytime black-null chance grows by this per day past day 2, up to the cap (day 3+ only). */
        public double daytimeBlackChancePerDay = 0.12;
        public double daytimeBlackChanceCap = 0.60;
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
        /** Chance (0-1) that lying down summons the harmless 2s apparition at the foot of the bed. */
        public double sleepAppearChance = 0.20;
    }

    /** Horror scheduler cadence. The scheduler decides WHEN events may fire; intensity decides which. */
    public static final class Scheduler {
        /**
         * The main event roll runs on a fixed clock: every {@code eventIntervalSeconds} there is an
         * {@code eventChance} roll to fire one weighted event (halved interval underground - twice as
         * often in caves). Replaces the old intensity-scaled interval so cadence is predictable.
         */
        public int eventIntervalSeconds = 60;   // try once a minute
        public double eventChance = 0.25;        // 25% each try
        /** Legacy interval bounds (kept for config compatibility; no longer drives the main roll). */
        public int baseIntervalSecondsMin = 45;
        public int baseIntervalSecondsMax = 120;
        /** Distance, in blocks of horizontal travel, between the recurring "120-block" sound event. */
        public int travelSoundBlocks = 120;
        /** Chance (0-1) that a travel-sound event escalates into the travel jumpscare. */
        public double travelJumpscareChance = 0.10;
        /** Minimum seconds between any two full-screen jumpscares (global cooldown). */
        public int jumpscareCooldownSeconds = 180;

        /**
         * Guaranteed environmental-tampering cadence (#13), independent of the weighted roll. Every
         * {@code envEventIntervalSeconds} (halved underground) there is a {@code envEventChance} roll for
         * a block-break / torch-snuff / door beat - so these physical scares happen on a reliable clock.
         */
        public int envEventIntervalSeconds = 120; // surface; underground uses half
        public double envEventChance = 0.50;
    }

    /** Periodic ambient audio cadence, in seconds. */
    public static final class Audio {
        public int scarySoundIntervalMinSeconds = 300;  // 5 min
        public int scarySoundIntervalMaxSeconds = 900;  // 15 min
        public int iSeeYouIntervalMinSeconds = 600;     // 10 min
        public int iSeeYouIntervalMaxSeconds = 1500;    // 25 min
        /** Spatial volume (0-1) for low ambient cues. */
        public float ambientVolume = 0.25f;
        /**
         * Constant background ambience loop (your {@code background.ogg}). Off by default. Set true to
         * have it play continuously in every world (survives death/dimension/reconnect).
         */
        public boolean backgroundLoop = false;
        public float backgroundVolume = 0.30f;
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

    public final Assault assault = new Assault();

    /**
     * Mob hostility. At night there is a small chance per minute that nearby mobs turn on the player
     * for a minute (weakly). From {@code aggressionDay} onward, null is permanently aggressive: mobs
     * hound the player day and night and spawn far more thickly. A mob landing the kill triggers a
     * jumpscare.
     */
    public static final class Assault {
        public boolean enabled = true;
        public double nightChancePerMinute = 0.03;
        /** Each assault lasts a randomised time in this range (default 30-90s). */
        public int durationMinSeconds = 30;
        public int durationMaxSeconds = 90;
        public int radiusChunks = 4;
        public float contactDamage = 1.0f;
        public int dayAssaultFromDay = 4;    // from this day, assaults can also strike in daylight
        public int aggressionDay = 10;       // permanent day+night aggression and 3x spawns after this
        public int extraSpawnPerCycle = 3;   // mobs added near each player per spawn cycle (post-day-10)
    }

    public final Watchers watchers = new Watchers();

    /**
     * The "many eyes". After the fifth night, a group of extra nulls stands far off and simply
     * watches. They are almost always peaceful - vanishing if the player gets close - but a few will
     * instead lunge with a jumpscare.
     */
    public static final class Watchers {
        public boolean enabled = true;
        public int startDay = 5;            // appear after the fifth night
        public int minCount = 3;
        public int maxCount = 8;
        public int distanceMin = 50;
        public int distanceMax = 200;
        public int vanishRadius = 25;       // vanish (or strike) once the player is this close
        // (Strike chance follows the shared day-scaled AdaptiveAI curve, like the main stalker.)

        /**
         * Night surge: after dark (or underground) from {@code startDay} on, the watchers swell into an
         * overwhelming ring. The headcount floors at {@code nightMinCount}, they gather closer
         * ({@code nightDistanceMin}-{@code nightDistanceMax}) and form faster - this is the day-5+
         * "5-6 nulls at once" pressure.
         */
        public int nightMinCount = 5;
        public int nightDistanceMin = 26;   // just outside the 25-block vanish radius
        public int nightDistanceMax = 70;
        /** Daytime hard cap on simultaneous white watchers (#11). */
        public int daytimeMaxCount = 4;
    }

    public final InventoryDrop inventoryDrop = new InventoryDrop();

    /**
     * From {@code minDay} on, null periodically tries to make the player drop their items. The chance
     * is context-sensitive: low normally, higher the deeper and more dangerous the player's position -
     * the entity strikes when a dropped inventory hurts most (near lava, deep underground).
     */
    public static final class InventoryDrop {
        public boolean enabled = true;
        public int minDay = 3;
        public int intervalSeconds = 180;        // try every 3 minutes
        public double chance = 0.03;             // base chance on the surface
        public double chanceBelowZero = 0.10;    // 10% when below Y=0
        public double chanceNearLava = 0.05;     // 5% when within a few blocks of lava (overrides if higher)
        public int lavaSearchRadius = 4;
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
