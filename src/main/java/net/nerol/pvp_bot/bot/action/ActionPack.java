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
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.nerol.pvp_bot.bot.BotPlayer;

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
    private BlockPos breakingPos;
    private Direction breakingFace;

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

    private void using(boolean value) {
        using = value;
        if (!value && bot.isUsingItem()) {
            bot.releaseUsingItem();
        }
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

        abortBreaking();
    }

    public void jump() {
        if (bot.onGround()) {
            bot.jumpFromGround();
        }
    }

    public void leftClick() {
        bot.swing(InteractionHand.MAIN_HAND);
        bot.level().getEntities(bot, bot.getBoundingBox().inflate(4.0))
                .stream()
                .filter(e -> e != bot && e.isPickable())
                .min((a, b) -> Double.compare(
                        a.distanceToSqr(bot.position()),
                        b.distanceToSqr(bot.position())
                ))
                .ifPresent(bot::attack);
    }

    /**
     * Hold left click — mines the block the bot is looking at.
     * Starts breaking on first call, continues on subsequent calls for the same block.
     * Call each tick while the button should be held.
     */
    public void breakBlock() {
        HitResult hit = bot.pick(5.0, 0, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            abortBreaking();

            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        Direction face = blockHit.getDirection();
        if (!pos.equals(breakingPos)) {
            // Looking at a different block — abort previous and start new
            abortBreaking();
            bot.gameMode.handleBlockBreakAction(
                    pos,
                    ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    face,
                    bot.level().getMaxY(),
                    0
            );
            breakingPos = pos;
            breakingFace = face;
        }
        // Same block — server ticks progress automatically, nothing to send
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
    }

    public void rightClick() {
        using(!using);
    }

    private void applyRightClick() {
        if (bot.isUsingItem()) return;

        HitResult hit = bot.pick(5.0, 0, false);

        for (InteractionHand hand : InteractionHand.values()) {
            if (hit instanceof EntityHitResult entityHit) {
                bot.resetLastActionTime();
                Entity entity = entityHit.getEntity();
                boolean handWasEmpty = bot.getItemInHand(hand).isEmpty();
                boolean itemFrameEmpty = (entity instanceof ItemFrame) && ((ItemFrame) entity).getItem().isEmpty();
                Vec3 relativeHitPos = entityHit.getLocation().subtract(entity.getX(), entity.getY(), entity.getZ());
                if (entity.interact(bot, hand, relativeHitPos).consumesAction()) {

                    return;
                }
                if (bot.interactOn(entity, hand, relativeHitPos).consumesAction() && !(handWasEmpty && itemFrameEmpty)) {
                    return;
                }
            } else if (hit instanceof BlockHitResult blockHit) {
                bot.resetLastActionTime();
                ServerLevel world = bot.level();
                BlockPos pos = blockHit.getBlockPos();
                Direction side = blockHit.getDirection();
                if (pos.getY() < bot.level().getMaxY() - (side == Direction.UP ? 1 : 0) && world.mayInteract(bot, pos)) {
                    InteractionResult result = bot.gameMode.useItemOn(bot, world, bot.getItemInHand(hand), hand, blockHit);
                    if (result instanceof InteractionResult.Success success) {
                        if (success.swingSource() != InteractionResult.SwingSource.NONE)
                            bot.swing(hand);
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
        }
    }
}