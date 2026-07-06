package net.nerol.pvp_bot.bot.controller.dqn;

import net.minecraft.world.entity.LivingEntity;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.action.ActionPack;
import net.nerol.pvp_bot.bot.controller.BotAction;
import net.nerol.pvp_bot.bot.controller.BotBrain;

/**
 * Neural-network brain: builds the observation, runs {@link NeuralPolicy}, and executes the chosen
 * action via {@link ActionPack#executeBotAction} (the 15-action {@link BotAction} space matching
 * {@code environment.py}).
 */
public final class NeuralBrain implements BotBrain {
    private final NeuralPolicy policy;

    public NeuralBrain() {
        this.policy = NeuralPolicy.load(NeuralPolicy.DEFAULT_RESOURCE);
    }

    @Override
    public void act(BotPlayer bot, LivingEntity target, ActionPack pack) {
        int a = policy.act(NeuralObservation.observe(bot, target));
        pack.executeBotAction(BotAction.values()[a]);
    }
}