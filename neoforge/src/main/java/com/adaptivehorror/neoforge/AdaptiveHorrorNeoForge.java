package com.adaptivehorror.neoforge;

import com.adaptivehorror.AdaptiveHorror;
import com.adaptivehorror.Constants;
import com.adaptivehorror.command.HorrorCommands;
import com.adaptivehorror.event.AssaultManager;
import com.adaptivehorror.event.MobDeathHorror;
import com.adaptivehorror.event.MobLockManager;
import com.adaptivehorror.npc.NullManager;
import com.adaptivehorror.platform.NeoForgeNetworkHelper;
import com.adaptivehorror.platform.NeoForgeRegistryHelper;
import com.adaptivehorror.scheduler.HorrorScheduler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge entrypoint. Wires the mod-bus registration events (content, attributes, payloads) to the
 * platform helpers, runs common init, and subscribes the game-bus scheduler hooks.
 */
@Mod(Constants.MOD_ID)
public final class AdaptiveHorrorNeoForge {

    public AdaptiveHorrorNeoForge(IEventBus modBus) {
        // Mod-bus: registration must happen inside these events.
        modBus.addListener(NeoForgeRegistryHelper::onRegister);
        modBus.addListener(NeoForgeRegistryHelper::onAttributes);
        modBus.addListener(NeoForgeNetworkHelper::register);

        AdaptiveHorror.init();

        // Game-bus: runtime hooks.
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        HorrorScheduler.tickServer(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        NullManager.reset();
        MobLockManager.reset();
        AssaultManager.reset();
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HorrorScheduler.tickPlayer(player);
        }
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HorrorScheduler.onPlayerJoin(player);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HorrorScheduler.removePlayer(player);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        HorrorCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level() instanceof ServerLevel level
                && event.getSource().getEntity() instanceof ServerPlayer killer) {
            MobDeathHorror.onMobKilled(level, event.getEntity(), killer);
        }
    }
}
