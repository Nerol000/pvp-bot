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
        super.tick();
    }

    /** Allow knockback to actually move the bot. */
    @Override
    public void knockback(double strength, double x, double z) {
        // ServerPlayer suppresses knockback by sending it to the client instead of
        // applying it server-side. We apply it directly to the velocity ourselves.
        Vec3 vel = this.getDeltaMovement();
        Vec3 knock = new Vec3(x, 0.0, z).normalize().scale(-strength);
        this.setDeltaMovement(vel.x / 2.0 - knock.x, this.onGround() ? Math.min(0.4, vel.y / 2.0 + strength) : vel.y, vel.z / 2.0 - knock.z);
    }

    /** Remove the bot from the world on death instead of entering spectator / respawn flow. */
    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        this.server.getPlayerList().remove(this);
    }

    public boolean isBot() {
        return true;
    }
}
