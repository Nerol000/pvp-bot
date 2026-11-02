package net.nerol.pvp_bot.bot.controller.kit;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.action.ActionPack;
import net.nerol.pvp_bot.bot.controller.BotAction;

/**
 * Executes one {@link KitAction} per tick, matching {@code kit_env.KitDuelEnv._apply_action}.
 *
 * <p>The fake player gets none of MC's client-side input handling, so this enforces what a real
 * client would: no sprint while using an item or with food <= 6, x0.2 movement while using an item,
 * no attack while using an item, and sprint exhaustion (normally charged from movement packets).
 */
public final class KitActionExecutor {

    private KitActionExecutor() {}

    public static void execute(BotPlayer bot, ActionPack pack, int index) {
        KitAction a = KitAction.values()[index];
        boolean using = bot.isUsingItem();

        if (index < KitAction.FIRST_KIT) {
            BotAction act = BotAction.values()[index];
            if (act == BotAction.SPRINT_FORWARD
                    && (using || bot.getFoodData().getFoodLevel() < KitItems.SPRINT_MIN_FOOD)) {
                act = BotAction.FORWARD;
            }
            if (act == BotAction.ATTACK && using) {
                act = BotAction.IDLE;
            }
            pack.executeBotAction(act);
            if (act == BotAction.ATTACK) {
                // MC resets the charge on every swing (client side); the server only does it on a hit.
                bot.resetAttackStrengthTicker();
            }
        } else {
            pack.executeBotAction(BotAction.IDLE);   // clears per-tick movement, like the sim
            switch (a) {
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
                default -> { }
            }
        }
        pack.setInputScale(bot.isUsingItem() ? KitItems.USE_ITEM_SPEED_MULT : 1.0f);
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

    /** Charge on-ground sprint exhaustion for the distance moved last tick (0.1 per block). */
    public static void sprintExhaustion(BotPlayer bot, double dx, double dz) {
        if (bot.isSprinting() && bot.onGround()) {
            bot.causeFoodExhaustion(KitItems.EX_SPRINT_PER_BLOCK * (float) Math.sqrt(dx * dx + dz * dz));
        }
    }
}
