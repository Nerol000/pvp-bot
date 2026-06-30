package net.nerol.pvp_bot.bot.controller.fsm;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.action.ActionPack;
import net.nerol.pvp_bot.bot.controller.BotBrain;

import java.util.Random;

/**
 * Hand-coded combo brain — a strong baseline for comparing the learned (qtable / neural) policies.
 *
 * <ul>
 *   <li>Aim-locks the target every tick ({@code lookAt}).</li>
 *   <li>Sprint-chases, and once close, <b>A-D strafes</b> (alternating left/right) to juke.</li>
 *   <li>Sprint-attacks when in range and charged.</li>
 *   <li><b>S-taps</b> after each landed hit — a brief backward tap to re-space and re-time the next
 *       sprint-knockback hit, keeping the target pinned in a knockback loop (same timing as the
 *       simulator's bot2).</li>
 * </ul>
 *
 * No jump-crits, by request. Aim is exact/free via {@code lookAt}, so this is an oracle-aim
 * opponent; swap {@code lookAt(target)} for coarse turn steps to make it a fair aiming fight. State
 * (s-tap / strafe timers) is per-bot since each bot gets its own brain instance.


 */
public final class FSMBrain implements BotBrain {

    private static final double S_TAP_MEAN = 3.0;
    private static final double S_TAP_STDDEV = 0.75;
    private static final int S_TAP_MAX = 5;
    private static final double STRAFE_RANGE = 5.0;   // start juking once within this many blocks

    private final Random random = new Random();
    private int sTapTicks = 0;
    private int strafeTicks = 0;
    private boolean strafeRight = false;

    @Override
    public void act(BotPlayer bot, LivingEntity target, ActionPack pack) {

        pack.lookAt(target);

        double dx = target.getX() - bot.getX();
        double dz = target.getZ() - bot.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double reach = bot.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getValue();

        // Per-tick movement reset.
        pack.setWalking(false);
        pack.setBackward(false);
        pack.setStrafeLeft(false);
        pack.setStrafeRight(false);
        pack.setSprinting(false);

        // S-tap: just landed a hit -> tap backward briefly to re-space before sprinting back in.
        if (sTapTicks > 0) {
            sTapTicks--;
            pack.setBackward(true);
            return;
        }

        // Chase: sprint in. Far away -> straight (close fast); close -> A-D strafe (juke).
        pack.setSprinting(true);
        if (dist < STRAFE_RANGE) {
            if (--strafeTicks <= 0) {
                strafeRight = !strafeRight;
                strafeTicks = 3 + random.nextInt(4);   // flip strafe every ~3-6 ticks
            }
            if (strafeRight) {
                pack.setStrafeRight(true);
            } else {
                pack.setStrafeLeft(true);
            }
        }

        // Attack when in range and charged. A landed (sprint) hit consumes the cooldown — that drop
        // tells us the hit connected, which kicks off the s-tap.
        if (dist <= reach - 0.3 && bot.getAttackStrengthScale(0.5f) >= 0.9f) {
            float before = bot.getAttackStrengthScale(0.5f);
            pack.attack();
            if (bot.getAttackStrengthScale(0.5f) < before - 0.5f) {
                double sampled = S_TAP_MEAN + random.nextGaussian() * S_TAP_STDDEV;
                sTapTicks = (int) Math.round(Math.max(1.0, Math.min(S_TAP_MAX, sampled)));
            }
        }
    }
}