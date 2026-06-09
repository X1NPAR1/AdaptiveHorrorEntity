package com.adaptivehorror.fabric;

import com.adaptivehorror.AdaptiveHorror;
import com.adaptivehorror.command.HorrorCommands;
import com.adaptivehorror.platform.FabricNetworkHelper;
import com.adaptivehorror.scheduler.HorrorScheduler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

/**
 * Fabric entrypoint. Runs common init, registers the typed payloads + C2S receiver, and wires the
 * server-side scheduler hooks. Content registration happens eagerly inside common init via the
 * Fabric registry helper.
 */
public final class AdaptiveHorrorFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        AdaptiveHorror.init();
        FabricNetworkHelper.registerCommon();
        FabricNetworkHelper.registerServerReceiver();

        ServerTickEvents.END_SERVER_TICK.register(server ->
                server.getPlayerList().getPlayers().forEach(HorrorScheduler::tickPlayer));

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                HorrorScheduler.onPlayerJoin(handler.player));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                HorrorScheduler.removePlayer(handler.player));

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                HorrorCommands.register(dispatcher));
    }
}
