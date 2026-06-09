package com.adaptivehorror.client;

import com.adaptivehorror.Constants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Single client-side owner of every transient horror presentation: the active jumpscare, blackout,
 * vignette pulse, glitch bars, music distortion and camera shake. The networking layer decodes
 * packets into calls here; a per-loader client tick advances timers ({@link #tick()}) and a
 * per-loader HUD callback draws the active overlays ({@link #renderOverlay(GuiGraphics)}). The camera
 * hooks read the shake state.
 *
 * <p>State is a handful of countdown timers - cheap, allocation-free per frame. Nothing ticks when
 * idle.
 */
public final class ClientHorrorManager {

    private static final ClientHorrorManager INSTANCE = new ClientHorrorManager();

    public static ClientHorrorManager get() {
        return INSTANCE;
    }

    private final Random random = new Random();
    private final Minecraft mc = Minecraft.getInstance();

    private ResourceLocation jumpscareTexture;
    private int jumpscareTicks;
    private int jumpscareMaxTicks;

    private int blackoutTicks;
    private int vignetteTicks;
    private int glitchTicks;
    private int musicDistortTicks;
    private boolean musicWasStopped;

    private int shakeTicks;
    private int shakeMaxTicks;
    private float shakeIntensity;

    private ClientHorrorManager() {
    }

    // --- packet entry points -------------------------------------------------------------------

    public void triggerJumpscare(int imageIndex, int soundIndex, int durationTicks) {
        this.jumpscareTexture = jumpscareImage(imageIndex);
        this.jumpscareTicks = durationTicks;
        this.jumpscareMaxTicks = Math.max(1, durationTicks);
        playSound2D(jumpscareSoundPath(soundIndex), 1.0F, 1.0F);
    }

    public void playSound2D(String path, float volume, float pitch) {
        final SoundEvent sound = lookup(path);
        if (sound != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
        }
    }

    public void playSoundAt(String path, Vec3 pos, float volume, float pitch) {
        final SoundEvent sound = lookup(path);
        if (sound != null) {
            mc.getSoundManager().play(new SimpleSoundInstance(
                    sound, SoundSource.AMBIENT, volume, pitch, RandomSource.create(), pos.x, pos.y, pos.z));
        }
    }

    public void startBlackout(int ticks) {
        this.blackoutTicks = Math.max(this.blackoutTicks, ticks);
    }

    public void startVignettePulse(int ticks) {
        this.vignetteTicks = Math.max(this.vignetteTicks, ticks);
    }

    public void startGlitch(int ticks) {
        this.glitchTicks = Math.max(this.glitchTicks, ticks);
    }

    public void distortMusic(int ticks) {
        this.musicDistortTicks = Math.max(this.musicDistortTicks, ticks);
        if (!musicWasStopped) {
            mc.getMusicManager().stopPlaying();
            musicWasStopped = true;
        }
    }

    public void startShake(int ticks, float intensity) {
        this.shakeTicks = Math.max(this.shakeTicks, ticks);
        this.shakeMaxTicks = Math.max(1, Math.max(this.shakeMaxTicks, ticks));
        this.shakeIntensity = Math.max(this.shakeIntensity, intensity);
    }

    public boolean isShaking() {
        return shakeTicks > 0;
    }

    private float shakeMagnitude() {
        return shakeIntensity * (shakeTicks / (float) shakeMaxTicks);
    }

    public float nextShakeYaw() {
        return (float) (random.nextGaussian() * shakeMagnitude());
    }

    public float nextShakePitch() {
        return (float) (random.nextGaussian() * shakeMagnitude());
    }

    public float nextShakeRoll() {
        return (float) (random.nextGaussian() * shakeMagnitude() * 1.5F);
    }

    public void spawnShadowGhost() {
        startVignettePulse(2);
        startGlitch(2);
    }

    // --- per-tick advance ----------------------------------------------------------------------

    public void tick() {
        if (jumpscareTicks > 0) {
            jumpscareTicks--;
        }
        if (blackoutTicks > 0) {
            blackoutTicks--;
        }
        if (vignetteTicks > 0) {
            vignetteTicks--;
        }
        if (glitchTicks > 0) {
            glitchTicks--;
        }
        if (musicDistortTicks > 0) {
            musicDistortTicks--;
            if (musicDistortTicks == 0) {
                musicWasStopped = false;
            }
        }
        if (shakeTicks > 0) {
            shakeTicks--;
            if (shakeTicks == 0) {
                shakeIntensity = 0.0F;
            }
        }
    }

    // --- HUD rendering -------------------------------------------------------------------------

    public void renderOverlay(GuiGraphics graphics) {
        final int w = mc.getWindow().getGuiScaledWidth();
        final int h = mc.getWindow().getGuiScaledHeight();

        if (vignetteTicks > 0) {
            renderVignette(graphics, w, h);
        }
        if (glitchTicks > 0) {
            renderGlitch(graphics, w, h);
        }
        if (jumpscareTicks > 0 && jumpscareTexture != null) {
            renderJumpscare(graphics, w, h);
        }
        if (blackoutTicks > 0) {
            final float a = Math.min(1.0F, blackoutTicks / 4.0F);
            graphics.fill(0, 0, w, h, withAlpha(0x000000, a));
        }
    }

    private void renderJumpscare(GuiGraphics graphics, int w, int h) {
        final float alpha = Math.min(1.0F, jumpscareTicks / (jumpscareMaxTicks * 0.25F + 1.0F));
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        // Stretch the full texture across the whole screen (region == texSize -> samples 0..1).
        graphics.blit(jumpscareTexture, 0, 0, w, h, 0.0F, 0.0F, 256, 256, 256, 256);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void renderVignette(GuiGraphics graphics, int w, int h) {
        final float t = (float) Math.sin((vignetteTicks % 20) / 20.0 * Math.PI);
        final float a = 0.15F + 0.35F * t;
        final int band = Math.max(8, w / 8);
        final int col = withAlpha(0x300000, a);
        graphics.fill(0, 0, band, h, col);
        graphics.fill(w - band, 0, w, h, col);
        graphics.fill(0, 0, w, band, col);
        graphics.fill(0, h - band, w, h, col);
    }

    private void renderGlitch(GuiGraphics graphics, int w, int h) {
        for (int i = 0; i < 5; i++) {
            final int y = random.nextInt(h);
            final int barH = 1 + random.nextInt(6);
            final int rgb = random.nextBoolean() ? 0xFFFFFF : 0x550000;
            graphics.fill(0, y, w, y + barH, withAlpha(rgb, 0.25F + random.nextFloat() * 0.3F));
        }
    }

    // --- helpers -------------------------------------------------------------------------------

    private static int withAlpha(int rgb, float alpha) {
        final int a = Math.max(0, Math.min(255, (int) (alpha * 255))) << 24;
        return a | (rgb & 0xFFFFFF);
    }

    private static SoundEvent lookup(String path) {
        final ResourceLocation id = path.indexOf(':') >= 0
                ? ResourceLocation.parse(path) : Constants.id(path);
        return BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
    }

    private static ResourceLocation jumpscareImage(int index) {
        final String name = index == 120 ? "jumpscare120" : "jumpscare" + index;
        return Constants.id("textures/gui/jumpscare/" + name + ".png");
    }

    private static String jumpscareSoundPath(int index) {
        final int clamped = Math.max(1, Math.min(4, index));
        return "jumpscare" + clamped;
    }
}
