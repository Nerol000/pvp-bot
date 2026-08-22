package net.nerol.pvp_bot.bot.action;

import java.util.Random;

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
    LOOKAT,
    TURN,

    STOP; // Stops current action

    private static final Random RANDOM = new Random();

    public static ActionType getRandomAction() {
        ActionType[] values = {JUMP, SNEAK, SPRINT, STRAFE_LEFT, STRAFE_RIGHT, WALK_FORWARD, WALK_BACKWARD, LEFT_CLICK, RIGHT_CLICK, SWAP_HANDS, LOOK, TURN, STOP};
        return values[RANDOM.nextInt(values.length)];
    }
}