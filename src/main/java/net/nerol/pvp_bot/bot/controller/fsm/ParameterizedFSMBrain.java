package net.nerol.pvp_bot.bot.controller.fsm;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.action.ActionPack;
import net.nerol.pvp_bot.bot.controller.BotBrain;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Genome-parameterized fighter — the in-game port of the Python trainer's {@code ParameterizedFSM}
 * (RL/PythonTrainer/core/opponents.py). Running it in the mod lets you WATCH the exact opponent
 * behaviors the learner trained against ({@code champion} / {@code td-max} / {@code improve}); which
 * behavior it runs is set by the {@link Genome} passed to the constructor.
 *
 * <p>Decision order each tick mirrors the trainer 1:1: self-calibrating jump-reset (anti-combo),
 * aim gate, post-hit s-tap, honor an in-progress pause, active strafe run (which may swing
 * mid-run), reach-edge punish (with anti-turtle bait), occasional jump, then spacing control and
 * in-band variety (start a strafe run / pause / approach).
 *
 * <p>Adapted to live conventions like {@link FSMBrain}: it aims every tick via {@code lookAt}, uses
 * the bot's reach attribute for the swing gate, and reads charge from
 * {@code getAttackStrengthScale}. State (timers, hit-cadence tracking) is per-bot since each bot
 * gets its own brain instance.
 */
public final class ParameterizedFSMBrain implements BotBrain {

    private static final double STRAFE_MIN_SPACING = 3.0;   // keep >= this spacing while strafing
    private static final float CHARGE_READY = 0.9f;         // "fully charged" threshold (matches FSMBrain)

    private final Genome g;
    private final Random random = new Random();

    // Per-tick / per-episode state (mirrors the Python FSM instance fields).
    private int pause = 0;
    private int stap = 0;
    private int strafe = 0;
    private boolean strafeRight = false;
    private int ticks = 0;

    // Jump-reset (anti-combo) cadence tracking.
    private Double lastHealth = null;
    private final Deque<Integer> hitTicks = new ArrayDeque<>();   // last few inferred hit ticks
    private int detectedPeriod = 0;

    public ParameterizedFSMBrain(Genome genome) {
        this.g = genome;
    }

    /** Name of the behavior this brain runs (e.g. "champion"). */
    public String behaviorLabel() {
        return g.label;
    }

