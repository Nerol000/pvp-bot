package net.nerol.pvp_bot.bot.action;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.controller.BotAction;

import java.util.Optional;

/**
 * Utility class that translates ActionType values into actual Minecraft API calls on a BotPlayer.
 * Sticky actions (WALK_*, STRAFE_*, SPRINT, SNEAK) persist until cleared via stop() or a conflicting setter.
 * Call apply() once per tick to push sticky movement state into the bot's inputs.
 */
public class ActionPack {

    private final BotPlayer bot;
    private LookInterpolation lookInterpolation;

    // Sticky movement state
    private boolean forward = false;
    private boolean backward = false;
    private boolean strafeLeft = false;
    private boolean strafeRight = false;
    private boolean sprinting = false;
    private boolean sneaking = false;
    private boolean using = false;
    private boolean destroying = false;

    // Sticky interaction state
    private boolean destroy = false;
    private BlockPos breakingPos;
    private Direction breakingFace;
    private float destroyProgress = 0.0f;   // accumulated dig progress on breakingPos (0..1)
    private int lastBreakStage = -1;

    // Sticky aim-lock: while true, apply() re-snaps the bot's look onto its current target every
    // tick (mirrors environment.py Bot.aim_lock). Latched by LOOK_AT_TARGET, released when the
    // policy takes manual rotation control (YAW_*/PITCH_*).
    private boolean aimLock = false;

    private static class LookInterpolation {
        float targetYaw;
        float targetPitch;
        float deltaYaw;
        float deltaPitch;
        int ticksRemaining;

        LookInterpolation(float targetYaw, float targetPitch, float deltaYaw, float deltaPitch, int ticks) {
            this.targetYaw = targetYaw;
            this.targetPitch = targetPitch;
            this.deltaYaw = deltaYaw;
            this.deltaPitch = deltaPitch;
            this.ticksRemaining = ticks;
        }
    }

    public ActionPack(BotPlayer bot) {
        this.bot = bot;
    }

    // Sticky setters
    public void setWalking(boolean value)     {
        forward = value;
        if (value) backward = false;
        sprinting = false;
    }
    public void setBackward(boolean value)    {
        backward = value;
        if (value) {
            forward = false;
            sprinting = false;
        }
    }
    public void setStrafeLeft(boolean value)  { strafeLeft = value; if (value) strafeRight = false; }
    public void setStrafeRight(boolean value) { strafeRight = value; if (value) strafeLeft = false; }
    public void setSprinting(boolean value)   {
        sprinting = value;
        forward = value;
        if (value) {
            sneaking = false;
            backward = false;
        }
    }
    public void setSneaking(boolean value)    { sneaking = value; if (value) sprinting = false; }

    // Clears all sticky movement state.
    public void stop() {
        forward = false;
        backward = false;
        strafeLeft = false;
        strafeRight = false;
        sprinting = false;
        sneaking = false;
        if (using && bot.isUsingItem()) bot.releaseUsingItem();
        using = false;
        destroying = false;

        abortBreaking();
    }

    public void jump() {
        if (bot.onGround()) {
            bot.jumpFromGround();
        }
    }

    public void pickBlock() {}

    public void attack() {
        double reach = bot.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getValue();
        Vec3 eyePos  = bot.getEyePosition();
        Vec3 lookVec = bot.getLookAngle();
        Vec3 endPos  = eyePos.add(lookVec.scale(reach));

        AABB searchBox = bot.getBoundingBox()
                .expandTowards(lookVec.scale(reach))
                .inflate(1.0);

        Entity hit = null;
        double closestDistSq = reach * reach;

        for (Entity entity : bot.level().getEntities(bot, searchBox, e -> e != bot && e.isPickable())) {
            AABB entityBox = entity.getBoundingBox().inflate(entity.getPickRadius());
            Optional<Vec3> intersection = entityBox.clip(eyePos, endPos);
            if (intersection.isPresent()) {
                double distSq = eyePos.distanceToSqr(intersection.get());
                if (distSq < closestDistSq) {
                    hit = entity;
                    closestDistSq = distSq;
                }
            }
        }

        if (hit != null) {
            bot.attack(hit);
        }

        bot.swing(InteractionHand.MAIN_HAND);
    }

