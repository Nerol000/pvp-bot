package net.nerol.pvp_bot.bot.controller.kit;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Item ids and constants shared with {@code RL/PythonTrainer/kits.py}. The ids index the one-hot
 * item slots of the kit observation, so they MUST match {@code kits.py} (EMPTY..SHIELD = 0..5).
 */
public final class KitItems {
    public static final int EMPTY = 0, SWORD = 1, AXE = 2, BOW = 3, CROSSBOW = 4, SHIELD = 5;
    public static final int NUM_ITEMS = 6;

    // kits.py constants used by the encoder / executor
    public static final double BOW_FULL_DRAW = 20.0;
    public static final double CROSSBOW_LOAD_TICKS = 25.0;
    public static final int SPRINT_MIN_FOOD = 7;          // MC: sprint needs food > 6
    public static final float USE_ITEM_SPEED_MULT = 0.2f; // MC: client slows input x0.2 while using
    public static final float EX_SPRINT_PER_BLOCK = 0.1f; // MC: sprint exhaustion per block
    public static final double ARROW_SENSE_RANGE = 30.0;

    private KitItems() {}

    /** Map a stack to its kit id; anything that isn't part of a kit reads as EMPTY. */
    public static int id(ItemStack s) {
        if (s == null || s.isEmpty()) return EMPTY;
        if (s.is(ItemTags.SWORDS)) return SWORD;        // VERIFY(26.2): ItemTags.SWORDS exists
        if (s.is(ItemTags.AXES)) return AXE;
        if (s.is(Items.BOW)) return BOW;
        if (s.is(Items.CROSSBOW)) return CROSSBOW;
        if (s.is(Items.SHIELD)) return SHIELD;
        return EMPTY;
    }

    /** First hotbar slot (0-8) holding the given kit item, or -1. */
    public static int hotbarSlotOf(Inventory inv, int itemId) {
        for (int i = 0; i < 9; i++) {
            if (id(inv.getItem(i)) == itemId) return i;
        }
        return -1;
    }
}