    @Override
    public void act(BotPlayer bot, LivingEntity target, ActionPack pack) {
        ticks++;

        double dx = target.getX() - bot.getX();
        double dz = target.getZ() - bot.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        double reach = bot.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getValue();
        float charge = bot.getAttackStrengthScale(0.5f);

        // Per-tick movement reset; aim at the target every tick (a swing only lands if aimed).
        pack.setWalking(false);
        pack.setBackward(false);
        pack.setStrafeLeft(false);
        pack.setStrafeRight(false);
        pack.setSprinting(false);
        pack.lookAt(target);

        // 0) Jump-reset (anti-combo DEFENSE), self-calibrating. A jump timed onto an incoming hit
        //    blunts the knockback combo. The FSM can't read the hit directly, so it INFERS each hit
        //    from its own health dropping and MEASURES the opponent's cadence: after 3+ hits at a
        //    consistent interval it locks the period and jumps on the predicted hit ticks; if the
        //    timing varies the lock drops. Runs first so hit tracking never misses a tick.
        if (g.jumpReset) {
            double health = bot.getHealth();
            if (lastHealth != null && health < lastHealth - 1e-9) {
                hitTicks.addLast(ticks - 1);              // the hit actually landed last tick
                while (hitTicks.size() > 6) hitTicks.removeFirst();
                if (hitTicks.size() >= 3) {
                    Integer[] ht = hitTicks.toArray(new Integer[0]);
                    int i1 = ht[ht.length - 2] - ht[ht.length - 3];
                    int i2 = ht[ht.length - 1] - ht[ht.length - 2];
                    detectedPeriod = (i2 > 0 && Math.abs(i1 - i2) <= 1) ? Math.round((i1 + i2) / 2.0f) : 0;
                }
            }
            lastHealth = health;
            if (detectedPeriod > 0 && !hitTicks.isEmpty()) {
                int since = ticks - hitTicks.peekLast();
                if (since > 2 * detectedPeriod) {
                    detectedPeriod = 0;                   // combo stopped -> drop the lock
                } else if (bot.onGround() && since > 0 && since % detectedPeriod == 0) {
                    pack.jump();                          // jump onto the predicted hit tick
                    return;
                }
            }
        }

        // 1) Aim first: if mis-aimed, spend this tick only turning (lookAt already issued above).
        if (aimError(bot, dx, dz) > g.aimTol) {
            return;
        }

        // 2) Post-hit s-tap: a brief backward tap resets sprint so the NEXT full-charge hit re-
        //    applies the sprint-knockback bonus (correct s-tap rhythm). Scheduled when it swings.
        if (stap > 0) {
            stap--;
            pack.setBackward(true);
            return;
        }

        // 3) Honor an in-progress pause.
        if (pause > 0) {
            pause--;
            return;   // IDLE (all movement already cleared)
        }

        boolean canHit = dist <= reach - 0.3;                        // swing would connect (live gate)
        boolean ready = !g.waitForCharge || charge >= CHARGE_READY;  // charged enough to swing?

        // 3a) Active strafe run: a committed sustained A/D tap. Sprint-strafe so a mid-run full-
        //     charge swing still lands the sprint-knockback. It can swing mid-run (same path as the
        //     main punish, scheduling the s-tap); otherwise keeps >= 3 block spacing.
        if (strafe > 0) {
            strafe--;
            if (canHit && ready && random.nextDouble() < g.attackProb) {
                if (g.stapTicks > 0) stap = g.stapTicks;
                pack.attack();
                return;
            }
            if (dist < STRAFE_MIN_SPACING) {
                pack.setBackward(true);
                return;
            }
            strafeStep(pack);
            return;
        }

        // 4) Punish at the REACH edge FIRST: if a hit can land now (in reach, charged when required,
        //    already aimed), swing before any spacing/jump decision. This lands the full-charge
        //    sprint-knockback punish that beats a stationary spam-clicker ("turtle").
        if (canHit && ready && random.nextDouble() < g.attackProb) {
            if (g.stapTicks > 0) stap = g.stapTicks;
            pack.attack();
            return;
        }
        if (canHit && !ready) {
            if (g.bait) {
                // Recharging inside the turtle's reach is a losing trade -> back out to charge
                // safely, then lunge in for the punish next cycle.
                pack.setBackward(true);
                return;
            }
            return;   // hold with IDLE (preserves the spot) until fully charged
        }

        // 5) Occasional jump (movement variety / knockback dodging).
        if (random.nextDouble() < g.jumpProb) {
            pack.jump();
            return;
        }

        // 6) Spacing control (out of reach): approach, or retreat if closer than preferred.
        if (dist > g.preferredDistance + g.band) {
            pack.setSprinting(true);
            return;
        }
        if (dist < g.preferredDistance - g.band && random.nextDouble() < g.retreatProb) {
            pack.setBackward(true);
            return;
        }

        // 7) In-band variety: START a strafe run (50/50 left/right, Gaussian length) or a brief
        //    pause -- the unpredictable spacing the teacher wants. Otherwise approach / hold.
        if (random.nextDouble() < g.strafeProb) {
            strafeRight = random.nextDouble() < 0.5;
            double sigma = Math.max(1.0, g.strafeTicks / 3.0);
            strafe = Math.max(1, (int) Math.round(g.strafeTicks + random.nextGaussian() * sigma));
            strafe -= 1;   // this tick is the run's first step
            if (dist < STRAFE_MIN_SPACING) {
                pack.setBackward(true);
            } else {
                strafeStep(pack);
            }
            return;
        }
        if (g.pauseProb > 0.0 && random.nextDouble() < g.pauseProb) {
            pause = g.pauseTicks;
            return;   // IDLE
        }

        if (dist > reach) {
            pack.setSprinting(true);
        }   // else IDLE (in reach, holding)
    }

    /** Sprint-strafe one step in the current run direction. */
    private void strafeStep(ActionPack pack) {
        pack.setSprinting(true);
        if (strafeRight) {
            pack.setStrafeRight(true);
        } else {
            pack.setStrafeLeft(true);
        }
    }

    /** Absolute yaw error (radians) between the bot's facing and the direction to the target. */
    private static double aimError(BotPlayer bot, double dx, double dz) {
        double desiredYaw = Math.toDegrees(Math.atan2(-dx, dz));   // MC yaw convention
        double errDeg = Math.abs(Mth.wrapDegrees(desiredYaw - bot.getYRot()));
        return Math.toRadians(errDeg);
    }
}