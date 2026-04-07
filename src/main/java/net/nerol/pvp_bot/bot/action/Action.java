package net.nerol.pvp_bot.bot.action;

public enum Action {
    /// Movement
    JUMP,
    SNEAK,
    SPRINT,
    STRAFE_LEFT,
    STRAFE_RIGHT,
    WALK_FORWARD,
    WALK_BACKWARD,

    /// Gameplay
    ATTACK,
    PICK_BLOCK,
    USE,

    /// Inventory
    DROP_ITEM,
    HOTBAR_SLOT_ONE,
    HOTBAR_SLOT_TWO,
    HOTBAR_SLOT_THREE,
    HOTBAR_SLOT_FOUR,
    HOTBAR_SLOT_FIVE,
    HOTBAR_SLOT_SIX,
    HOTBAR_SLOT_SEVEN,
    HOTBAR_SLOT_EIGHT,
    HOTBAR_SLOT_NINE,
    OPEN_INVENTORY,
    SWAP_HANDS
}
