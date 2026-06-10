package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.world.phys.Vec3;

/**
 * Plays a familiar vanilla sound (footsteps, a door, a chest, breaking glass, a creeper fuse, a
 * ghast, a hurt player) from a nearby point with no real source - the bread-and-butter "did I just
 * hear that?" cue. Sent per-player so only the target hears it.
 */
public final class SoundIllusionEvent implements HorrorEvent {

    private static final String[] SOUNDS = {
            "minecraft:block.gravel.step",
            "minecraft:block.wooden_door.close",
            "minecraft:block.chest.open",
            "minecraft:block.glass.break",
            "minecraft:entity.creeper.primed",
            "minecraft:entity.ghast.scream",
            "minecraft:entity.player.hurt"
    };

    @Override
    public String id() {
        return "sound_illusion";
    }

    @Override
    public int minDay() {
        return 1; // ambient sounds begin early (design: day 2 footsteps/ambient)
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.audioEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        return ctx.underground ? 9.0 : 4.0; // far more phantom noise down in the dark
    }

    /** Five phantom sounds in a row, half a second apart - a crowd of noises closing in. */
    private static final int BURST_COUNT = 5;
    private static final long BURST_GAP_TICKS = 10L; // 0.5s

    @Override
    public void execute(EventContext ctx) {
        final long start = ctx.level.getGameTime();
        for (int i = 0; i < BURST_COUNT; i++) {
            if (i == 0) {
                oneIllusion(ctx);
            } else {
                ctx.state.scheduled.add(new ScheduledAction(start + i * BURST_GAP_TICKS, () -> oneIllusion(ctx)));
            }
        }
    }

    private static void oneIllusion(EventContext ctx) {
        final String sound = SOUNDS[ctx.random.nextInt(SOUNDS.length)];
        final double angle = ctx.random.nextDouble() * Math.PI * 2.0;
        final double dist = 3.0 + ctx.random.nextDouble() * 6.0;
        final Vec3 pos = ctx.player.position().add(Math.cos(angle) * dist, 0.0, Math.sin(angle) * dist);
        HorrorNet.sendSoundAt(ctx.player, sound, pos, 0.7F, 0.8F + ctx.random.nextFloat() * 0.4F);
    }
}
