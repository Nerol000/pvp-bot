package net.nerol.pvp_bot.bot.controller;

/**
 * The learned 15-action space. Order MUST stay in lock-step with
 * {@code RL/PythonTrainer/environment.py} (and the Q-table columns / neural output), because the
 * learners choose an action by index and {@code BotAction.values()[index]} must resolve to the
 * same physical action the trainer meant.
 */
public enum BotAction {
    IDLE,            // 0
    FORWARD,         // 1
    SPRINT_FORWARD,  // 2
    BACK,            // 3
    STRAFE_LEFT,     // 4
    STRAFE_RIGHT,    // 5
    ATTACK,          // 6
    JUMP,            // 7
    YAW_L,           // 8   turn -15 yaw
    YAW_R,           // 9   turn +15 yaw
    YAW_L_FINE,      // 10  turn -4 yaw
    YAW_R_FINE,      // 11  turn +4 yaw
    PITCH_UP,        // 12  turn -8 pitch
    PITCH_DOWN,      // 13  turn +8 pitch
    LOOK_AT_TARGET   // 14  snap exact aim
}