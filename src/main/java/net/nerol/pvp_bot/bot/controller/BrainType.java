package net.nerol.pvp_bot.bot.controller;

/** Which decision brain a BotPlayer runs. Selectable per-bot at runtime via {@code /pvpbot brain}. */
public enum BrainType {
    /** Tabular Q-table policy loaded from qtable.csv (the original behavior). */
    QTABLE,
    /** Neural-network policy loaded from policy.json (continuous obs, 360 yaw + pitch). */
    NEURAL,
    /** Hand-coded rule-based baseline ({@link FsmBrain}); loads no files. */
    FSM
}
