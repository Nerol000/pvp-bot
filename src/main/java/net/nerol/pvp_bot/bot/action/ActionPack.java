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
import net.minecraft.world.entity.projectile.ProjectileUtil;
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

    // Sticky interaction state
    private boolean destroy = false;
    private BlockPos breakingPos;
    private Direction breakingFace;
    private float destroyProgress = 0.0f;   // accumulated dig progress on breakingPos (0..1)

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

    // -------------------------------------------------------------------------
    // Sticky setters
    // -------------------------------------------------------------------------

    public void setWalking(boolean value)     { forward = value; if (value) backward = false; }
    public void setBackward(boolean value)    { backward = value; if (value) forward = false; }
    public void setStrafeLeft(boolean value)  { strafeLeft = value; if (value) strafeRight = false; }
    public void setStrafeRight(boolean value) { strafeRight = value; if (value) strafeLeft = false; }
    public void setSprinting(boolean value)   { sprinting = value; forward = value; if (value) sneaking = false; if (value) backward = false; }
    public void setSneaking(boolean value)    { sneaking = value; if (value) sprinting = false; }

    /**
     * Sticky right-click: simulates holding the button down. While {@code use} is true,
     * {@link #apply()} re-runs the interaction every tick (continuing item use — eating,
     * drawing a bow, holding a shield up); passing false releases it and stops any
     * in-progress use.
     */
    public void use(boolean use) {
        using = use;
        if (!use && bot.isUsingItem()) {
            bot.releaseUsingItem();
        }
    }

    /**
     * Sticky block-breaking: simulates holding left-click on a block. While true,
     * {@link #apply()} drives {@link #breakBlock()} every tick (start / continue mining the
     * block being looked at); passing false stops and aborts any in-progress break.
     */
    public void setDestroying(boolean value) {
        destroy = value;
        if (!value) abortBreaking();
    }

    /** Clears all sticky movement state. */
    public void stop() {
        forward = false;
        backward = false;
        strafeLeft = false;
        strafeRight = false;
        sprinting = false;
        sneaking = false;
        if (using && bot.isUsingItem()) bot.releaseUsingItem();
        using = false;
        destroy = false;

        abortBreaking();
    }

    public void jump() {
        if (bot.onGround()) {
            bot.jumpFromGround();
        }
    }

    public void leftClick() {
        if (bot.getAttackStrengthScale(0.5f) < 0.9f) {
            return;
        }

        bot.swing(InteractionHand.MAIN_HAND);

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
    }

    /**
     * Held left-click. Driven every tick by {@link #apply()} while {@code destroy} is set:
     * mines whatever solid block is under the crosshair and in reach.
     *
     * <p>We drive the dig the way the client does, not just by sending START: the server's
     * gameMode only advances the <em>crack animation</em> on its own and waits for a
     * STOP_DESTROY_BLOCK to actually break the block. So we accumulate the block's per-tick
     * destroy progress and, once it reaches 1.0, send STOP so the server finishes it — without
     * this the dig stalled at 100% right before breaking. Looking at air mines nothing; a block
     * placed into reach starts breaking next tick, and after one breaks the crosshair falls onto
     * whatever is behind it and that dig begins — just like genuinely holding the button.
     */
    public void breakBlock() {
        double reach = bot.getAttribute(Attributes.BLOCK_INTERACTION_RANGE).getValue();
        HitResult hit = bot.pick(reach, 0, false);

        // pick() returns a MISS-type BlockHitResult when nothing is in reach, so the type
        // check matters — without it a miss reads as a "block" at the ray's end.
        if (!(hit instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
            breakingPos = null;
            breakingFace = null;
            destroyProgress = 0.0f;
            return;
        }

        BlockPos pos = blockHit.getBlockPos();
        Direction face = blockHit.getDirection();
        BlockState state = bot.level().getBlockState(pos);

        if (!pos.equals(breakingPos)) {
            // New block under the crosshair: begin the dig. A fresh START on a different block
            // implicitly supersedes any previous one, so no abort is needed to switch.
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

        // Accumulate this block's per-tick mining progress (tool / haste / etc. all factor in
        // via getDestroyProgress, exactly as for a real player). Once mined, send STOP so the
        // server actually destroys it.
        destroyProgress += state.getDestroyProgress(bot, bot.level(), pos);
        if (destroyProgress >= 1.0f) {
            bot.gameMode.handleBlockBreakAction(
                    pos,
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    face,
                    bot.level().getMaxY(),
                    0
            );
            breakingPos = null;
            breakingFace = null;
            destroyProgress = 0.0f;
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
            breakingPos = null;
            breakingFace = null;
        }
        destroyProgress = 0.0f;
    }

    /**
     * Non-sticky right-click: performs a single interaction this tick — use item / place
     * block / interact with the entity or block being looked at — without holding the button
     * down. For a held right-click (continuous use), call {@link #use(boolean)} instead.
     */
    public void rightClick() {
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
            } else {
                ItemStack handItem = bot.getItemInHand(hand);
                if (bot.gameMode.useItem(bot, bot.level(), handItem, hand).consumesAction()) {
                    return;
                }
            }
        }
    }

    public void dropItem() {
        bot.drop(false);
    }

    public void dropStack() {
        bot.drop(true);
    }

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


    /**
     * Sets the bot's look direction to an absolute yaw/pitch.
     * yaw: degrees, 0 = south, positive = west. pitch: -90 = up, 90 = down.
     */
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
     * Pushes current sticky state into the bot's movement inputs.
     * Must be called each tick before super.tick() / travel().
     */
    public void apply() {
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
        if (destroy) breakBlock();
    }

    public void executeBotAction(BotAction action) {
        switch (action) {
            case SPRINT -> setSprinting(!sprinting);
            case MOVE_FORWARD -> setWalking(!forward);
            case MOVE_BACK -> setBackward(!backward);
            case STRAFE_LEFT -> setStrafeLeft(!strafeLeft);
            case STRAFE_RIGHT -> setStrafeRight(!strafeRight);
            case ATTACK -> leftClick();
            case TURN_LEFT_45 -> turn(-45, 0);
            case TURN_RIGHT_45 -> turn(45, 0);
            case TURN_LEFT_90 -> turn(-90, 0);
            case TURN_RIGHT_90 -> turn(90, 0);
            // Snap aim onto the current target so the real entity raycast in leftClick() can
            // connect — the in-game mirror of the simulator's LOOK_AT_TARGET. No-op if there's
            // no target (lookAt null-checks).
            case LOOK_AT_TARGET -> lookAt(bot.getTarget());
        }
    }
}