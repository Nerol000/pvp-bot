package net.nerol.pvp_bot.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.nerol.pvp_bot.bot.BotSpawner;

public final class BotCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerTree(dispatcher);
        });
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
        )));
    }

    private static int spawn(CommandSourceStack src, Vec3 pos, float yaw, float pitch) {
        ServerLevel level = src.getLevel();

        String botName = "PracticeBot";

        BotSpawner.spawn(src.getServer(), level, pos, yaw, pitch, botName);

        src.sendSuccess(() -> net.minecraft.network.chat.Component.literal(
                "Spawned " + botName + " at " + pos + " facing yaw=" + yaw + " pitch=" + pitch
        ), true);

        return 1;
    }

    private BotCommand() {}
}