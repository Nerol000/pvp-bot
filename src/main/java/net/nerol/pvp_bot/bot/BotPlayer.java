package net.nerol.pvp_bot.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.nerol.pvp_bot.bot.action.ActionPack;
import net.nerol.pvp_bot.bot.action.BotAction;
import net.nerol.pvp_bot.bot.controller.BotMode;
import net.nerol.pvp_bot.bot.controller.BotState;
import net.nerol.pvp_bot.bot.controller.LiveController;
import net.nerol.pvp_bot.bot.controller.QTableLoader;
import net.nerol.pvp_bot.bot.reader.CSVReader;
import org.jspecify.annotations.NonNull;

import java.util.List;

public class BotPlayer extends ServerPlayer {
    /** Flip to {@link BotMode#PLAYBACK} to drive actions from bot_replay.csv (debug
     *  / fallback). {@link BotMode#LIVE} uses the trained Q-table to decide each tick. */
    private static final BotMode MODE = BotMode.LIVE;

    private final ActionPack actionPack;
    protected LivingEntity target;
    public int ping = 0;
    public static final byte SKIN_CAPE = 0x01;
    public static final byte SKIN_JACKET = 0x02;
    public static final byte SKIN_LEFT_SLEEVE = 0x04;
    public static final byte SKIN_RIGHT_SLEEVE = 0x08;
    public static final byte SKIN_LEFT_PANT = 0x10;
    public static final byte SKIN_RIGHT_PANT = 0x20;
    public static final byte SKIN_HAT = 0x40;
    public int seconds = 0;

    // PLAYBACK-mode state
    private List<BotAction> actions;
    private int actionStep = 0;

    // LIVE-mode brain (null in PLAYBACK)
    private final LiveController controller;

