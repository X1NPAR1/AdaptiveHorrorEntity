package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.scheduler.ScheduledAction;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * "The forest is burning." When the player is in a wooded biome, with a 15% chance the canopy around
 * them catches fire: every leaf block within five chunks gets a flame set on top of it, and null says
 * so in chat. The work is amortised one chunk per tick so even a 5-chunk sweep never stalls the server.
 */
public final class ForestFireEvent implements HorrorEvent {

    private static final int CHUNK_RADIUS = 5;
    private static final int VERTICAL_BAND = 24; // scan this far below the surface for leaves
    private static final double TRIGGER_CHANCE = 0.15;

    @Override
    public String id() {
        return "forest_fire";
    }

    @Override
    public int minDay() {
        return 3;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.worldManipulation;
    }

    @Override
    public double weight(EventContext ctx) {
        // Only in the woods.
        return ctx.level.getBiome(ctx.player.blockPosition()).is(BiomeTags.IS_FOREST) ? 1.5 : 0.0;
    }

    @Override
    public void execute(EventContext ctx) {
        if (ctx.random.nextDouble() >= TRIGGER_CHANCE) {
            return; // 15% of the time it actually ignites
        }
        final ServerLevel level = ctx.level;
        final int pcx = ctx.player.blockPosition().getX() >> 4;
        final int pcz = ctx.player.blockPosition().getZ() >> 4;
        final long start = level.getGameTime();

        long delay = 0L;
        for (int cx = -CHUNK_RADIUS; cx <= CHUNK_RADIUS; cx++) {
            for (int cz = -CHUNK_RADIUS; cz <= CHUNK_RADIUS; cz++) {
                final int chunkX = pcx + cx;
                final int chunkZ = pcz + cz;
                ctx.state.scheduled.add(new ScheduledAction(start + delay,
                        () -> burnChunk(level, chunkX, chunkZ))); // one chunk per tick
                delay++;
            }
        }
        ChatMessageEvent.sendNullChat(ctx.player, Component.literal("yan"));
    }

    private static void burnChunk(ServerLevel level, int chunkX, int chunkZ) {
        final int baseX = chunkX << 4;
        final int baseZ = chunkZ << 4;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                final int x = baseX + dx;
                final int z = baseZ + dz;
                if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
                    continue;
                }
                final int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                for (int y = top; y >= top - VERTICAL_BAND; y--) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).is(BlockTags.LEAVES)
                            && level.getBlockState(pos.above()).isAir()) {
                        level.setBlock(pos.above(), Blocks.FIRE.defaultBlockState(), 3);
                    }
                }
            }
        }
    }
}
