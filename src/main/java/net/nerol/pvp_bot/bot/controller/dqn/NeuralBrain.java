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
        BotAction act = BotAction.values()[a];
        pack.executeBotAction(act);
        if (act == BotAction.ATTACK) {
            // MC resets the charge on every swing (client side); the server only does it on a hit.
            // Without this a missed swing keeps full charge, so the bot can swing again next tick.
            bot.resetAttackStrengthTicker();
        }
    }
}