package net.nerol.pvp_bot.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.Vec3;
import net.nerol.pvp_bot.mixin.AvatarAccessor;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class BotSpawner {
    private static final Set<String> spawning = ConcurrentHashMap.newKeySet();


    public static boolean spawn(MinecraftServer server, ServerLevel level, Vec3 pos, float yaw, float pitch, String name) {
        // Offline-style deterministic UUID so re-spawns are stable (optional)
        UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(server, name);
        if (uuid == null) {
            server.services().nameToIdCache().resolveOfflineUsers(server.isDedicatedServer() && server.usesAuthentication());
            uuid = UUIDUtil.createOfflinePlayerUUID(name);
        }

        GameProfile profile = new GameProfile(uuid, name);
        spawning.add(name);

        fetchGameProfile(server, profile.id()).whenCompleteAsync((p, t) -> {
            // Always remove the name, even if exception occurs
            spawning.remove(name);
            if (t != null) {
                return;
            }

            GameProfile current;
            if (p.name().isEmpty()) {
                current = profile;
            } else {
                current = p;
            }

            BotPlayer instance = new BotPlayer(server, level, current, ClientInformation.createDefault());
            instance.snapTo(pos.x, pos.y, pos.z, yaw, pitch);
            server.getPlayerList().placeNewPlayer(new BotNet.SilentConnection(PacketFlow.SERVERBOUND), instance, new CommonListenerCookie(current, 0, instance.clientInformation(), false));
            loadPlayerData(instance);
            instance.stopRiding(); // otherwise the created bot player will be on the vehicle
            assert level != null;
            instance.teleportTo(level, pos.x, pos.y, pos.z, Set.of(), yaw, pitch, true);
            instance.setHealth(20.0F);
            Objects.requireNonNull(instance.getAttribute(Attributes.STEP_HEIGHT)).setBaseValue(0.6F);
            instance.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            instance.setRespawnPosition(
                    new ServerPlayer.RespawnConfig(
                            LevelData.RespawnData.of(
                                    instance.level().dimension(),
                                    BlockPos.containing(pos),
                                    yaw,
                                    pitch
                            ),
                            true
                    ),
                    false
            );
            instance.ping = 10;
            instance.removeAllEffects();
            instance.clearFire();
            instance.setArrowCount(0);
            instance.getFoodData().setSaturation(5);
            instance.getFoodData().setFoodLevel(20);
            instance.resetAttributes();
            instance.setYRot(yaw);
            instance.setXRot(pitch);
            server.getPlayerList().broadcastAll(new ClientboundRotateHeadPacket(instance, (byte) (instance.yHeadRot * 256 / 360)), instance.level().dimension());//instance.dimension);
            server.getPlayerList().broadcastAll(ClientboundEntityPositionSyncPacket.of(instance), instance.level().dimension());//instance.dimension);
            instance.getEntityData().set(AvatarAccessor.getModelCustomisation(), (byte) 0x7f); // show all model layers (incl. capes)

        }, server);
        return true;
    }
/*
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ClientInformation info = cookie.clientInformation();

        BotPlayer bot = new BotPlayer(server, level, profile, info);
        bot.setPos(pos.x, pos.y, pos.z);


        // Minimal connection + listener (cookie-aware)
        BotNet.SilentConnection conn = new BotNet.SilentConnection(PacketFlow.SERVERBOUND);
        BotNet.SilentGameListener listener = new BotNet.SilentGameListener(server, conn, bot, cookie);

        bot.connection = listener; // important: many server paths assume non-null
        BotNet.attachListener(conn,listener);

        PlayerList playerList = server.getPlayerList();
        playerList.placeNewPlayer(conn, bot, cookie);

        spawning.remove(name);

        return bot;
    }*/

    private static CompletableFuture<GameProfile> fetchGameProfile(MinecraftServer server, final UUID name) {
        final ResolvableProfile resolvableProfile = ResolvableProfile.createUnresolved(name);
        return resolvableProfile.resolveProfile(server.services().profileResolver());
    }

    private static void loadPlayerData(BotPlayer player) {
        player.level().getServer().getPlayerList()
                .loadPlayerData(player.nameAndId())
                .map(tag -> TagValueInput.create(
                        ProblemReporter.DISCARDING,
                        player.registryAccess(),
                        tag
                ))
                .ifPresent(valueInput -> {
                    player.load(valueInput);
                    player.loadAndSpawnEnderPearls(valueInput);
                    player.loadAndSpawnParentVehicle(valueInput);
                });
    }

    private BotSpawner() {}
}
