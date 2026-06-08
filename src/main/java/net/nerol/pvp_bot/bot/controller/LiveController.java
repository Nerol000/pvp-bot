package net.nerol.pvp_bot.bot.controller;

import java.util.Random;

/** Wraps a loaded Q-table and chooses an action each tick.
 *
 *  Two modes:
 *    - exploit=true: pure greedy, no learning. Use this for live MC play with a
 *      trained brain — it just runs the policy. (Online updates would need the
 *      reward signal, which we don't compute in MC yet.)
 *    - exploit=false: epsilon-greedy. Reserved for the "learn dynamically"
 *      phase; not used yet.
 *
 *  Action ordering must match simulator's Action enum exactly. The shared order is:
 *    0 SPRINT, 1 MOVE_FORWARD, 2 MOVE_BACK, 3 STRAFE_LEFT, 4 STRAFE_RIGHT,
 *    5 ATTACK, 6 TURN_LEFT_45, 7 TURN_RIGHT_45, 8 TURN_LEFT_90, 9 TURN_RIGHT_90,
 *    10 JUMP. If anyone reorders one side without the other, the Q-table indexes
 *    the wrong action. */
public final class LiveController {

    private final double[][] q;
    private final boolean exploit;
    private final double epsilon;
    private final Random rng = new Random();

    public LiveController(double[][] q, boolean exploit) {
        this(q, exploit, 0.05);
    }

    public LiveController(double[][] q, boolean exploit, double epsilon) {
        this.q = q;
        this.exploit = exploit;
        this.epsilon = epsilon;
    }

    public BotAction decide(BotState state) {
        int stateIdx = state.toIndex();
        int actionIdx;

        if (!exploit && rng.nextDouble() < epsilon) {
            actionIdx = rng.nextInt(BotAction.values().length);
        } else {
            actionIdx = argmax(stateIdx);
        }

        return BotAction.values()[actionIdx];
    }

    private int argmax(int stateIdx) {
        int best = 0;
        double bestQ = q[stateIdx][0];
        for (int a = 1; a < q[stateIdx].length; a++) {
            if (q[stateIdx][a] > bestQ) {
                bestQ = q[stateIdx][a];
                best = a;
            }
        }
        return best;
    }
}