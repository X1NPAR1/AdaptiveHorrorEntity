package com.adaptivehorror.event;

import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.entity.NullBossEntity;
import com.adaptivehorror.npc.NullManager;
import com.adaptivehorror.registry.ModEntities;
import com.adaptivehorror.scheduler.HorrorScheduler;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * The two totems.
 *
 * <ul>
 *   <li><b>Summon</b> - lighting the top of a <em>redstone block</em> with flint &amp; steel begins the
 *       ritual: ghast cries, twenty lightning strikes half a second apart, and from the fire the
 *       {@link NullBossEntity} rises with a boss bar. Beating it frees the world; if it kills the
 *       player, the totem detonates and the boss is empowered (handled in the entity).</li>
 *   <li><b>Re-invite</b> - lighting the top of a <em>diamond block</em> brings a defeated null back:
 *       "null sunucuya bağlandı".</li>
 * </ul>
 *
 * <p>Triggered from each loader's right-click hook via {@link #onLitWithFlintAndSteel}.
 */
public final class TotemManager {

    private static final int STRIKES = 20;
    private static final long STRIKE_GAP = 10L;   // 0.5s
    private static volatile UUID activeBoss;

    private TotemManager() {
    }

    public static void reset() {
        activeBoss = null;
    }

    /**
     * Called when a player right-clicks a block face with flint &amp; steel. Returns true if it kicked
     * off a totem (so the caller can treat the interaction as handled).
     */
    public static boolean onLitWithFlintAndSteel(ServerLevel level, BlockPos clicked, Direction face,
                                                 ServerPlayer player) {
        if (face != Direction.UP) {
            return false;
        }
        final BlockState state = level.getBlockState(clicked);
        if (state.is(Blocks.REDSTONE_BLOCK)) {
            return summon(level, clicked, player);
        }
        if (state.is(Blocks.DIAMOND_BLOCK)) {
            return reinvite(level, clicked, player);
        }
        return false;
    }

    // --- summon ---------------------------------------------------------------------------------

    private static boolean summon(ServerLevel level, BlockPos totem, ServerPlayer player) {
        if (bossAlive(level)) {
            player.sendSystemMessage(Component.literal("Çağrı zaten sürüyor...").withStyle(ChatFormatting.DARK_RED));
            return true;
        }
        final BlockPos fireAt = totem.above();
        level.setBlock(fireAt, Blocks.FIRE.defaultBlockState(), 3);
        broadcast(level, "Bir şey uyanıyor...", ChatFormatting.DARK_RED);

        final PlayerHorrorState st = HorrorScheduler.getOrCreateState(player);
        final long start = level.getGameTime();
        for (int i = 0; i < STRIKES; i++) {
            final int idx = i;
            st.scheduled.add(new ScheduledAction(start + (long) i * STRIKE_GAP, () -> {
                strike(level, fireAt);
                if (idx % 2 == 0) {
                    level.playSound(null, fireAt, SoundEvents.GHAST_SCREAM, SoundSource.HOSTILE, 3.0F, 0.6F);
                }
            }));
        }
        // After the storm, the boss rises from the fire.
        st.scheduled.add(new ScheduledAction(start + (long) STRIKES * STRIKE_GAP + 10L,
                () -> spawnBoss(level, fireAt, totem)));
        return true;
    }

    private static void spawnBoss(ServerLevel level, BlockPos at, BlockPos totem) {
        final NullBossEntity boss = ModEntities.NULL_BOSS.create(level);
        if (boss == null) {
            return;
        }
        boss.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
        boss.setTotemPos(totem);
        if (!level.addFreshEntity(boss)) {
            return;
        }
        activeBoss = boss.getUUID();
        boss.roar(level);
        for (int i = 0; i < 4; i++) {
            level.setBlock(at.relative(Direction.from2DDataValue(i)), Blocks.FIRE.defaultBlockState(), 3);
        }
        broadcast(level, "null çağrıldı.", ChatFormatting.DARK_RED);
    }

    private static void strike(ServerLevel level, BlockPos at) {
        final LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            bolt.setVisualOnly(true); // no collateral griefing - the fire is placed deliberately
            level.addFreshEntity(bolt);
        }
        level.setBlock(at, Blocks.FIRE.defaultBlockState(), 3);
    }

    private static boolean bossAlive(ServerLevel level) {
        if (activeBoss == null) {
            return false;
        }
        final Entity e = level.getEntity(activeBoss);
        return e instanceof NullBossEntity && e.isAlive();
    }

    // --- re-invite ------------------------------------------------------------------------------

    private static boolean reinvite(ServerLevel level, BlockPos diamond, ServerPlayer player) {
        if (!NullManager.isDefeated()) {
            return false; // diamond block only matters once null has been beaten
        }
        level.setBlock(diamond.above(), Blocks.FIRE.defaultBlockState(), 3);
        level.playSound(null, diamond, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 0.8F);
        NullManager.invite(level.getServer());
        return true;
    }

    private static void broadcast(ServerLevel level, String message, ChatFormatting color) {
        final Component c = Component.literal(message).withStyle(color);
        level.getServer().getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(c));
    }
}
