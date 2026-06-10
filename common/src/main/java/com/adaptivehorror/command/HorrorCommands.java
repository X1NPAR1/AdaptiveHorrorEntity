package com.adaptivehorror.command;

import com.adaptivehorror.ai.PlayerHorrorState;
import com.adaptivehorror.config.ConfigManager;
import com.adaptivehorror.config.HorrorConfig;
import com.adaptivehorror.event.MobLockManager;
import com.adaptivehorror.network.HorrorNet;
import com.adaptivehorror.npc.NullManager;
import com.adaptivehorror.scheduler.DayProgression;
import com.adaptivehorror.scheduler.EventContext;
import com.adaptivehorror.scheduler.EventRegistry;
import com.adaptivehorror.scheduler.HorrorScheduler;
import com.adaptivehorror.spawn.StalkerManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Operator-only debug/test commands under {@code /adaptivehorror} (alias {@code /ahe}). These let you
 * verify every subsystem on demand instead of waiting for the scheduler: force a spawn, trigger any
 * event or jumpscare, play any sound, inspect your live AI state, and reload the config.
 *
 * <p>Loader-agnostic: both the Forge {@code RegisterCommandsEvent} and the Fabric
 * {@code CommandRegistrationCallback} call {@link #register(CommandDispatcher)} with the same
 * dispatcher type, so the command tree is defined exactly once.
 */
public final class HorrorCommands {

    private static final String[] SOUND_NAMES = {
            "iseeyou", "scary_ambient", "jumpscare1", "jumpscare2", "jumpscare3", "jumpscare4",
            "travel1", "travel2"
    };

    private static final SuggestionProvider<CommandSourceStack> EVENT_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(EventRegistry.ids(), builder);
    private static final SuggestionProvider<CommandSourceStack> SOUND_IDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(SOUND_NAMES, builder);

    private HorrorCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        final LiteralCommandNode<CommandSourceStack> root = dispatcher.register(
                Commands.literal("adaptivehorror")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.literal("spawn").executes(HorrorCommands::spawn))
                        .then(Commands.literal("jumpscare")
                                .executes(ctx -> jumpscare(ctx, -1))
                                .then(Commands.argument("index", IntegerArgumentType.integer(1, 8))
                                        .executes(ctx -> jumpscare(ctx, IntegerArgumentType.getInteger(ctx, "index")))))
                        .then(Commands.literal("event")
                                .then(Commands.argument("id", StringArgumentType.word())
                                        .suggests(EVENT_IDS)
                                        .executes(HorrorCommands::event)))
                        .then(Commands.literal("sound")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests(SOUND_IDS)
                                        .executes(HorrorCommands::sound)))
                        .then(Commands.literal("status").executes(HorrorCommands::status))
                        .then(Commands.literal("day").executes(HorrorCommands::day))
                        .then(Commands.literal("nulljoin").executes(HorrorCommands::nullJoin))
                        .then(Commands.literal("moblock").executes(HorrorCommands::mobLock))
                        .then(Commands.literal("disclaimer").executes(HorrorCommands::disclaimer))
                        .then(Commands.literal("reload").executes(HorrorCommands::reload)));

        // Short alias: /ahe ...
        dispatcher.register(Commands.literal("ahe").requires(src -> src.hasPermission(2)).redirect(root));
    }

    private static int spawn(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final ServerPlayer player = ctx.getSource().getPlayerOrException();
        final net.minecraft.core.BlockPos pos = StalkerManager.forceSpawn(
                player, HorrorScheduler.getOrCreateState(player), HorrorScheduler.rng());
        if (pos != null) {
            final int dist = (int) Math.sqrt(player.distanceToSqr(pos.getX() + 0.5, player.getY(), pos.getZ() + 0.5));
            feedback(ctx, "null belirdi: " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                    + " (~" + dist + " blok). Etrafına bak.");
            return 1;
        }
        feedback(ctx, "Yakında uygun bir konum bulunamadı.");
        return 0;
    }

    private static int jumpscare(CommandContext<CommandSourceStack> ctx, int index) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final ServerPlayer player = ctx.getSource().getPlayerOrException();
        final int image = index < 1 ? 1 + HorrorScheduler.rng().nextInt(8) : index;
        final int sound = 1 + HorrorScheduler.rng().nextInt(4);
        HorrorNet.sendJumpscare(player, image, sound, 16);
        feedback(ctx, "Jumpscare " + image + " gönderildi.");
        return 1;
    }

    private static int event(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final ServerPlayer player = ctx.getSource().getPlayerOrException();
        final String id = StringArgumentType.getString(ctx, "id");
        final boolean ok = EventRegistry.runById(id, contextFor(player));
        feedback(ctx, ok ? "'" + id + "' olayı tetiklendi." : "Bilinmeyen olay: '" + id + "'.");
        return ok ? 1 : 0;
    }

    private static int sound(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final ServerPlayer player = ctx.getSource().getPlayerOrException();
        final String name = StringArgumentType.getString(ctx, "name");
        HorrorNet.sendSound2D(player, name, 1.0F, 1.0F);
        feedback(ctx, "'" + name + "' sesi çalındı.");
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final ServerPlayer player = ctx.getSource().getPlayerOrException();
        final PlayerHorrorState s = HorrorScheduler.getOrCreateState(player);
        final int day = DayProgression.dayOf(player.level());
        feedback(ctx, String.format(
                "Gün %d | yoğunluk %.2f | null katıldı: %s | tetikte %.2f | madencilik %.0f | kamp %.0f | afk %dsn | aktif: %s",
                day, DayProgression.intensity(day, ConfigManager.get()),
                NullManager.hasJoined() ? "evet" : "hayır", s.behavior.vigilance(),
                s.behavior.miningScore, s.behavior.campingScore, s.behavior.afkTicks / 20,
                s.activeStalkerId != null ? "evet" : "hayır"));
        return 1;
    }

    private static int day(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        final ServerPlayer player = ctx.getSource().getPlayerOrException();
        final int day = DayProgression.dayOf(player.level());
        feedback(ctx, "Oyun günü " + day + ", yoğunluk "
                + String.format("%.2f", DayProgression.intensity(day, ConfigManager.get())));
        return 1;
    }

    private static int nullJoin(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        NullManager.forceJoin(ctx.getSource().getServer());
        feedback(ctx, "null şimdi katıldı.");
        return 1;
    }

    private static int mobLock(CommandContext<CommandSourceStack> ctx) {
        MobLockManager.forceStart(ctx.getSource().getServer());
        feedback(ctx, "Kitlenme olayı başlatıldı.");
        return 1;
    }

    private static int disclaimer(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        HorrorNet.sendDisclaimer(ctx.getSource().getPlayerOrException());
        feedback(ctx, "Uyarı ekranı gönderildi.");
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> ctx) {
        ConfigManager.load();
        feedback(ctx, "Yapılandırma yeniden yüklendi.");
        return 1;
    }

    private static EventContext contextFor(ServerPlayer player) {
        final HorrorConfig config = ConfigManager.get();
        final int day = DayProgression.dayOf(player.level());
        return new EventContext(player, HorrorScheduler.getOrCreateState(player), config,
                HorrorScheduler.rng(), day, DayProgression.intensity(day, config));
    }

    private static void feedback(CommandContext<CommandSourceStack> ctx, String message) {
        // sendSuccess now takes a Supplier<Component> (lazy) in 1.21.
        ctx.getSource().sendSuccess(() -> Component.literal("[AdaptiveHorror] " + message), false);
    }
}