    public void setDestroy(boolean destroy) {
        destroying = destroy;
        if (!destroy) abortBreaking();
    }

    private void destroy() {
        double reach = bot.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue();
        HitResult hit = bot.pick(reach, 0, false);
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            clearBreakProgress();
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        Direction face = blockHit.getDirection();
        BlockState state = bot.level().getBlockState(pos);

        if (!pos.equals(breakingPos)) {
            clearBreakProgress();
            bot.gameMode.handleBlockBreakAction(
                    pos,
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    face,
                    bot.level().getMaxY(),
                    0
            );
            breakingPos = pos;
            breakingFace = face;
            destroyProgress = 0.0f;
        }
        destroyProgress += state.getDestroyProgress(bot, bot.level(), pos);
        if (destroyProgress >= 1.0f) {
            bot.gameMode.handleBlockBreakAction(
                    pos,
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    face,
                    bot.level().getMaxY(),
                    0
            );
            clearBreakProgress();
            return;
        }

        int stage = (int) (destroyProgress * 10.0f);
        if (stage != lastBreakStage) {
            bot.level().destroyBlockProgress(bot.getId(), pos, stage);
            lastBreakStage = stage;
        }
    }

    private void abortBreaking() {
        if (breakingPos != null) {
            bot.gameMode.handleBlockBreakAction(
                    breakingPos,
                    ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK,
                    breakingFace,
                    bot.level().getMaxY(),
                    0
            );
        }
        clearBreakProgress();
    }

    /** Remove the crack overlay from the block we were mining (if any) and reset dig state. */
    private void clearBreakProgress() {
        if (breakingPos != null) {
            bot.level().destroyBlockProgress(bot.getId(), breakingPos, -1);
        }
        breakingPos = null;
        breakingFace = null;
        destroyProgress = 0.0f;
        lastBreakStage = -1;
    }

    public void use(boolean use) {
        using = use;
        if (!use && bot.isUsingItem()) {
            bot.releaseUsingItem();
        }
    }

    public void interact() {
        using = false;
        bot.releaseUsingItem();
        applyRightClick();
    }

    private void applyRightClick() {
        if (bot.isUsingItem()) return;

        // 1) Manual entity raycast (MC's interaction priority: entity before block)
        double entityReach = bot.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getValue();
        Vec3 eye  = bot.getEyePosition();
        Vec3 look = bot.getLookAngle();
        Vec3 end  = eye.add(look.scale(entityReach));
        AABB searchBox = bot.getBoundingBox()
                .expandTowards(look.scale(entityReach))
                .inflate(1.0);

        Entity hitEntity = null;
        Vec3   hitLocation = null;
        double closestDistSq = entityReach * entityReach;

        for (Entity entity : bot.level().getEntities(bot, searchBox, e -> e != bot && e.isPickable())) {
            AABB entityBox = entity.getBoundingBox().inflate(entity.getPickRadius());
            Optional<Vec3> intersection = entityBox.clip(eye, end);
            if (intersection.isPresent()) {
                double distSq = eye.distanceToSqr(intersection.get());
                if (distSq < closestDistSq) {
                    hitEntity    = entity;
                    hitLocation  = intersection.get();
                    closestDistSq = distSq;
                }
            }
        }

        // 2) Block raycast at block reach
        double blockReach = bot.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue();
        HitResult blockHit = bot.pick(blockReach, 0, false);

        for (InteractionHand hand : InteractionHand.values()) {
            if (hitEntity != null) {
                bot.resetLastActionTime();
                boolean handWasEmpty   = bot.getItemInHand(hand).isEmpty();
                boolean itemFrameEmpty = (hitEntity instanceof ItemFrame) && ((ItemFrame) hitEntity).getItem().isEmpty();
                Vec3 relativeHitPos = hitLocation.subtract(hitEntity.getX(), hitEntity.getY(), hitEntity.getZ());
                if (hitEntity.interact(bot, hand, relativeHitPos).consumesAction()) {
                    return;
                }
                if (bot.interactOn(hitEntity, hand, relativeHitPos).consumesAction() && !(handWasEmpty && itemFrameEmpty)) {
                    return;
                }
            } else if (blockHit instanceof BlockHitResult bh && bh.getType() == HitResult.Type.BLOCK) {
                bot.resetLastActionTime();
                ServerLevel world = bot.level();
                BlockPos pos = bh.getBlockPos();
                Direction side = bh.getDirection();
                if (pos.getY() < bot.level().getMaxY() - (side == Direction.UP ? 1 : 0) && world.mayInteract(bot, pos)) {
                    InteractionResult result = bot.gameMode.useItemOn(bot, world, bot.getItemInHand(hand), hand, bh);
                    if (result instanceof InteractionResult.Success success) {
                        if (success.swingSource() != InteractionResult.SwingSource.NONE) {
                            bot.swing(hand);
                        }
                        return;
                    }
                }
            }

            ItemStack handItem = bot.getItemInHand(hand);
            if (bot.gameMode.useItem(bot, bot.level(), handItem, hand).consumesAction()) {
                return;
            }
        }
    }

