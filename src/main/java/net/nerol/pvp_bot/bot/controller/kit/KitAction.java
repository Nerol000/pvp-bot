package net.nerol.pvp_bot.bot.controller.kit;

/**
 * The kit action space. Indices 0-21 match {@code KIT_ACTION_NAMES} in
 * {@code RL/PythonTrainer/kit_env.py}: 0-14 are the legacy {@link
 * net.nerol.pvp_bot.bot.controller.BotAction} values, 15-21 are the kit actions. 22-23 are the
 * demo bot's attribute-swap actions ({@code DEMO_KIT_ACTION_NAMES} in demo_kit_env.py).
 */
public enum KitAction {
    IDLE, FORWARD, SPRINT_FORWARD, BACK, STRAFE_LEFT, STRAFE_RIGHT, ATTACK, JUMP,
    YAW_L, YAW_R, YAW_L_FINE, YAW_R_FINE, PITCH_UP, PITCH_DOWN, LOOK_AT_TARGET,   // 0-14
    HOLD_SWORD,      // 15
    HOLD_AXE,        // 16
    HOLD_BOW,        // 17
    HOLD_CROSSBOW,   // 18
    SWAP_HANDS,      // 19
    USE_START,       // 20  hold right-click (shield / draw bow / load or fire crossbow)
    USE_STOP,        // 21  release right-click
    SWAP_ATTACK_SWORD,  // 22  select the sword and swing in the same tick (attribute swap)
    SWAP_ATTACK_AXE;    // 23  select the axe and swing in the same tick (attribute swap)

    public static final int FIRST_KIT = 15;

    /** Same snake_case names the Python spec exports (policy_kit.json "action_names"). */
    public String specName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
