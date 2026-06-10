package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.event.TargetingManager;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The rare, memorable "something is deeply wrong" beat - and the one event that is felt by the whole
 * server at once. A single weighted-rotation {@link TargetingManager primary victim} is plunged into a
 * sustained blackout with a whisper from nowhere; everyone else on the server shares a lighter cue (a
 * blood vignette and a distant whisper), so the moment lands collectively rather than in private.
 * Probability is kept below 1% via a tiny weight and the day-10 gate.
 */
public final class GlobalEvent implements HorrorEvent {

    @Override
    public String id() {
        return "global";
    }

    @Override
    public int minDay() {
        return 10;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.globalEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        return 0.1; // extremely rare
    }

    @Override
    public void execute(EventContext ctx) {
        final MinecraftServer server = ctx.player.getServer();
        final int blackout = 30 + ctx.random.nextInt(30); // 1.5-3s of darkness

        // Weighted rotation picks who bears the brunt; fall back to the ticking player if targeting has
        // nobody (single-player, or before anyone accepted).
        ServerPlayer primary = server == null ? null : TargetingManager.pickPrimary(server, ctx.random);
        if (primary == null) {
            primary = ctx.player;
        }

        // The primary victim: full blackout, distorted music, the whisper inside the dark.
        HorrorNet.sendBlackout(primary, blackout);
        HorrorNet.sendMusicDistort(primary, blackout);
        HorrorNet.sendSound2D(primary, "iseeyou", 0.9F, 0.85F);

        // Everyone else shares the moment, lighter: a blood vignette and a far-off whisper.
        if (server != null) {
            HorrorNet.broadcastVignettePulseExcept(server, primary, blackout);
            HorrorNet.broadcastSound2DExcept(server, primary, "iseeyou", 0.5F, 0.8F);
        }
    }
}
