package net.nerol.pvp_bot.bot;
import com.mojang.authlib.GameProfile;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class BotSpawner {
    private static int botNums = 0;

    public static BotPlayer spawn(MinecraftServer server, ServerLevel level, Vec3 pos, float yaw, float pitch, String name) {
        // Offline-style deterministic UUID so re-spawns are stable (optional)
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        GameProfile profile = new GameProfile(uuid, name);

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

        bot.setCustomName(net.minecraft.network.chat.Component.literal("@PvPBot"));
        bot.setCustomNameVisible(true);

        botNums++;

        return bot;
    }

    private BotSpawner() {}
}
