package net.nerol.pvp_bot.bot.controller;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.nerol.pvp_bot.bot.BotPlayer;

/** Mirror of the simulator's {@code State} class. Index layout must stay in lock-step
 *  with simulator/State.java so the Q-table the bot loads keys correctly. */
public final class BotState {

    public final int distance;       // 0=NEAR (<5), 1=MID (<10), 2=FAR
    public final int direction;      // 0..7, 0 = FRONT (target dead ahead)

    public BotState(int distance, int direction) {
        this.distance = distance;
        this.direction = direction;
    }

    /** Matches simulator State.toIndex(): distance*8 + direction (0..23). */
    public int toIndex() {
        return distance * 8 + direction;
    }

    /** Build the state from a live Minecraft observation. The relative-bearing math
     *  matches the simulator's {@code computeDirectionBucket} — both use
     *  (bearing_to_target − own_yaw) bucketed into 8 sectors of 45 degrees centered
     *  on 0 (FRONT). Yaw conventions in MC and the simulator differ in absolute
     *  values but the relative angle is convention-independent. */
    public static BotState observe(BotPlayer bot, LivingEntity target) {
        Vec3 b = bot.position();
        Vec3 t = target.position();
        double dx = t.x - b.x;
        double dz = t.z - b.z;

        // Distance bucket — same thresholds as simulator.
        double dist = Math.sqrt(dx * dx + dz * dz);
        int distanceBucket = (dist < 5.0) ? 0 : (dist < 10.0) ? 1 : 2;

        // Bearing to target expressed in MC's yaw convention (yaw 0 = +z).
        // atan2(dz,dx) is math-convention (0 = +x), so subtract 90 to align.
        double bearingToTarget = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double relative = ((bearingToTarget - bot.getYRot()) % 360.0 + 360.0 + 22.5) % 360.0;
        int directionBucket = (int)(relative / 45.0);

        return new BotState(distanceBucket, directionBucket);
    }
}