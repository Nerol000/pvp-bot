package net.nerol.pvp_bot.bot.controller;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.action.ActionPack;

/**
 * Hand-coded rule-based brain — a strong baseline to compare the learned (qtable / neural)
 * policies against. It aim-locks the target every tick ({@code lookAt}), sprints straight in, and
 * sprint-attacks when in range and charged.
 *
 * <p>Because the aim is exact and free, this is an upper-bound "oracle-aim" opponent, not an
 * apples-to-apples one: it's handed the very thing (precise aim) the learners must discover. To
 * make it a fair fight, replace {@code pack.lookAt(target)} with coarse turn steps so it aims with
 * the same tools the learners do. It is also deliberately predictable (always charges straight in),
 * which a well-trained policy can learn to exploit.
 */
public final class FsmBrain implements BotBrain {

    @Override
    public void act(BotPlayer bot, LivingEntity target, ActionPack pack) {
        // Aim: snap onto the target so movement is target-relative and attacks land.
        pack.lookAt(target);

        double dx = target.getX() - bot.getX();
        double dz = target.getZ() - bot.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double reach = bot.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getValue();

        // Per-tick movement reset, then sprint straight at the target.
        pack.setWalking(false);
        pack.setBackward(false);
        pack.setStrafeLeft(false);
        pack.setStrafeRight(false);
        pack.setSprinting(true);

        // In range and charged -> sprint-knockback hit (sprint persists from the approach).
        if (dist <= reach - 0.3 && bot.getAttackStrengthScale(0.5f) >= 0.9f) {
            pack.leftClick();
        }
    }
}
