package com.adaptivehorror.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * The constant low CRT crackle ({@code background.ogg}) - a quiet, non-positional loop that plays the
 * whole time you're in a world, reinforcing the "watching an old tube TV" feel. It stops itself the
 * moment the world unloads; {@link ClientHorrorManager} restarts it on the next world.
 */
public final class CrtAmbienceSound extends AbstractTickableSoundInstance {

    public CrtAmbienceSound(SoundEvent sound, float volume) {
        super(sound, SoundSource.MASTER, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = volume;
        this.relative = true;                         // 2D, follows the listener
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public void tick() {
        if (Minecraft.getInstance().level == null) {
            stop();
        }
    }
}
