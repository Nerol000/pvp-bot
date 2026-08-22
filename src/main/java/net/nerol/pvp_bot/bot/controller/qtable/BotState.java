package net.nerol.pvp_bot.bot.controller.qtable;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.nerol.pvp_bot.bot.BotPlayer;

/** Mirror of the simulator's {@code State} class. Index layout must stay in lock-step
 *  with simulator/State.java and environment.py so the Q-table the bot loads keys correctly.
 *  The table is {@code numStates = 48}: charged (0..1) x distance (0..2) x direction (0..7).
 *  The charged bit lets the policy see whether its next swing is at full strength, so it can
 *  learn to wait out the attack cooldown instead of spam-swinging. */
public final class BotState {

    public final int charged;        // 0 = recharging, 1 = attack fully charged
    public final int distance;       // 0=NEAR (<1.66666), 1=MID (<=3), 2=FAR
    public final int direction;      // 0..7, 0 = FRONT (target dead ahead)

    public BotState(int charged, int distance, int direction) {
        this.charged = charged;
        this.distance = distance;
        this.direction = direction;
    }

    /** Matches environment.py state_index(): charged*24 + distance*8 + direction (0..47). */
    public int toIndex() {
        return charged * 24 + distance * 8 + direction;
    }

    /** Build the state from a live Minecraft observation. The relative-bearing math
     *  matches the simulator's {@code computeDirectionBucket} — both use
     *  (bearing_to_target − own_yaw) bucketed into 8 sectors of 45 degrees centered
     *  on 0 (FRONT). Yaw conventions in MC and the simulator differ in absolute
     *  values, but the {@code -90} below folds MC's convention (yaw 0 = +z) into the
     *  simulator's (forward = (cos yaw, sin yaw)), so the relative angle — and thus the
     *  bucket — is identical for the same physical geometry. */
    public static BotState observe(BotPlayer bot, LivingEntity target) {
        Vec3 b = bot.position();
        Vec3 t = target.position();
        double dx = t.x - b.x;
        double dy = t.y - b.y;
        double dz = t.z - b.z;

        // Distance bucket — full 3D distance with the same thresholds the simulator's
        // computeDistanceBucket now uses (it includes the vertical gap so a jump/crit
        // separation registers).
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int distanceBucket = (dist < 1.66666) ? 0 : (dist <= 3.0) ? 1 : 2;

        // Bearing to target expressed in MC's yaw convention (yaw 0 = +z).
        // atan2(dz,dx) is math-convention (0 = +x), so subtract 90 to align.
        double bearingToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double relative = ((bearingToTarget - bot.getYRot()) % 360.0 + 360.0 + 22.5) % 360.0;
        int directionBucket = (int)(relative / 45.0);

        // Charged bit — mirrors environment.py (charge >= FULL_STRENGTH). The attack-strength
        // scale reaches 1.0 only when the cooldown is fully recovered, so a swing here lands at
        // full damage (and can crit / apply sprint knockback). partialTick 0 = current tick.
        int chargedBucket = (bot.getAttackStrengthScale(0.0f) >= 1.0f) ? 1 : 0;

        return new BotState(chargedBucket, distanceBucket, directionBucket);
    }
}