    public void drop() {
        bot.drop(false);
    }

    public void dropStack() {
        bot.drop(true);
    }

    public void drop(int slot) {}
    public void dropStack(int slot) {}

    public void swapHands() {
        ItemStack main = bot.getMainHandItem().copy();
        ItemStack off  = bot.getOffhandItem().copy();
        bot.getInventory().setItem(bot.getInventory().getSelectedSlot(), off);
        bot.getInventory().setItem(Inventory.SLOT_OFFHAND, main);
    }

    public void hotbarSlot(int slot) {
        if (slot >= 0 && slot <= 8) {
            bot.getInventory().setSelectedSlot(slot);
        }
    }

    public void look(float yaw, float pitch) {
        bot.setYRot(yaw % 360);
        bot.setXRot(Mth.clamp(pitch, -90, 90));
        bot.setYHeadRot(yaw % 360);
    }

    public void look(Vec2 rotation) {
        this.look(rotation.y, rotation.x);
    }

    public void lookAt(Vec3 position) {
        bot.lookAt(EntityAnchorArgument.Anchor.EYES, position);
    }

    public void lookAt(Entity target) {
        if (target == null) return;


        Vec3 botPos = bot.getEyePosition();
        Vec3 targetPos = target.getEyePosition();

        double dx = targetPos.x - botPos.x;
        double dy = targetPos.y - botPos.y;
        double dz = targetPos.z - botPos.z;

        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, distXZ)));

        bot.setYRot(yaw);
        bot.setXRot(pitch);

        // Sync head + body
        bot.yHeadRot = yaw;
        bot.yBodyRot = yaw;
    }

    /** Rotates the bot's look direction by a relative delta. */
    public void turn(float deltaYaw, float deltaPitch) {
        look(bot.getYRot() + deltaYaw, bot.getXRot() + deltaPitch);
    }

    public void look(Vec2 rotation, int ticks) {
        lookInterpolated(rotation.y, rotation.x, ticks);
    }

    public void look(float yaw, float pitch, int ticks) {
        lookInterpolated(yaw, pitch, ticks);
    }

    public void turn(float deltaYaw, float deltaPitch, int ticks) {
        lookInterpolated(bot.getYRot() + deltaYaw, bot.getXRot() + deltaPitch, ticks);
    }

    public void lookAt(Vec3 position, int ticks) {
        if (ticks <= 0) {
            lookAt(position);
            return;
        }
        Vec3 eye = bot.getEyePosition();
        double dx = position.x - eye.x;
        double dy = position.y - eye.y;
        double dz = position.z - eye.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F);
        float pitch = Mth.wrapDegrees((float) (-(Mth.atan2(dy, dist) * (180.0 / Math.PI))));
        lookInterpolated(yaw, pitch, ticks);
    }

    public void lookAt(Entity entity, int ticks) {
        if (entity == null) return;
        if (ticks <= 0) {
            lookAt(entity);
            return;
        }

        Vec3 botPos = bot.getEyePosition();
        Vec3 targetPos = entity.getEyePosition();

        double dx = targetPos.x - botPos.x;
        double dy = targetPos.y - botPos.y;
        double dz = targetPos.z - botPos.z;

        double distXZ = Math.sqrt(dx * dx + dz * dz);

        float targetYaw = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0F);
        float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, distXZ)));

        // Smooth interpolation
        lookInterpolated(targetYaw, targetPitch, ticks);
    }

    private void lookInterpolated(float targetYaw, float targetPitch, int ticks) {
        if (ticks <= 0) {
            look(targetYaw, targetPitch);
            return;
        }
        float clampedPitch = Mth.clamp(targetPitch, -90, 90);
        lookInterpolation = new LookInterpolation(
                targetYaw,
                clampedPitch,
                Mth.wrapDegrees(targetYaw - bot.getYRot()) / ticks,
                (clampedPitch - bot.getXRot()) / ticks,
                ticks
        );
    }

    public void stopInterpolation() {
        lookInterpolation = null;
    }

    /**
     * Latches (or releases) sticky aim-lock. This ONLY sets a flag; the actual per-tick
     * re-tracking happens in apply(). While latched, the bot keeps its look snapped onto its
     * current target every tick until the policy takes manual rotation control (which calls
     * setLookAtTarget(false)). Distinct from the one-shot lookAt(...) methods, which aim once
     * and do not persist.
     */
    public void setLookAtTarget(boolean value) {
        aimLock = value;
    }

    public boolean isLookingAtTarget() {
        return aimLock;
    }

    /**
     * Pushes current sticky state into the bot's movement inputs.
     * Must be called each tick before super.tick() / travel().
     */
    public void apply() {
        // Sticky aim-lock: re-track the current target every tick while latched, so aim persists
        // through movement/attack/idle ticks (mirrors environment.py's per-tick re-snap).
        if (aimLock) {
            Entity target = bot.getTarget();
            if (target != null) {
                lookAt(target);
            }
        }

        if (lookInterpolation != null && lookInterpolation.ticksRemaining > 0) {
            bot.setYRot(bot.getYRot() + lookInterpolation.deltaYaw);
            bot.setXRot(Mth.clamp(bot.getXRot() + lookInterpolation.deltaPitch, -90f, 90f));
            bot.yHeadRot = bot.getYRot();
            bot.yBodyRot = bot.getYRot();
            lookInterpolation.ticksRemaining--;
            if (lookInterpolation.ticksRemaining <= 0) {
                // snap to final target to avoid drift
                bot.setYRot(lookInterpolation.targetYaw);
                bot.setXRot(lookInterpolation.targetPitch);
                lookInterpolation = null;
            }
        }

        bot.setShiftKeyDown(sneaking);
        bot.setSprinting(sprinting);

        float zza = 0;
        float xxa = 0;

        if (forward)     zza += 1.0f;
        if (backward)    zza -= 1.0f;
        if (strafeLeft)  xxa += 1.0f;
        if (strafeRight) xxa -= 1.0f;

        bot.xxa = xxa;
        bot.zza = zza;

        if (using) applyRightClick();
        if (destroying) destroy();
    }

    public void executeBotAction(BotAction action) {
        // Per-tick impulse model: clear movement flags each tick, then set the chosen one, matching
        // the simulator/environment.py (which apply per-tick impulses, not sticky toggles).
        setWalking(false);
        setBackward(false);
        setStrafeLeft(false);
        setStrafeRight(false);
        setSprinting(false);
        switch (action) {
            case FORWARD -> setWalking(true);
            case SPRINT_FORWARD -> setSprinting(true);
            case BACK -> setBackward(true);
            case STRAFE_LEFT -> setStrafeLeft(true);
            case STRAFE_RIGHT -> setStrafeRight(true);
            case ATTACK -> attack();
            case JUMP -> jump();
            case YAW_L -> { setLookAtTarget(false); turn(-15f, 0f); }
            case YAW_R -> { setLookAtTarget(false); turn(15f, 0f); }
            case YAW_L_FINE -> { setLookAtTarget(false); turn(-4f, 0f); }
            case YAW_R_FINE -> { setLookAtTarget(false); turn(4f, 0f); }
            case PITCH_UP -> { setLookAtTarget(false); turn(0f, -8f); }
            case PITCH_DOWN -> { setLookAtTarget(false); turn(0f, 8f); }
            case LOOK_AT_TARGET -> { setLookAtTarget(true); lookAt(bot.getTarget()); }
            case IDLE -> { }
        }
    }
}