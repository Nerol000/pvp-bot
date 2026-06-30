package net.nerol.pvp_bot.bot.controller;

/** Selects how BotPlayer decides actions each tick. */
public enum BotMode {
    /** Replays recorded actions from bot_replay.csv. Useful for debugging. */
    PLAYBACK,
    /** Uses the trained Q-table (qtable.csv) to decide actions in real time. */
    LIVE,

    FSM
}