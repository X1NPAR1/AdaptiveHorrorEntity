package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A lightning strike nearby leaves behind a sign bearing a localized message ("LOOK BEHIND", "I SEE
 * YOU"...). The bolt is visual-only (no fire, no damage). Sign text is a translatable component, so
 * every viewer reads it in their own language. Rare - never spammed.
 */
public final class SignEvent implements HorrorEvent {

    private static final String[] KEYS = {
            "adaptivehorror.sign.look_behind",
            "adaptivehorror.sign.i_see_you",
            "adaptivehorror.sign.go_home",
            "adaptivehorror.sign.dont_sleep"
    };

    @Override
    public String id() {
        return "sign";
    }

    @Override
    public int minDay() {
        return 3;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.signEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        return 0.6;
    }

    @Override
    public void execute(EventContext ctx) {
        final BlockPos pos = nearbyGround(ctx);
        if (pos == null) {
            return;
        }

        final LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(ctx.level);
        if (bolt != null) {
            bolt.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            bolt.setVisualOnly(true);
            ctx.level.addFreshEntity(bolt);
        }

        final int rotation = ctx.random.nextInt(16);
        final BlockState sign = Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, rotation);
        ctx.level.setBlock(pos, sign, 3);

        if (ctx.level.getBlockEntity(pos) instanceof SignBlockEntity be) {
            // 1.21 sign text is an immutable SignText; build a new one with the message on line 1.
            final SignText text = be.getFrontText()
                    .setMessage(1, Component.translatable(KEYS[ctx.random.nextInt(KEYS.length)]));
            be.setText(text, true);
            be.setChanged();
            ctx.level.sendBlockUpdated(pos, sign, sign, 3);
        }
    }

    private static BlockPos nearbyGround(EventContext ctx) {
        for (int attempt = 0; attempt < 12; attempt++) {
            final int x = ctx.player.blockPosition().getX() + ctx.random.nextInt(17) - 8;
            final int z = ctx.player.blockPosition().getZ() + ctx.random.nextInt(17) - 8;
            if (!ctx.level.hasChunkAt(new BlockPos(x, 0, z))) {
                continue;
            }
            final int y = ctx.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            final BlockPos pos = new BlockPos(x, y, z);
            if (ctx.level.getBlockState(pos).isAir()
                    && ctx.level.getBlockState(pos.below()).isSolid()) {
                return pos;
            }
        }
        return null;
    }
}
