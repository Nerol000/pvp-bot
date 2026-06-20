package net.nerol.pvp_bot.bot.controller;

import net.minecraft.world.entity.LivingEntity;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.action.ActionPack;

/**
 * Neural-network brain: builds the observation, runs {@link NeuralPolicy}, and maps the chosen
 * action onto the {@link ActionPack}. The 14 actions match {@code environment.py} (full 360 yaw +
 * pitch via small turn deltas).
 */
public final class NeuralBrain implements BotBrain {
    private final NeuralPolicy policy;

    public NeuralBrain() {
        this.policy = NeuralPolicy.load(NeuralPolicy.DEFAULT_RESOURCE);
    }

    @Override
    public void act(BotPlayer bot, LivingEntity target, ActionPack pack) {
        applyAction(pack, policy.act(NeuralObservation.observe(bot, target)));
    }

    /**
     * Maps the discrete action index to ActionPack. Movement flags are reset every tick so motion
     * is applied per-tick, matching the simulator's per-tick impulse model.
     *
     * <p>NOTE: this clears the sprint flag each tick, so a SPRINT_FORWARD immediately followed by
     * ATTACK won't carry the sprint flag into the swing (no +0.5 sprint-knockback). That's the
     * most likely thing to tune if the neural bot's knockback feels weak in-game.
     */
    private void applyAction(ActionPack pack, int a) {
        pack.setWalking(false);
        pack.setBackward(false);
        pack.setStrafeLeft(false);
        pack.setStrafeRight(false);
        pack.setSprinting(false);
        switch (a) {
            case 1 -> pack.setWalking(true);        // FORWARD
            case 2 -> pack.setSprinting(true);      // SPRINT_FORWARD
            case 3 -> pack.setBackward(true);       // BACK
            case 4 -> pack.setStrafeLeft(true);     // STRAFE_LEFT
            case 5 -> pack.setStrafeRight(true);    // STRAFE_RIGHT
            case 6 -> pack.leftClick();             // ATTACK
            case 7 -> pack.jump();                  // JUMP
            case 8 -> pack.turn(-15f, 0f);          // YAW_L
            case 9 -> pack.turn(15f, 0f);           // YAW_R
            case 10 -> pack.turn(-4f, 0f);          // YAW_L_FINE
            case 11 -> pack.turn(4f, 0f);           // YAW_R_FINE
            case 12 -> pack.turn(0f, -8f);          // PITCH_UP
            case 13 -> pack.turn(0f, 8f);           // PITCH_DOWN
            default -> { }                          // 0 = IDLE
        }
    }
}
