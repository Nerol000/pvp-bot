package net.nerol.pvp_bot.bot.action;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.nerol.pvp_bot.bot.BotPlayer;

/**
 * Utility class that translates ActionType values into actual Minecraft API calls on a BotPlayer.
 * Sticky actions (WALK_*, STRAFE_*, SPRINT, SNEAK) persist until cleared via stop() or a conflicting setter.
 * Call apply() once per tick to push sticky movement state into the bot's inputs.
 */
public class ActionPack {

    private final BotPlayer bot;

    // Sticky movement state
    private boolean forward;
    private boolean backward;
    private boolean strafeLeft;
    private boolean strafeRight;
    private boolean sprinting;
    private boolean sneaking;

    public ActionPack(BotPlayer bot) {
        this.bot = bot;
    }

    // -------------------------------------------------------------------------
    // Sticky setters
    // -------------------------------------------------------------------------

    public void setForward(boolean value)     { forward = value; }
    public void setBackward(boolean value)    { backward = value; }
    public void setStrafeLeft(boolean value)  { strafeLeft = value; }
    public void setStrafeRight(boolean value) { strafeRight = value; }
    public void setSprinting(boolean value)   { sprinting = value; }
    public void setSneaking(boolean value)    { sneaking = value; }

    /** Clears all sticky movement state. */
    public void stop() {
        forward = false;
        backward = false;
        strafeLeft = false;
        strafeRight = false;
        sprinting = false;
        sneaking = false;
    }

    // -------------------------------------------------------------------------
    // One-shot actions
    // -------------------------------------------------------------------------

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
            .ifPresent(target -> bot.attack(target));
    }

    public void rightClick() {
        bot.startUsingItem(InteractionHand.MAIN_HAND);
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
        bot.getInventory().setItem(bot.getInventory().selected, off);
        bot.getInventory().setItem(Inventory.SLOT_OFFHAND, main);
    }

    public void hotbarSlot(int slot) {
        if (slot >= 0 && slot <= 8) {
            bot.getInventory().selected = slot;
        }
    }

    /**
     * Sets the bot's look direction to an absolute yaw/pitch.
     * yaw: degrees, 0 = south, positive = west. pitch: -90 = up, 90 = down.
     */
    public void look(float yaw, float pitch) {
        bot.setYRot(yaw);
        bot.setXRot(pitch);
        bot.setYHeadRot(yaw);
    }

    /** Rotates the bot's look direction by a relative delta. */
    public void turn(float deltaYaw, float deltaPitch) {
        look(bot.getYRot() + deltaYaw, bot.getXRot() + deltaPitch);
    }

    // -------------------------------------------------------------------------
    // apply() — call once per tick before travel()
    // -------------------------------------------------------------------------

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
    }

    // -------------------------------------------------------------------------
    // Dispatch
    // -------------------------------------------------------------------------

    /**
     * Executes a one-shot action or enables a sticky action.
     * LOOK and TURN require parameters — use look(yaw, pitch) / turn(dYaw, dPitch) directly.
     */
    public void execute(ActionType action) {
        switch (action) {
            case JUMP             -> jump();
            case SNEAK            -> setSneaking(true);
            case SPRINT           -> setSprinting(true);
            case STRAFE_LEFT      -> setStrafeLeft(true);
            case STRAFE_RIGHT     -> setStrafeRight(true);
            case WALK_FORWARD     -> setForward(true);
            case WALK_BACKWARD    -> setBackward(true);
            case LEFT_CLICK       -> leftClick();
            case RIGHT_CLICK      -> rightClick();
            case PICK_BLOCK       -> {} // requires raycasting — not yet implemented
            case DROP_ITEM        -> dropItem();
            case DROP_STACK       -> dropStack();
            case SWAP_HANDS       -> swapHands();
            case OPEN_INVENTORY   -> {} // client-driven, no-op for server-side bot
            case HOTBAR_SLOT_ONE   -> hotbarSlot(0);
            case HOTBAR_SLOT_TWO   -> hotbarSlot(1);
            case HOTBAR_SLOT_THREE -> hotbarSlot(2);
            case HOTBAR_SLOT_FOUR  -> hotbarSlot(3);
            case HOTBAR_SLOT_FIVE  -> hotbarSlot(4);
            case HOTBAR_SLOT_SIX   -> hotbarSlot(5);
            case HOTBAR_SLOT_SEVEN -> hotbarSlot(6);
            case HOTBAR_SLOT_EIGHT -> hotbarSlot(7);
            case HOTBAR_SLOT_NINE  -> hotbarSlot(8);
            case STOP             -> stop();
            case LOOK, TURN       -> {} // use look() / turn() directly
        }
    }
}
