package net.nerol.pvp_bot.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.nerol.pvp_bot.PvPBot;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.BotSpawner;
import net.nerol.pvp_bot.bot.controller.BrainType;
import net.nerol.pvp_bot.mixin.ServerCommonPacketListenerImplAccessor;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class BotCommand {
    private static final String name = "PracticeBot";
    private static final SimpleCommandExceptionType BOT_NOT_FOUND =
            new SimpleCommandExceptionType(Component.literal("Bot not found!"));

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                registerTree(dispatcher));
    }

    public static void registerTree(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pvpbot").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("spawn")
                .executes(ctx -> {
                    var src = ctx.getSource();
                    Vec3 pos = src.getPosition();
                    float yaw = src.getRotation().y;
                    float pitch = src.getRotation().x;
                    return spawn(src, pos, yaw, pitch);
                })

                // at <pos> ...
                .then(Commands.literal("at")
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                        .executes(ctx -> {
                            var src = ctx.getSource();
                            Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                            float yaw = src.getRotation().y;
                            float pitch = src.getRotation().x;
                            return spawn(src, pos, yaw, pitch);
                        })
                .then(Commands.literal("facing")
                    .then(Commands.argument("yaw", FloatArgumentType.floatArg(-180f, 180f))
                        .then(Commands.argument("pitch", FloatArgumentType.floatArg(-90f, 90f))
                            .executes(ctx -> {
                                var src = ctx.getSource();
                                Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                                float yaw = FloatArgumentType.getFloat(ctx, "yaw");
                                float pitch = FloatArgumentType.getFloat(ctx, "pitch");
                                return spawn(src, pos, yaw, pitch);
                            })
                        )
                    )
                )
            )
        )

        // facing <yaw> <pitch> ...
        .then(Commands.literal("facing")
            .then(Commands.argument("yaw", FloatArgumentType.floatArg(-180f, 180f))
                .then(Commands.argument("pitch", FloatArgumentType.floatArg(-90f, 90f))
                    .executes(ctx -> {
                        var src = ctx.getSource();
                        Vec3 pos = src.getPosition();
                        float yaw = FloatArgumentType.getFloat(ctx, "yaw");
                        float pitch = FloatArgumentType.getFloat(ctx, "pitch");
                        return spawn(src, pos, yaw, pitch);
                    }).then(Commands.literal("at")
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                            .executes(ctx -> {
                                var src = ctx.getSource();
                                Vec3 pos = Vec3Argument.getVec3(ctx, "pos");
                                float yaw = FloatArgumentType.getFloat(ctx, "yaw");
                                float pitch = FloatArgumentType.getFloat(ctx, "pitch");
                                return spawn(src, pos, yaw, pitch);
                            })
                        )
                    )
                )
            )
        ))
            .then(Commands.literal("setPing")
                    .then(Commands.argument("ping", IntegerArgumentType.integer(0))
                            .suggests((ctx, builder) -> {
                                for (int i = 0; i <= 250; i += 50) {
                                    builder.suggest(i);
                                }
                                return builder.buildFuture();
                            })
                            // No bot name -> apply to every bot.
                            .executes(ctx -> setPingAll(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "ping")))
                            // Optional bot name -> apply to just that bot.
                            .then(Commands.argument("bot", StringArgumentType.word())
                                    .suggests((ctx, builder) -> {
                                        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                            if (player instanceof BotPlayer bot) {
                                                builder.suggest(bot.getName().getString());
                                            }
                                        }
                                        return builder.buildFuture();
                                    })
                                    .executes(ctx -> setPingOne(
                                            ctx.getSource(),
                                            StringArgumentType.getString(ctx, "bot"),
                                            IntegerArgumentType.getInteger(ctx, "ping")))
                            )
                    )
            )
        .then(Commands.literal("setTarget")
                .then(
                        Commands.argument("bot", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                        if (player instanceof BotPlayer bot) {
                                            builder.suggest(bot.getName().getString());
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .then(
                                        Commands.argument("target", StringArgumentType.word())
                                                .suggests((ctx, builder) -> {
                                                    for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                                        builder.suggest(player.getName().getString());
                                                    }
                                                    return builder.buildFuture();
                                                })
                                                .executes(ctx ->
                                                        setTarget(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "bot"),
                                                                StringArgumentType.getString(ctx, "target")
                                                        )
                                                )
                                )
                )
        ).then(Commands.literal("brain")
                        .then(Commands.argument("bot", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                        if (player instanceof BotPlayer bot) {
                                            builder.suggest(bot.getName().getString());
                                        }
                                    }
                                    return builder.buildFuture();
                                })
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            builder.suggest("qtable");
                                            builder.suggest("neural");
                                            builder.suggest("fsm");
                                            builder.suggest("champion");
                                            builder.suggest("td_max");
                                            builder.suggest("improve");
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> setBrain(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "bot"),
                                                StringArgumentType.getString(ctx, "type")))
                                )
                        )
                )
        .then(Commands.literal("kill")
                .executes(ctx -> disconnect(ctx.getSource()))
                .then(
                        Commands.argument("botname", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    var players = ctx.getSource().getServer().getPlayerList().getPlayers();

                                    for (ServerPlayer player : players) {
                                        if (player instanceof BotPlayer bot) {
                                            builder.suggest(bot.getName().getString());
                                        }
                                    }

                                    return builder.buildFuture();
                                })
                                .executes(ctx ->
                                        disconnectBotByName(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "botname"))
                                )
                )
        ));
    }

    private static int spawn(CommandSourceStack src, Vec3 pos, float yaw, float pitch) {
        ServerLevel level = src.getLevel();

        BotSpawner.spawn(src.getServer(), level, pos, yaw, pitch, getNextBotName(src.getServer(), name));

        return 1;
    }

    private static int setTarget(CommandSourceStack src, String botName, String target) throws CommandSyntaxException {
        if (botName.equalsIgnoreCase(target))
            throw new SimpleCommandExceptionType(Component.literal("Bot cannot target itself!")).create();

        LivingEntity tar = null;
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            if (player.getName().getString().equalsIgnoreCase(target)) {
               tar  =  player;
            }
        }

        if (tar == null) throw new SimpleCommandExceptionType(Component.literal("Target not found!")).create();

        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            if (player instanceof BotPlayer bot && bot.getName().getString().equalsIgnoreCase(botName)) {
                bot.setTarget(tar);
                return 1;
            }
        }
        throw new SimpleCommandExceptionType(Component.literal("Bot not found!")).create();
    }

    private static void disconnectBot(ServerPlayer bot, String reason) {
        if (bot instanceof BotPlayer) {
            ((BotPlayer) bot).botPlayerDisconnect(Component.literal(reason));
        }
    }

    private static int disconnectBotByName(CommandSourceStack src, String name) throws CommandSyntaxException {
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            if (player instanceof BotPlayer bot && bot.getName().getString().equalsIgnoreCase(name)) {
                disconnectBot(bot, "Disconnected by command");
                return 1;
            }
        }
        throw new SimpleCommandExceptionType(Component.literal("Bot not found!")).create();
    }

    private static int disconnect(CommandSourceStack src) throws CommandSyntaxException {
        for (ServerPlayer bot : (src.getServer()).getPlayerList().getPlayers()) {
            if (bot instanceof BotPlayer) {
                disconnectBot(bot, "Disconnected by command");
            }
        }
        return 1;
    }

    private static int setBrain(CommandSourceStack src, String botName, String type) throws CommandSyntaxException {
        BrainType brain;
        try {
            brain = BrainType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "Brain must be one of: qtable, neural, fsm, champion, td_max, improve")).create();
        }
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            if (player instanceof BotPlayer bot && bot.getName().getString().equalsIgnoreCase(botName)) {
                if (bot.setBrain(brain)) {
                    src.sendSuccess(() -> Component.literal(
                            "Set " + bot.getName().getString() + "'s brain to " + brain.name().toLowerCase(Locale.ROOT)), false);
                    return 1;
                }
                throw new SimpleCommandExceptionType(Component.literal(
                        "Failed to load " + type.toLowerCase(Locale.ROOT) + " brain (missing policy file?)")).create();
            }
        }
        throw BOT_NOT_FOUND.create();
    }

    /** Push a ping value onto a single bot: store it, set the connection latency, and
     *  broadcast the tab-list update so clients show the new ping. */
    private static void applyPing(BotPlayer bot, int ping) {
        bot.ping = ping;
        ((ServerCommonPacketListenerImplAccessor) bot.connection).setLatency(ping);
        bot.level().getServer().getPlayerList().broadcastAll(
                new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY, bot));
    }

    private static int setPingAll(CommandSourceStack src, int ping) {
        int count = 0;
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            if (player instanceof BotPlayer bot) {
                applyPing(bot, ping);
                count++;
            }
        }
        final int n = count;
        src.sendSuccess(() -> Component.literal("Set ping to " + ping + "ms for " + n + " bot(s)"), false);
        return count;
    }

    private static int setPingOne(CommandSourceStack src, String botName, int ping) throws CommandSyntaxException {
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            if (player instanceof BotPlayer bot && bot.getName().getString().equalsIgnoreCase(botName)) {
                applyPing(bot, ping);
                src.sendSuccess(() -> Component.literal("Set " + bot.getName().getString() + "'s ping to " + ping + "ms"), false);
                return 1;
            }
        }
        throw BOT_NOT_FOUND.create();
    }

    public static String getNextBotName(MinecraftServer server, String base) {
        Set<Integer> used = new HashSet<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            String name = player.getGameProfile().name();


            if (name.equals(base)) {
                used.add(0);
                continue;
            }

            if (name.startsWith(base)) {
                String suffix = name.substring(base.length());

                if (!suffix.isEmpty() && suffix.chars().allMatch(Character::isDigit)) {
                    int num = Integer.parseInt(suffix);
                    used.add(num);
                }
            }
        }

        for (Integer i : used) {
            PvPBot.LOGGER.info(String.valueOf(i));
        }

        int i = 0;
        while (used.contains(i)) {
            i++;
        }

        return (i == 0) ? base : base + i;
    }

    private BotCommand() {}
}