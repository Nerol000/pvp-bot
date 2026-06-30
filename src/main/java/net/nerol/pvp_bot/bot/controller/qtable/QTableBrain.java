package net.nerol.pvp_bot.bot.controller.qtable;

import net.minecraft.world.entity.LivingEntity;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.action.ActionPack;
import net.nerol.pvp_bot.bot.controller.BotBrain;

import java.io.IOException;

/**
 * Tabular Q-table brain — the original behavior. Loads {@code qtable.csv} via
 * {@link QTableLoader} and runs the greedy policy through {@link LiveController}.
 */
public final class QTableBrain implements BotBrain {
    private final LiveController controller;

    public QTableBrain() throws IOException {
        double[][] q = QTableLoader.load(QTableLoader.DEFAULT_RESOURCE);
        this.controller = new LiveController(q, /*exploit=*/ true);
    }

    @Override
    public void act(BotPlayer bot, LivingEntity target, ActionPack pack) {
        BotState state = BotState.observe(bot, target);
        pack.executeBotAction(controller.decide(state));
    }
}
