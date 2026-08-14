package net.nerol.pvp_bot.bot.controller;

import net.nerol.pvp_bot.bot.controller.fsm.FSMBrain;

/** Which decision brain a BotPlayer runs. Selectable per-bot at runtime via {@code /pvpbot brain}. */
public enum BrainType {
    /** Tabular Q-table policy loaded from qtable.csv (the original behavior). */
    QTABLE,
    /** Neural-network policy loaded from policy.json (continuous obs, 360 yaw + pitch). */
    NEURAL,
    /** Hand-coded rule-based baseline ({@link FSMBrain}); loads no files. */
    FSM,
    /** H2 Champion arm — win-seeking s-tapper ({@link net.nerol.pvp_bot.bot.controller.fsm.ParameterizedFSMBrain}). */
    CHAMPION,
    /** H1 TD-Error / Teacher arm — engaged-but-unpredictable, surprise-maximizing behavior. */
    TD_MAX,
    /** H2-proper Improve arm — bait-and-punish counter (improvement-optimizing teacher). */
    IMPROVE
}