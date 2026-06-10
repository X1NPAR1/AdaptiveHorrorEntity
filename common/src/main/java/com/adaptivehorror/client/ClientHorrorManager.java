package com.adaptivehorror.client;

import com.adaptivehorror.Constants;
import com.adaptivehorror.entity.StalkerEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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

    // Forced-look (#16): scripted view-wrenching toward changing random directions.
    private int forceLookTicks;
    private float forceTargetYaw;
    private float forceTargetPitch;
    private int forceRetargetIn;

    // Aim-lock (#17): the view is dragged to centre on the nearest null for a spell.
    private int aimLockTicks;

    /** Max degrees the locked/forced view may slew per tick - the player can still fight it a little. */
    private static final float FORCE_SLEW = 16.0F;
    private static final float AIM_SLEW = 22.0F;
    /** Only ever lock onto a null within this range; beyond it the lock simply does nothing. */
    private static final double AIM_RANGE = 220.0;

    // Window torment (10% of jumpscares) and the rare crash (1%).
    private boolean pendingCrash;
    private int windowFxTicks;
    private int origWinW, origWinH, origWinX, origWinY;
    private boolean windowSaved;

    // Old-TV crackle loop + occasional on-screen static/snow.
    private CrtAmbienceSound bgLoop;
    private int crtStaticTicks;

    private static final double CRASH_CHANCE = 0.01;
    private static final double WINDOW_FX_CHANCE = 0.10;

    private ClientHorrorManager() {
    }

    // --- packet entry points -------------------------------------------------------------------

    public void triggerJumpscare(int imageIndex, int soundIndex, int durationTicks) {
        this.jumpscareTexture = jumpscareImage(imageIndex);
        this.jumpscareTicks = durationTicks;
        this.jumpscareMaxTicks = Math.max(1, durationTicks);
        playSound2D(jumpscareSoundPath(soundIndex), 1.0F, 1.0F);

        // Rarely, the game "breaks": a 1% hard crash, or a 10% spell where the window itself shakes,
        // shrinks and grows on its own.
        final double roll = random.nextDouble();
        if (roll < CRASH_CHANCE) {
            pendingCrash = true;
        } else if (roll < CRASH_CHANCE + WINDOW_FX_CHANCE) {
            startWindowTorment();
        }
    }

    public void playSound2D(String path, float volume, float pitch) {
        final SoundEvent sound = lookup(path);
        if (sound == null || mc.player == null) {
            return;
        }
        // Play at the listener's own position with the MASTER source. This is the same code path the
        // (working) positional ambient sounds use; SimpleSoundInstance.forUI proved unreliable for the
        // jumpscare stings, which is why they often came through silent.
        final Vec3 p = mc.player.position();
        mc.getSoundManager().play(new SimpleSoundInstance(
                sound, SoundSource.MASTER, volume, pitch, RandomSource.create(), p.x, p.y, p.z));
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

    /** Begin the forced-look beat (#16): wrench the view around, paired with shake, for {@code ticks}. */
    public void startForcedLook(int ticks, float intensity) {
        this.forceLookTicks = Math.max(this.forceLookTicks, ticks);
        this.forceRetargetIn = 0; // pick a direction immediately
        startShake(ticks, Math.max(0.7F, intensity));
    }

    /** Begin the aim-lock beat (#17): drag the view onto the nearest null for {@code ticks}. */
    public void startAimLock(int ticks) {
        this.aimLockTicks = Math.max(this.aimLockTicks, ticks);
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
        stopBackgroundLoop();
        // Occasional bursts of on-screen static/snow.
        if (crtStaticTicks > 0) {
            crtStaticTicks--;
        } else if (random.nextInt(450) == 0) {
            crtStaticTicks = 3 + random.nextInt(9);
        }
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
        // Forced view control. Always decremented so it self-expires even if the player is gone -
        // control can never be left permanently overridden.
        if (forceLookTicks > 0) {
            tickForcedLook();
            forceLookTicks--;
        }
        if (aimLockTicks > 0) {
            tickAimLock();
            aimLockTicks--;
        }
        if (pendingCrash) {
            pendingCrash = false;
            crashGame();
        }
        if (windowFxTicks > 0) {
            tickWindowTorment();
        }
    }

    // --- forced view control (#16 forced look, #17 aim-lock) -----------------------------------

    private void tickForcedLook() {
        if (mc.player == null) {
            return;
        }
        if (forceRetargetIn-- <= 0) {
            // A fresh, disorienting direction: a big yaw swing and a wild up/down pitch.
            forceTargetYaw = mc.player.getYRot() + (random.nextFloat() - 0.5F) * 260.0F;
            forceTargetPitch = (random.nextFloat() - 0.5F) * 120.0F;
            forceRetargetIn = 6 + random.nextInt(10); // hold each target ~0.3-0.8s
        }
        slewView(forceTargetYaw, forceTargetPitch, FORCE_SLEW);
    }

    private void tickAimLock() {
        if (mc.player == null) {
            return;
        }
        final StalkerEntity target = nearestStalker();
        if (target == null) {
            return; // nothing to lock onto - the lock simply does nothing this tick
        }
        final Vec3 eye = mc.player.getEyePosition();
        final Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(eye);
        final double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
        final float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        final float pitch = (float) (-Math.toDegrees(Math.atan2(to.y, horiz)));
        slewView(yaw, pitch, AIM_SLEW);
    }

    /** Rotate the player's view toward a target, capped per tick so it pulls rather than teleports. */
    private void slewView(float targetYaw, float targetPitch, float maxStep) {
        final float curYaw = mc.player.getYRot();
        final float curPitch = mc.player.getXRot();
        final float dYaw = Mth.clamp(Mth.wrapDegrees(targetYaw - curYaw), -maxStep, maxStep);
        final float dPitch = Mth.clamp(Mth.wrapDegrees(targetPitch - curPitch), -maxStep, maxStep);
        final float newYaw = curYaw + dYaw;
        final float newPitch = Mth.clamp(curPitch + dPitch, -90.0F, 90.0F);
        mc.player.setYRot(newYaw);
        mc.player.setXRot(newPitch);
        mc.player.setYHeadRot(newYaw);
    }

    private StalkerEntity nearestStalker() {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        StalkerEntity best = null;
        double bestSq = AIM_RANGE * AIM_RANGE;
        for (var entity : mc.level.entitiesForRendering()) {
            if (entity instanceof StalkerEntity stalker) {
                final double d = mc.player.distanceToSqr(stalker);
                if (d < bestSq) {
                    bestSq = d;
                    best = stalker;
                }
            }
        }
        return best;
    }

    // --- window torment / crash ----------------------------------------------------------------

    private void startWindowTorment() {
        try {
            final long handle = mc.getWindow().getWindow();
            final int[] w = new int[1];
            final int[] h = new int[1];
            final int[] x = new int[1];
            final int[] y = new int[1];
            org.lwjgl.glfw.GLFW.glfwGetWindowSize(handle, w, h);
            org.lwjgl.glfw.GLFW.glfwGetWindowPos(handle, x, y);
            origWinW = w[0];
            origWinH = h[0];
            origWinX = x[0];
            origWinY = y[0];
            windowSaved = true;
            windowFxTicks = 60; // ~3 seconds of torment
        } catch (Throwable ignored) {
            // Window manipulation is best-effort; never let it break rendering.
        }
    }

    private void tickWindowTorment() {
        windowFxTicks--;
        try {
            final long handle = mc.getWindow().getWindow();
            if (windowFxTicks <= 0) {
                if (windowSaved) {
                    org.lwjgl.glfw.GLFW.glfwSetWindowSize(handle, origWinW, origWinH);
                    org.lwjgl.glfw.GLFW.glfwSetWindowPos(handle, origWinX, origWinY);
                }
                return;
            }
            final double scale = 0.7 + random.nextDouble() * 0.6; // shrink and grow
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(handle,
                    Math.max(320, (int) (origWinW * scale)), Math.max(240, (int) (origWinH * scale)));
            org.lwjgl.glfw.GLFW.glfwSetWindowPos(handle,
                    origWinX + random.nextInt(61) - 30, origWinY + random.nextInt(61) - 30); // shake
        } catch (Throwable ignored) {
        }
    }

    private void crashGame() {
        final net.minecraft.CrashReport report =
                net.minecraft.CrashReport.forThrowable(new RuntimeException("null"), "null");
        throw new net.minecraft.ReportedException(report);
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

        // The always-on "old TV" look, drawn last so the whole picture - HUD and jumpscares alike -
        // reads as if seen through a CRT. Not gated by anything; this is part of the world now.
        renderOldTv(graphics, w, h);
    }

    /**
     * Pillarboxes the picture to ~4:3 with solid black bars (scaled to the resolution), darkens it
     * like a dim tube, then lays scanlines, a tube vignette, a flicker, a rolling line and - in
     * bursts - on-screen static/snow and a tearing band.
     */
    private void renderOldTv(GuiGraphics g, int w, int h) {
        final int pictureW = Math.min(w, (int) (h * 4.0 / 3.0));
        final int barW = Math.max(0, (w - pictureW) / 2);
        final int x0 = barW;
        final int x1 = w - barW;

        // Overall dim - a tube TV is never as bright as a monitor.
        g.fill(x0, 0, x1, h, withAlpha(0x000000, 0.18F));

        // Scanlines: a translucent dark line every 3 px.
        for (int y = 0; y < h; y += 3) {
            g.fill(x0, y, x1, y + 1, 0x44000000);
        }

        // Tube vignette - top/bottom and the sides nearest the bars.
        final int band = Math.max(20, h / 6);
        final int side = Math.max(20, pictureW / 8);
        g.fillGradient(x0, 0, x1, band, 0x77000000, 0x00000000);
        g.fillGradient(x0, h - band, x1, h, 0x00000000, 0x77000000);
        g.fill(x0, 0, x0 + side / 3, h, withAlpha(0x000000, 0.12F));
        g.fill(x1 - side / 3, 0, x1, h, withAlpha(0x000000, 0.12F));

        // Brightness flicker.
        final float flick = 0.06F + 0.05F * (float) Math.sin(System.currentTimeMillis() / 80.0);
        g.fill(x0, 0, x1, h, withAlpha(0x000000, flick));

        // A faint horizontal line rolling slowly up the screen.
        final int rollY = (int) ((System.currentTimeMillis() / 12L) % Math.max(1, h));
        g.fill(x0, rollY, x1, rollY + 2, 0x16FFFFFF);

        // Bursts of static/snow and a torn band.
        if (crtStaticTicks > 0) {
            renderStatic(g, x0, x1, h);
        }

        // Solid pillarbox bars on top of everything.
        if (barW > 0) {
            g.fill(0, 0, barW, h, 0xFF000000);
            g.fill(x1, 0, w, h, 0xFF000000);
        }
    }

    private void renderStatic(GuiGraphics g, int x0, int x1, int h) {
        final int width = Math.max(1, x1 - x0);
        // Snow: many small flecks of random grey scattered over the picture.
        for (int i = 0; i < 90; i++) {
            final int sx = x0 + random.nextInt(width);
            final int sy = random.nextInt(h);
            final int len = 1 + random.nextInt(3);
            final int grey = 0x33 + random.nextInt(0xCC);
            final int col = (0xA0 << 24) | (grey << 16) | (grey << 8) | grey;
            g.fill(sx, sy, Math.min(x1, sx + len), sy + 1, col);
        }
        // A torn / displaced band that jitters.
        final int tearY = random.nextInt(h);
        final int tearH = 4 + random.nextInt(10);
        g.fill(x0, tearY, x1, tearY + tearH, withAlpha(0xFFFFFF, 0.10F));
        g.fill(x0, tearY + tearH, x1, tearY + tearH + 1, 0x66000000);
    }

    /** Background ambience loop removed by request: ensure any lingering instance is silenced. */
    private void stopBackgroundLoop() {
        if (bgLoop != null) {
            mc.getSoundManager().stop(bgLoop);
            bgLoop = null;
        }
    }

    /** All jumpscare textures are normalised to this square size by the asset-conversion step. */
    private static final int JUMPSCARE_TEX_SIZE = 1024;

    private void renderJumpscare(GuiGraphics graphics, int w, int h) {
        final float alpha = Math.min(1.0F, jumpscareTicks / (jumpscareMaxTicks * 0.25F + 1.0F));
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        // Stretch the full texture across the whole screen. Region == the texture's real size, so the
        // entire image is sampled (0..1) regardless of screen resolution.
        graphics.blit(jumpscareTexture, 0, 0, w, h, 0.0F, 0.0F,
                JUMPSCARE_TEX_SIZE, JUMPSCARE_TEX_SIZE, JUMPSCARE_TEX_SIZE, JUMPSCARE_TEX_SIZE);
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
