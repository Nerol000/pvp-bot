package net.nerol.pvp_bot.bot.controller.fsm;

/**
 * A behavior "genome" — the parameter vector that drives {@link ParameterizedFSMBrain}. This is
 * the Java mirror of the Python trainer's {@code ParameterizedFSM.DEFAULTS} knobs
 * (RL/PythonTrainer/core/opponents.py), so a behavior examined in-game matches the opponent the
 * learner was actually trained against.
 *
 * <p>Three presets correspond to the study's opponent arms:
 * <ul>
 *   <li>{@link #champion()} — the H2 <b>Champion</b> arm: a clean, win-seeking s-tapper (a single
 *       fixed genome in the trainer, so it ports exactly).</li>
 *   <li>{@link #tdMax()} — the H1 <b>TD-Error</b> / <b>Teacher</b> arm: the "hard to predict",
 *       surprise-maximizing hand genome. In training this arm is <i>adaptive</i> (a bandit over a
 *       genome population); this preset is its canonical static representative so you can watch its
 *       characteristic engaged-but-noisy style.</li>
 *   <li>{@link #improve()} — the H2-proper <b>Improve</b> arm (optimizes measured learner
 *       improvement). Also adaptive in training; represented here by the {@code punish} anchor
 *       genome its population is seeded with — a strong bait-and-punish counter — so its behavior
 *       is examinable in-game.</li>
 * </ul>
 * Because {@code tdMax} and {@code improve} are evolving populations during training, these two are
 * <i>representative</i> genomes, not a single canonical one; {@code champion} is exact.
 */
public final class Genome {

    /** Combat distance (blocks) it tries to hold. */
    public final double preferredDistance;
    /** Tolerance around {@link #preferredDistance} before it approaches / retreats. */
    public final double band;
    /** Chance to swing when a hit would connect and it is aimed (and charged if {@link #waitForCharge}). */
    public final double attackProb;
    /** Chance to back off when closer than preferred. */
    public final double retreatProb;
    /** Chance to start a strafe run when in-band. */
    public final double strafeProb;
    /** Mean strafe-run length (ticks); each run's length is drawn from a Gaussian around this. */
    public final int strafeTicks;
    /** Chance to jump on any tick (movement variety / knockback dodge). */
    public final double jumpProb;
    /** Chance to begin an idle pause when in-band. */
    public final double pauseProb;
    /** Length (ticks) of an idle pause. */
    public final int pauseTicks;
    /** Aim error (radians) above which it re-aims instead of acting. */
    public final double aimTol;
    /** Only swing when fully charged (correct sprint-knockback timing). */
    public final boolean waitForCharge;
    /** Post-hit backward tap length (the s-tap reset); 0 disables it. */
    public final int stapTicks;
    /** When in reach but recharging, retreat out of reach to charge safely (anti-turtle bait). */
    public final boolean bait;
    /** Self-calibrating anti-combo defense: jump onto a predicted, consistently-timed incoming hit. */
    public final boolean jumpReset;
    /** Human-readable name shown for this behavior. */
    public final String label;

    public Genome(String label, double preferredDistance, double band, double attackProb,
                  double retreatProb, double strafeProb, int strafeTicks, double jumpProb,
                  double pauseProb, int pauseTicks, double aimTol, boolean waitForCharge,
                  int stapTicks, boolean bait, boolean jumpReset) {
        this.label = label;
        this.preferredDistance = preferredDistance;
        this.band = band;
        this.attackProb = attackProb;
        this.retreatProb = retreatProb;
        this.strafeProb = strafeProb;
        this.strafeTicks = strafeTicks;
        this.jumpProb = jumpProb;
        this.pauseProb = pauseProb;
        this.pauseTicks = pauseTicks;
        this.aimTol = aimTol;
        this.waitForCharge = waitForCharge;
        this.stapTicks = stapTicks;
        this.bait = bait;
        this.jumpReset = jumpReset;
    }

    /**
     * H2 Champion arm — optimize for WINNING: a clean s-tapper. Sprints in, waits for a full
     * charge, lands a full-strength sprint-knockback hit, then s-taps (brief backpedal) to reset
     * sprint and re-engage. Low behavioral variance. Exact port of the trainer's
     * {@code ParameterizedFSM.champion()}.
     */
    public static Genome champion() {
        return new Genome("champion",
                /* preferredDistance */ 2.3, /* band */ 0.6, /* attackProb */ 0.95,
                /* retreatProb */ 0.05, /* strafeProb */ 0.06, /* strafeTicks */ 4,
                /* jumpProb */ 0.02, /* pauseProb */ 0.0, /* pauseTicks */ 0, /* aimTol */ 0.25,
                /* waitForCharge */ true, /* stapTicks */ 1, /* bait */ false, /* jumpReset */ true);
    }

    /**
     * H1 TD-Error / Teacher arm (static representative) — optimize for TEACHING by maximizing the
     * learner's surprise: stay engaged (frequent damage exchanges) but UNPREDICTABLE (high variance
     * in attack timing, strafe, and pauses) and allow counter-openings (moderate retreat). Port of
     * the trainer's {@code ParameterizedFSM.teacher()} preset. (In training the arm is an adaptive
     * bandit over many such genomes; this is its canonical hand genome.)
     */
    public static Genome tdMax() {
        return new Genome("td-max",
                /* preferredDistance */ 2.5, /* band */ 0.5, /* attackProb */ 0.6,
                /* retreatProb */ 0.35, /* strafeProb */ 0.45, /* strafeTicks */ 4,
                /* jumpProb */ 0.08, /* pauseProb */ 0.12, /* pauseTicks */ 4, /* aimTol */ 0.25,
                /* waitForCharge */ false, /* stapTicks */ 0, /* bait */ false, /* jumpReset */ false);
    }

    /**
     * H2-proper Improve arm (representative) — rewards the learner's measured improvement. Adaptive
     * in training; represented in-game by the {@code punish} anchor its population is seeded with: a
     * bait-and-punish counter that charges safely outside a turtle's reach, then lunges for a
     * full-charge sprint-knockback hit. Port of the trainer's {@code AdaptiveTeacher.SEED_MODES}
     * "punish" genome.
     */
    public static Genome improve() {
        return new Genome("improve",
                /* preferredDistance */ 3.2, /* band */ 0.35, /* attackProb */ 0.95,
                /* retreatProb */ 0.6, /* strafeProb */ 0.3, /* strafeTicks */ 4,
                /* jumpProb */ 0.06, /* pauseProb */ 0.1, /* pauseTicks */ 3, /* aimTol */ 0.25,
                /* waitForCharge */ true, /* stapTicks */ 2, /* bait */ true, /* jumpReset */ true);
    }
}
