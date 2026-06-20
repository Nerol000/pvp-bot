package net.nerol.pvp_bot.bot.controller;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.nerol.pvp_bot.bot.BotPlayer;

/**
 * Builds the 13-float observation for {@link NeuralBrain}, mirroring
 * {@code RL/PythonTrainer/environment.py._observe()} field-for-field.
 *
 * <p>Conventions are kept independent of the sim-vs-MC yaw difference: relative yaw is derived from
 * the actual look vector (not raw yaw), and velocity is expressed in facing-relative
 * (forward / strafe) components. Eye heights cancel in the pitch term, so it uses feet-y deltas.
 */
public final class NeuralObservation {
    public static final int OBS_DIM = 13;

    private NeuralObservation() {}

    public static float[] observe(BotPlayer bot, LivingEntity target) {
        Vec3 bp = bot.position();
        Vec3 tp = target.position();
        double dx = tp.x - bp.x;
        double dz = tp.z - bp.z;
        double dy = tp.y - bp.y;
        double dist = Math.sqrt(dx * dx + dz * dz);

        Vec3 look = bot.getLookAngle();
        double lookYaw = Math.atan2(look.z, look.x);            // forward bearing in atan2(z,x) frame
        double relYaw = Math.atan2(dz, dx) - lookYaw;           // target bearing relative to facing

        double desiredPitch = -Math.toDegrees(Math.atan2(dy, Math.max(dist, 1e-6)));
        double relPitch = Math.toRadians(desiredPitch - bot.getXRot());

        // Facing-relative velocity (forward / strafe), matching environment.py.
        Vec3 v = bot.getDeltaMovement();
        double fwd = v.x * Math.cos(lookYaw) + v.z * Math.sin(lookYaw);
        double strafe = -v.x * Math.sin(lookYaw) + v.z * Math.cos(lookYaw);

        // can-hit: would the eye->look ray clip the target's hitbox within reach (same as leftClick)?
        Vec3 eye = bot.getEyePosition();
        double reach = bot.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getValue();
        Vec3 end = eye.add(look.scale(reach));
        boolean canHit = target.getBoundingBox().inflate(target.getPickRadius()).clip(eye, end).isPresent();

        return new float[] {
                (float) Math.min(dist / 10.0, 2.0),
                (float) dy,
                (float) Math.sin(relYaw), (float) Math.cos(relYaw),
                (float) Math.sin(relPitch), (float) Math.cos(relPitch),
                (float) fwd, (float) strafe,
                bot.getAttackStrengthScale(0.5f),
                bot.getHealth() / 20.0f,
                target.getHealth() / 20.0f,
                bot.onGround() ? 1.0f : 0.0f,
                canHit ? 1.0f : 0.0f,
        };
    }
}
