package net.nerol.pvp_bot.bot.controller.kit;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.action.ActionPack;
import net.nerol.pvp_bot.bot.controller.BotAction;

/**
 * Executes one {@link KitAction} per tick, matching {@code demo_kit_env.DemoKitDuelEnv._apply_action}
 * (movement keys are sticky toggles, as in {@link ActionPack#executeBotAction}).
 *
 * <p>The fake player gets none of MC's client-side input handling, so this enforces what a real
 * client would: no sprint while using an item or with food <= 6 (the key stays held), x0.2 movement
 * while using an item, no attack while using an item, and sprint exhaustion (normally charged from
 * movement packets).
 */
public final class KitActionExecutor {

    private KitActionExecutor() {}

    public static void execute(BotPlayer bot, ActionPack pack, KitAction a) {
        int index = a.ordinal();
        boolean using = bot.isUsingItem();

        if (index < KitAction.FIRST_KIT) {
            BotAction act = BotAction.values()[index];
            if (act == BotAction.ATTACK && using) {
                act = BotAction.IDLE;
            }
            pack.executeBotAction(act);
            if (act == BotAction.ATTACK) {
                // MC resets the charge on every swing (client side); the server only does it on a hit.
                bot.resetAttackStrengthTicker();
            }
        } else {
            switch (a) {                              // held movement keys stay as they are
                case HOLD_SWORD -> hold(bot, KitItems.SWORD);
                case HOLD_AXE -> hold(bot, KitItems.AXE);
                case HOLD_BOW -> hold(bot, KitItems.BOW);
                case HOLD_CROSSBOW -> hold(bot, KitItems.CROSSBOW);
                case SWAP_HANDS -> {
                    if (bot.isUsingItem()) bot.stopUsingItem();   // MC: swapping always stops use
                    pack.swapHands();
                }
                case USE_START -> pack.use(true);
                case USE_STOP -> pack.use(false);
                case SWAP_ATTACK_SWORD -> swapAttack(bot, pack, KitItems.SWORD);
                case SWAP_ATTACK_AXE -> swapAttack(bot, pack, KitItems.AXE);
                default -> { }
            }
        }
        boolean usingNow = bot.isUsingItem();
        pack.setInputScale(usingNow ? KitItems.USE_ITEM_SPEED_MULT : 1.0f);
        pack.setSprintAllowed(!usingNow && bot.getFoodData().getFoodLevel() >= KitItems.SPRINT_MIN_FOOD);
    }

    private static void hold(BotPlayer bot, int itemId) {
        Inventory inv = bot.getInventory();
        if (KitItems.id(bot.getMainHandItem()) == itemId) return;
        int slot = KitItems.hotbarSlotOf(inv, itemId);
        if (slot < 0) return;                                  // not in the hotbar -> no-op
        if (bot.isUsingItem() && bot.getUsedItemHand() == InteractionHand.MAIN_HAND) {
            bot.stopUsingItem();                               // MC: slot change stops MAIN-hand use
        }
        inv.setSelectedSlot(slot);
    }

    /**
     * Attribute swap: select the item, then swing in the same tick. The brain runs after the
     * player's tick, and MC applies the new item's attributes (damage, attack speed) and resets the
     * charge only on the next tick, so this hit uses the old item's damage and charge but the new
     * item's ability (an axe disables a raised shield).
     */
    private static void swapAttack(BotPlayer bot, ActionPack pack, int itemId) {
        hold(bot, itemId);
        if (bot.isUsingItem()) return;                         // shield still raised: no attack
        pack.executeBotAction(BotAction.ATTACK);
        bot.resetAttackStrengthTicker();
    }

    /** Charge on-ground sprint exhaustion for the distance moved last tick (0.1 per block). */
    public static void sprintExhaustion(BotPlayer bot, double dx, double dz) {
        if (bot.isSprinting() && bot.onGround()) {
            bot.causeFoodExhaustion(KitItems.EX_SPRINT_PER_BLOCK * (float) Math.sqrt(dx * dx + dz * dz));
        }
    }
}