    public BotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info) {
        super(server, level, profile, info);

        this.actionPack = new ActionPack(this);
        this.target = null;

        if (MODE == BotMode.PLAYBACK) {
            this.actions = CSVReader.load(CSVReader.bot_replay);
            this.controller = null;
        } else {
            this.actions = null;
            LiveController loaded = null;
            try {
                double[][] q = QTableLoader.load(QTableLoader.DEFAULT_RESOURCE);
                loaded = new LiveController(q, /*exploit=*/ true);
            } catch (Exception e) {
                System.out.printf("LIVE mode: failed to load Q-table, bot will idle. %s%n", e.getMessage());
            }
            this.controller = loaded;
        }
    }


    public void setMainHand(HumanoidArm arm) {
        this.entityData.set(DATA_PLAYER_MAIN_HAND, arm);
    }

    @Override
    public void tick() {
        if (seconds % 20 == 0) seconds = 0;

        if (this.target != null && !this.target.isAlive()) this.target = null;

        if (this.target == null) {
            actionPack.stop();
        }

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

            if (!this.noPhysics) {
                this.moveTowardsClosestSpace(this.getX() - this.getBbWidth() * 0.35, this.getZ() + this.getBbWidth() * 0.35);
                this.moveTowardsClosestSpace(this.getX() - this.getBbWidth() * 0.35, this.getZ() - this.getBbWidth() * 0.35);
                this.moveTowardsClosestSpace(this.getX() + this.getBbWidth() * 0.35, this.getZ() - this.getBbWidth() * 0.35);
                this.moveTowardsClosestSpace(this.getX() + this.getBbWidth() * 0.35, this.getZ() + this.getBbWidth() * 0.35);
            }

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

        if (MODE == BotMode.PLAYBACK) {
            if (actions != null && actionStep < actions.size() - 1) {
                actionPack.executeBotAction(actions.get(actionStep));
                actionStep++;
            }
        } else if (MODE == BotMode.LIVE) {
            if (controller != null && target != null && target.isAlive()) {
                BotState state = BotState.observe(this, target);
                BotAction action = controller.decide(state);
                actionPack.executeBotAction(action);
            }
        }

        actionPack.apply();

        seconds++;
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

        botPlayerDisconnect(Component.literal("Died"));
    }

    private void moveTowardsClosestSpace(double x, double z) {
        BlockPos pos = BlockPos.containing(x, this.getY(), z);
        if (this.suffocatesAt(pos)) {
            double xd = x - pos.getX();
            double zd = z - pos.getZ();
            Direction dir = null;
            double closest = Double.MAX_VALUE;
            for (Direction direction : new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH}) {
                double axisDistance = direction.getAxis().choose(xd, 0.0, zd);
                double distanceToEdge = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 - axisDistance : axisDistance;
                if (distanceToEdge < closest && !this.suffocatesAt(pos.relative(direction))) {
                    closest = distanceToEdge;
                    dir = direction;
                }
            }
            if (dir != null) {
                Vec3 oldMovement = this.getDeltaMovement();
                if (dir.getAxis() == Direction.Axis.X) {
                    this.setDeltaMovement(0.1 * dir.getStepX(), oldMovement.y, oldMovement.z);
                } else {
                    this.setDeltaMovement(oldMovement.x, oldMovement.y, 0.1 * dir.getStepZ());
                }
            }
        }
    }

    private boolean suffocatesAt(final BlockPos pos) {
        AABB boundingBox = this.getBoundingBox();
        AABB testArea = new AABB(pos.getX(), boundingBox.minY, pos.getZ(), pos.getX() + 1.0, boundingBox.maxY, pos.getZ() + 1.0).deflate(1.0E-7);
        return this.level().collidesWithSuffocatingBlock(this, testArea);
    }

    public LivingEntity getRandomTarget(float range) {
        assert target == null;

        List<LivingEntity> entities = this.level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(range), e -> e != this && e.isAlive());

        if (entities.isEmpty()) return null;

        return entities.get(this.getRandom().nextInt(entities.size()));
    }

    public LivingEntity getTarget() {
        return target;
    }

    public void setTarget(LivingEntity target) {
        assert this.distanceToSqr(target) < 1048576; // cannot be further than 1024 blocks
        this.target = target;
    }

    public void resetAttributes() {
        for (AttributeInstance instance : this.getAttributes().getAttributesToSync()) {
            for (AttributeModifier modifier : List.copyOf(instance.getModifiers())) {
                instance.removeModifier(modifier);
            }
        }

        this.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
        this.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0.0);
        this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0);
        this.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(0.0);
        this.getAttribute(Attributes.ATTACK_SPEED).setBaseValue(4.0);
        this.getAttribute(Attributes.BLOCK_BREAK_SPEED).setBaseValue(1.0);
        this.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).setBaseValue(4.5);
        this.getAttribute(Attributes.BURNING_TIME).setBaseValue(1.0);
        this.getAttribute(Attributes.CAMERA_DISTANCE).setBaseValue(4.0);
        this.getAttribute(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE).setBaseValue(0.0);
        this.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).setBaseValue(3.0);
        this.getAttribute(Attributes.FALL_DAMAGE_MULTIPLIER).setBaseValue(1.0);
        //this.getAttribute(Attributes.FLYING_SPEED).setBaseValue(0.4);
        //this.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(32.0);
        this.getAttribute(Attributes.GRAVITY).setBaseValue(0.08);
        this.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(0.42);
        this.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0.0);
        this.getAttribute(Attributes.LUCK).setBaseValue(0.0);
        this.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(0.0);
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0);
        this.getAttribute(Attributes.MINING_EFFICIENCY).setBaseValue(0.0);
        this.getAttribute(Attributes.MOVEMENT_EFFICIENCY).setBaseValue(0.0);
        this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.1);
        this.getAttribute(Attributes.OXYGEN_BONUS).setBaseValue(0.0);
        this.getAttribute(Attributes.SAFE_FALL_DISTANCE).setBaseValue(3.0);
        this.getAttribute(Attributes.SCALE).setBaseValue(1.0);
        this.getAttribute(Attributes.SNEAKING_SPEED).setBaseValue(0.3);
        //this.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0.0);
        this.getAttribute(Attributes.STEP_HEIGHT).setBaseValue(0.6);
        this.getAttribute(Attributes.SUBMERGED_MINING_SPEED).setBaseValue(0.2);
        this.getAttribute(Attributes.SWEEPING_DAMAGE_RATIO).setBaseValue(0.0);
        //this.getAttribute(Attributes.TEMPT_RANGE).setBaseValue(10.0);
        this.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY).setBaseValue(0.0);
        this.getAttribute(Attributes.WAYPOINT_RECEIVE_RANGE).setBaseValue(60000000);
        this.getAttribute(Attributes.WAYPOINT_TRANSMIT_RANGE).setBaseValue(60000000);
    }

    public boolean isBot() {
        return true;
    }
}
