package net.nerol.pvp_bot.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.OldUsersConverter;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BotSpawner {
    private static final Set<String> spawning = ConcurrentHashMap.newKeySet();


    public static BotPlayer spawn(MinecraftServer server, ServerLevel level, Vec3 pos, float yaw, float pitch, String name) {
        // Offline-style deterministic UUID so re-spawns are stable (optional)
        UUID uuid = OldUsersConverter.convertMobOwnerIfNecessary(server, name);
        //NameAndId res = server.services().nameToIdCache().get(username).orElseThrow(); //findByName  .orElse(null)
        if (uuid == null) {
            server.services().nameToIdCache().resolveOfflineUsers(server.isDedicatedServer() && server.usesAuthentication());
            uuid = UUIDUtil.createOfflinePlayerUUID(name);
        }

        GameProfile profile = new GameProfile(uuid, name);
        spawning.add(name);

        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);
        ClientInformation info = cookie.clientInformation();

        BotPlayer bot = new BotPlayer(server, level, profile, info);
        bot.setPos(pos.x, pos.y, pos.z);
        bot.setYRot(yaw);
        bot.setXRot(pitch);

        // Minimal connection + listener (cookie-aware)
        BotNet.SilentConnection conn = new BotNet.SilentConnection(PacketFlow.SERVERBOUND);
        BotNet.SilentGameListener listener = new BotNet.SilentGameListener(server, conn, bot, cookie);

        bot.connection = listener; // important: many server paths assume non-null
        BotNet.attachListener(conn,listener);

        PlayerList playerList = server.getPlayerList();
        playerList.placeNewPlayer(conn, bot, cookie);

        spawning.remove(name);

        return bot;
    }

    private BotSpawner() {}
}
