package com.adaptivehorror.registry;

import com.adaptivehorror.Constants;
import com.adaptivehorror.platform.Services;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

/**
 * Sound registry.
 *
 * <p>IMPORTANT: Minecraft's sound engine only decodes Ogg Vorbis ({@code .ogg}). The shipped source
 * media is {@code .mp3}; the build converts each to {@code .ogg} (see {@code tools/convert-assets}).
 * The keys here map to entries in {@code assets/adaptivehorror/sounds.json}.
 */
public final class ModSounds {

    public static SoundEvent I_SEE_YOU;
    public static SoundEvent SCARY_AMBIENT;
    public static SoundEvent BACKGROUND;

    public static final SoundEvent[] JUMPSCARE = new SoundEvent[4];
    /** Only the travel sounds that ship with an .ogg are registered. Add more by extending this. */
    public static final SoundEvent[] TRAVEL = new SoundEvent[2];

    private ModSounds() {
    }

    public static void register() {
        I_SEE_YOU = sound("iseeyou");
        SCARY_AMBIENT = sound("scary_ambient");
        BACKGROUND = sound("background");
        for (int i = 0; i < JUMPSCARE.length; i++) {
            JUMPSCARE[i] = sound("jumpscare" + (i + 1));
        }
        for (int i = 0; i < TRAVEL.length; i++) {
            TRAVEL[i] = sound("travel" + (i + 1));
        }
    }

    private static SoundEvent sound(String path) {
        final ResourceLocation id = Constants.id(path);
        // The SoundEvent constructor is private in 1.21.1; use the factory.
        return Services.REGISTRY.registerSound(id, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
