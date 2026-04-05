package net.nerol.pvp_bot.bot;

import com.mojang.authlib.GameProfile;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

public class BotPlayer extends ServerPlayer {

    public BotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info) {
        super(server, level, profile, info);

        this.setGameMode(GameType.SURVIVAL);
    }

    @Override
    public void tick() {
        this.xxa = 0;
        this.zza = 0;
        this.yya = 0;
        super.tick();
        this.travel(new Vec3(0, 0, 0));
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);

        this.level().getServer().getPlayerList().broadcastSystemMessage(
                net.minecraft.network.chat.Component.literal(this.getGameProfile().name() + " left the game")
                        .withStyle(net.minecraft.ChatFormatting.YELLOW),
                false
        );

        this.level().getServer().getPlayerList().remove(this);
    }

    public boolean isBot() {return true;}
}
