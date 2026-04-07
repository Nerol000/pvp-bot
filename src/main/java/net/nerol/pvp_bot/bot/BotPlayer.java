package net.nerol.pvp_bot.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public class BotPlayer extends ServerPlayer {

    public BotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info) {
        super(server, level, profile, info);

        this.setGameMode(GameType.SURVIVAL);
    }

    @Override
    public void tick() {
        if (this.level().getServer().getTickCount() % 10 == 0) {
            this.connection.resetPosition();
            this.level().getChunkSource().move(this);
        }
        try {
            // Movement pretick stuff
            double startX = this.getX();
            double startY = this.getY();
            double startZ = this.getZ();

            super.tick();

            this.doCheckFallDamage(
                    this.getDeltaMovement().x,
                    this.getDeltaMovement().y,
                    this.getDeltaMovement().z,
                    this.onGround()
            );

            this.doTick();

            // Fixes getKnownMovement and in turn spear right clicks
            Vec3 movement = new Vec3(this.getX() - startX, this.getY() - startY, this.getZ() - startZ);
            this.setKnownMovement(movement);
            if (movement.lengthSqr() > 0.00001F) {
                this.resetLastActionTime();
            }
        } catch (NullPointerException ignored) {}
        //this.xxa = 0;
        //this.zza = 0;
        //this.yya = 0;
        //super.tick();
        //this.travel(Vec3.ZERO);
    }


    @Override
    public void onEquipItem(final @NonNull EquipmentSlot slot, final @NonNull ItemStack previous, final @NonNull ItemStack stack) {
        if (!isUsingItem()) super.onEquipItem(slot, previous, stack);
    }

    private void shakeOff() {
        if (getVehicle() instanceof Player) stopRiding();
        for (Entity passenger : getIndirectPassengers()) {
            if (passenger instanceof Player) passenger.stopRiding();
        }
    }

    @Override
    public ServerPlayer teleport(@NonNull TeleportTransition serverLevel) {
        super.teleport(serverLevel);
        if (wonGame) {
            ServerboundClientCommandPacket p = new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN);
            connection.handleClientCommand(p);
        }

        if (connection.player.isChangingDimension()) {
            connection.player.hasChangedDimension();
        }
        return connection.player;
    }

    public void botPlayerDisconnect(Component reason) {
        this.level().getServer().schedule(new TickTask(this.level().getServer().getTickCount(), () ->
                this.connection.onDisconnect(new DisconnectionDetails(reason))
        ));
    }

    @Override
    public void kill(@NonNull ServerLevel level) {
        kill(Component.literal("Killed"));
    }

    public void kill(Component reason) {
        shakeOff();

        if (reason.getContents() instanceof TranslatableContents text
                && text.getKey().equals("multiplayer.disconnect.duplicate_login")) {
            this.connection.onDisconnect(new DisconnectionDetails(reason));
            return;
        }
        this.hurtServer(this.level(), this.level().damageSources().fellOutOfWorld(), Float.MAX_VALUE);
    }

    @Override
    public void die(@NonNull DamageSource cause) {
        shakeOff();
        super.die(cause);

        MinecraftServer server = this.level().getServer();

        botPlayerDisconnect(Component.literal("Died"));

        server.execute(() -> {

            ServerPlayer p = this.connection.player;
            if (p instanceof BotPlayer bot) {
                bot.setHealth(20.0F);
                bot.foodData = new FoodData();
                bot.setExperienceLevels(0);
                bot.setExperiencePoints(0);
            }
        });
    }

    public boolean isBot() {
        return true;
    }
}
