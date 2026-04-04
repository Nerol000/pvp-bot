package net.nerol.pvp_bot.bot;

import com.mojang.authlib.GameProfile;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class BotPlayer extends ServerPlayer {

    public BotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info) {
        super(server, level, profile, info);

        this.setGameMode(GameType.SURVIVAL);
    }

    @Override
    public void tick() {
        super.tick();
    }

    public boolean isBot() {
        return true;
    }
}
