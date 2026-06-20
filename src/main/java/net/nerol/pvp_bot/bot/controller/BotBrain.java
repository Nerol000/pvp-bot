package net.nerol.pvp_bot.bot.controller;

import net.minecraft.world.entity.LivingEntity;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.action.ActionPack;

/**
 * A decision-making brain for a {@link BotPlayer}: each tick it observes the world and drives the
 * bot's {@link ActionPack}. Implementations ({@link QTableBrain}, {@link NeuralBrain}) are
 * interchangeable so the bot doesn't care which policy is running.
 */
public interface BotBrain {
    void act(BotPlayer bot, LivingEntity target, ActionPack pack);
}
