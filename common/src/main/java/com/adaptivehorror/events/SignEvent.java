package com.adaptivehorror.events;

import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.HorrorEvent;
import com.adaptivehorror.util.PlayerLocationService;
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
 * A lightning strike leaves behind a sign. Two flavours:
 * <ul>
 *   <li><b>Sign 1</b> (75%): one of twenty-five short, ominous messages.</li>
 *   <li><b>Sign 2</b> (25%): the player's own computer name, city and country, and "I'm near you" -
 *       the most personal scare in the mod.</li>
 * </ul>
 */
public final class SignEvent implements HorrorEvent {

    /** Twenty-five message variants, each up to four sign lines. */
    private static final String[][] MESSAGES = {
            {"ARKANA", "BAK"},
            {"EVİNE", "GİT"},
            {"SENİ", "GÖRÜYORUM"},
            {"UYUMA"},
            {"BURADAYIM"},
            {"ÇOK", "GEÇ"},
            {"KAÇAMAZSIN"},
            {"YALNIZ", "DEĞİLSİN"},
            {"BENİ", "GÖRDÜN", "MÜ"},
            {"GERİ", "DÖN"},
            {"DURMA", "KOŞ"},
            {"İZLİYORUM"},
            {"SESSİZ", "OL"},
            {"O", "ARKANDA"},
            {"GÖZLERİNİ", "KAPAT"},
            {"GELİYORUM"},
            {"SAKLAN"},
            {"NEFES", "ALMA"},
            {"DUYDUN", "MU"},
            {"HÂLÂ", "BURADAYIM"},
            {"ÇIKIŞ", "YOK"},
            {"BANA", "BAKMA"},
            {"SON", "GECE"},
            {"BENİMLE", "GEL"},
            {"GÜLÜMSE"},
            // --- 50 new lines ---
            {"SENİ", "BULDUM"},
            {"KOŞMAYI", "BIRAK"},
            {"ARTIK", "BENİMSİN"},
            {"KAPIYI", "AÇMA"},
            {"IŞIĞI", "KAPAT"},
            {"O", "İÇERİDE"},
            {"YAKLAŞIYOR"},
            {"DÖNME", "ARKANA"},
            {"SENİ", "DUYUYOR"},
            {"ÇOK", "YAKINDA"},
            {"BURADA", "KAL"},
            {"SAKIN", "UYUMA"},
            {"GÖZLERİN", "BENİM"},
            {"ADINI", "BİLİYORUM"},
            {"EVİN", "GÜVENLİ", "DEĞİL"},
            {"PERDEYİ", "KAPAT"},
            {"O", "HÂLÂ", "İZLİYOR"},
            {"NEFESİNİ", "TUT"},
            {"BENDEN", "KAÇMA"},
            {"SENİ", "İSTİYOR"},
            {"GERİYE", "BAKMA"},
            {"YATAĞINA", "BAK"},
            {"DUVARDAKİ", "GÖLGE"},
            {"O", "SEN", "DEĞİLSİN"},
            {"AĞLAMA"},
            {"YALVARMA"},
            {"SES", "ÇIKARMA"},
            {"ONLAR", "GELDİ"},
            {"BİZ", "ÇOĞUZ"},
            {"SENİ", "ÖZLEDİM"},
            {"HATIRLIYOR", "MUSUN"},
            {"BU", "SENİN", "HATAN"},
            {"GERİ", "ALAMAZSIN"},
            {"SON", "NEFES"},
            {"KARANLIK", "GELİYOR"},
            {"SAATİN", "DOLDU"},
            {"KİMSE", "GELMEYECEK"},
            {"YARDIM", "YOK"},
            {"DUVARLARI", "DİNLE"},
            {"TAVANA", "BAK"},
            {"ZEMİNİN", "ALTINDA"},
            {"PENCEREYE", "BAKMA"},
            {"BENİ", "BESLE"},
            {"AÇIM"},
            {"DAHA", "YAKINA", "GEL"},
            {"OYUN", "BİTTİ"},
            {"SIRA", "SENDE"},
            {"SON", "KEZ", "GÜLÜMSE"},
            {"SENİ", "İZLEDİM", "HEP"},
            {"ASLA", "YALNIZ", "DEĞİLSİN"}
    };

    @Override
    public String id() {
        return "sign";
    }

    @Override
    public int minDay() {
        return 2;
    }

    @Override
    public boolean isEnabled(HorrorConfig config) {
        return config.features.signEvents;
    }

    @Override
    public double weight(EventContext ctx) {
        return 0.8;
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

        final BlockState sign = Blocks.OAK_SIGN.defaultBlockState()
                .setValue(StandingSignBlock.ROTATION, ctx.random.nextInt(16));
        ctx.level.setBlock(pos, sign, 3);

        if (ctx.level.getBlockEntity(pos) instanceof SignBlockEntity be) {
            final String[] lines = ctx.random.nextFloat() < 0.75F ? randomMessage(ctx) : personalLines(ctx);
            SignText text = be.getFrontText();
            for (int i = 0; i < 4; i++) {
                text = text.setMessage(i, Component.literal(i < lines.length ? lines[i] : ""));
            }
            be.setText(text, true);
            be.setChanged();
            ctx.level.sendBlockUpdated(pos, sign, sign, 3);
        }
    }

    private static String[] randomMessage(EventContext ctx) {
        return MESSAGES[ctx.random.nextInt(MESSAGES.length)];
    }

    /** Sign 2: the personalised message - host, city, country, and the public IP at the very bottom. */
    private static String[] personalLines(EventContext ctx) {
        return new String[]{
                clip(PlayerLocationService.hostName()),
                clip(PlayerLocationService.city()),
                clip(PlayerLocationService.country()),
                clip(PlayerLocationService.ip())
        };
    }

    private static String clip(String s) {
        if (s == null || s.isEmpty()) {
            return "?";
        }
        return s.length() > 15 ? s.substring(0, 15) : s;
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
            if (ctx.level.getBlockState(pos).isAir() && ctx.level.getBlockState(pos.below()).isSolid()) {
                return pos;
            }
        }
        return null;
    }
}
