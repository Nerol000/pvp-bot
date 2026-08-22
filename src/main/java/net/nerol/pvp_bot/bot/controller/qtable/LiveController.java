package net.nerol.pvp_bot.bot.controller.qtable;

import net.nerol.pvp_bot.bot.controller.BotAction;

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
 *  Returns a {@link BotAction}. The chosen column index is bounded by the loaded
 *  table's own action count, and {@code BotAction.values()} is the 15-action space in
 *  {@code environment.py} order, so a trained table maps 1:1 onto the enum. If a table
 *  ever has more columns than the enum has values, the index is clamped to the enum. */
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

    /** Chooses an action for the given state. */
    public BotAction decide(BotState state) {
        int stateIdx = state.toIndex();
        int actionIdx = (!exploit && rng.nextDouble() < epsilon)
                ? rng.nextInt(q[stateIdx].length)
                : argmax(stateIdx);
        int maxIdx = BotAction.values().length - 1;
        return BotAction.values()[Math.min(actionIdx, maxIdx)];
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