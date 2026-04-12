package net.nerol.pvp_bot.bot.action;

public enum ActionType {
    /// Movement
    JUMP,
    SNEAK, // sticky
    SPRINT, // sticky
    STRAFE_LEFT, // sticky
    STRAFE_RIGHT, // sticky
    WALK_FORWARD, // sticky
    WALK_BACKWARD, // sticky

    /// Gameplay
    LEFT_CLICK,
    PICK_BLOCK,
    RIGHT_CLICK,

    /// Inventory
    DROP_ITEM,
    DROP_STACK,
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
    SWAP_HANDS,

    /// Camera
    LOOK,
    TURN,

    STOP // Stops current action
}
