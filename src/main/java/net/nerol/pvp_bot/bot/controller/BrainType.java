package net.nerol.pvp_bot.bot.controller;

import net.nerol.pvp_bot.bot.controller.fsm.FSMBrain;

/** Which decision brain a BotPlayer runs. Selectable per-bot at runtime via {@code /pvpbot brain}. */
public enum BrainType {
    /** Tabular Q-table policy loaded from qtable.csv (the original behavior). */
    QTABLE,
    /** Neural-network policy loaded from policy.json (continuous obs, 360 yaw + pitch). */
    NEURAL,

    FSM,

    CHAMPION,
    TD_MAX,
    IMPROVE,
    /** Kit DQN policy loaded from policy_kit.json (66 obs + 5 held keys; sword kit: 8 actions,
     *  axe kit: 8 + 7 item actions). Exported by demo_kit_env.py. */
    KIT_NEURAL
}