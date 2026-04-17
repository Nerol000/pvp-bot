package net.nerol.pvp_bot.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.nerol.pvp_bot.PvPBot;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.BotSpawner;

import java.util.HashSet;
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
            .then(
                Commands.literal("kill")
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
            )

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
        )));
    }

    private static int spawn(CommandSourceStack src, Vec3 pos, float yaw, float pitch) {
        ServerLevel level = src.getLevel();

        BotSpawner.spawn(src.getServer(), level, pos, yaw, pitch, getNextBotName(src.getServer(), name));

        return 1;
    }

    private static void disconnectBot(ServerPlayer bot, String reason) {
        if (bot instanceof BotPlayer) {
            // Explicitly save before disconnect — onDisconnect's internal save may be skipped
            // if SilentGameListener state flags were never set (e.g. hasLoggedIn).
            ((net.nerol.pvp_bot.mixin.PlayerListInvoker) bot.getServer().getPlayerList()).invokeSave(bot);
            bot.connection.onDisconnect(new DisconnectionDetails(Component.literal(reason)));
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

    private static int disconnect(CommandSourceStack src) {
        for (ServerPlayer bot : (src.getServer()).getPlayerList().getPlayers()) {
            if (bot instanceof BotPlayer) {
                disconnectBot(bot, "Disconnected by command");

            }
        }
        return 1;
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

        // Find smallest missing index

        int i = 0;
        while (used.contains(i)) {
            i++;
        }

        return (i == 0) ? base : base + i;
    }

    private BotCommand() {}
}