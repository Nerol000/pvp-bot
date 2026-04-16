package net.nerol.pvp_bot.bot.action;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
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

    // Sticky interaction state
    private boolean rightClicking;
    private BlockPos breakingPos;
    private Direction breakingFace;

    public ActionPack(BotPlayer bot) {
        this.bot = bot;
    }

    // -------------------------------------------------------------------------
    // Sticky setters
    // -------------------------------------------------------------------------

    public void setForward(boolean value)     { forward = value; if (value) backward = false; }
    public void setBackward(boolean value)    { backward = value; if (value) forward = false; }
    public void setStrafeLeft(boolean value)  { strafeLeft = value; if (value) strafeRight = false; }
    public void setStrafeRight(boolean value) { strafeRight = value; if (value) strafeLeft = false; }
    public void setSprinting(boolean value)   { sprinting = value; if (value) sneaking = false; }
    public void setSneaking(boolean value)    { sneaking = value; if (value) sprinting = false; }

    public void setRightClicking(boolean value) { rightClicking = value; }

    /** Clears all sticky state. */
    public void stop() {
        forward = false;
        backward = false;
        strafeLeft = false;
        strafeRight = false;
        sprinting = false;
        sneaking = false;
        rightClicking = false;
        breakingPos = null;
        breakingFace = null;
    }

    // -------------------------------------------------------------------------
    // One-shot actions
    // -------------------------------------------------------------------------

    public void jump() {
        if (bot.onGround()) {
            bot.jumpFromGround();
        }
    }

    /** Single left click — swings and attacks the nearest entity in reach. */
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

    /**
     * Hold left click — mines the block the bot is looking at.
     * Starts breaking on first call, continues on subsequent calls for the same block.
     * Call each tick while the button should be held.
     */
    public void leftClickHold() {
        HitResult hit = bot.pick(5.0, 0, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            breakingPos = null;
            breakingFace = null;
            return;
        }
        BlockPos pos = blockHit.getBlockPos();
        Direction face = blockHit.getDirection();
        if (!pos.equals(breakingPos)) {
            // New block — start breaking
            bot.gameMode.startDestroyBlock(pos, face);
            breakingPos = pos;
            breakingFace = face;
        } else {
            // Same block — continue breaking
            bot.gameMode.continueDestroyBlock(pos, face);
        }
    }

    /**
     * Toggle right click on/off.
     * When active, apply() calls applyRightClick() each tick which:
     *  - interacts with entities (mounting, trading, etc.)
     *  - uses items on blocks (chests, doors, etc.)
     *  - uses items in air — mainhand first, offhand if mainhand passes
     */
    public void rightClick() {
        setRightClicking(!rightClicking);
    }

    private void applyRightClick() {
        HitResult hit = bot.pick(5.0, 0, false);

        if (hit instanceof EntityHitResult entityHit) {
            // Entity interaction: mounting, villager trades, etc.
            InteractionResult result = bot.interactOn(entityHit.getEntity(), InteractionHand.MAIN_HAND);
            if (!result.consumesAction()) {
                bot.interactOn(entityHit.getEntity(), InteractionHand.OFF_HAND);
            }
        } else if (hit instanceof BlockHitResult blockHit) {
            // Block interaction: chests, doors, crafting tables, etc.
            // Try mainhand first, then offhand
            InteractionResult result = bot.gameMode.useItemOn(
                bot, bot.level(), bot.getMainHandItem(), InteractionHand.MAIN_HAND, blockHit);
            if (!result.consumesAction()) {
                bot.gameMode.useItemOn(
                    bot, bot.level(), bot.getOffhandItem(), InteractionHand.OFF_HAND, blockHit);
            }
        } else {
            // No target — use item in air (food, bows, potions, etc.)
            InteractionResult result = bot.gameMode.useItem(bot, bot.level(), InteractionHand.MAIN_HAND);
            if (!result.consumesAction()) {
                bot.gameMode.useItem(bot, bot.level(), InteractionHand.OFF_HAND);
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

        if (rightClicking) applyRightClick();
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
            case RIGHT_CLICK      -> rightClick(); // toggles on/off
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
