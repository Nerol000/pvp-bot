package net.nerol.pvp_bot.bot.controller.kit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;   // VERIFY(26.2): pre-1.21.11 was entity.projectile.AbstractArrow
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.nerol.pvp_bot.bot.BotPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Kit observation (66 floats). {@link #encode} is a line-for-line port of
 * {@code kit_env.encode_kit_obs} -- keep the two in lock-step; {@code kit_obs_parity.py} writes a
 * fixture that {@link KitNeuralBrain} checks on load. {@link #gather} reads the live world into the
 * same raw quantities the simulator produces.
 *
 * <p>Yaws are radians in the simulator's atan2(z, x) frame. MC yaw Y maps to it as
 * atan2(cos Y, -sin Y) (the horizontal look vector is (-sin Y, cos Y)).
 */
public final class KitObservation {

    private static final String[] ITEM_NAMES = {"empty", "sword", "axe", "bow", "crossbow", "shield"};
    public static final String[] NAMES = buildNames();
    public static final int OBS_DIM = NAMES.length;

    /** Raw world quantities; field names == kit_env.RAW_KEYS so Gson can read the fixture. */
    @SuppressWarnings("unused")
    public static final class Raw {
        public double sdx, sdy, sdz, look_yaw, pitch_deg, on_ground, fall_dist, health, food,
                saturation, sprinting, main, off, charge, use_item, use_ticks, blocking,
                shield_cd_frac, xbow_loaded, arrows,
                rx, ry, rz, can_hit,
                odx, odz, o_look_yaw, o_health, o_sprinting, o_on_ground, o_main, o_off,
                o_use_item, o_use_ticks, o_blocking, o_shield_cd_frac, o_xbow_loaded,
                has_arrow, arx, ary, arz, avx, avy, avz;
    }

    private KitObservation() {}

    private static String[] buildNames() {
        List<String> n = new ArrayList<>();
        n.addAll(List.of("self_fwd_v", "self_strafe_v", "self_vy", "self_on_ground", "self_fall_dist",
                "self_pitch", "self_health", "self_food", "self_saturation", "self_can_sprint",
                "self_sprinting"));
        for (String s : ITEM_NAMES) n.add("self_main_" + s);
        for (String s : ITEM_NAMES) n.add("self_off_" + s);
        n.addAll(List.of("self_charge", "self_using", "self_blocking", "self_shield_disabled",
                "self_bow_draw", "self_xbow_load", "self_xbow_loaded", "self_arrows"));
        n.addAll(List.of("dist", "dy", "rel_yaw_sin", "rel_yaw_cos", "rel_pitch_sin", "rel_pitch_cos",
                "can_hit", "opp_facing_me", "opp_fwd_v", "opp_strafe_v"));
        n.addAll(List.of("opp_health", "opp_sprinting", "opp_on_ground"));
        for (String s : ITEM_NAMES) n.add("opp_main_" + s);
        for (String s : ITEM_NAMES) n.add("opp_off_" + s);
        n.addAll(List.of("opp_using", "opp_blocking", "opp_shield_disabled", "opp_bow_draw",
                "opp_xbow_loaded"));
        n.addAll(List.of("arrow_incoming", "arrow_dist", "arrow_bearing_sin", "arrow_bearing_cos",
                "arrow_closing"));
        return n.toArray(new String[0]);
    }

    // ------------------------------------------------------------------------- encode (pure)
    private static double clip(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static float[] encode(Raw r) {
        float[] out = new float[OBS_DIM];
        int[] k = {0};

        double ly = r.look_yaw;
        double c = Math.cos(ly), s = Math.sin(ly);
        double fwd = r.sdx * c + r.sdz * s;
        double strafe = -r.sdx * s + r.sdz * c;

        double rx = r.rx, ry = r.ry, rz = r.rz;
        double dist = Math.hypot(rx, rz);
        double relYaw = Math.atan2(rz, rx) - ly;
        double desiredPitch = -Math.toDegrees(Math.atan2(ry, Math.max(dist, 1e-6)));
        double relPitch = Math.toRadians(desiredPitch - r.pitch_deg);
        double facing = 0.0;
        if (dist > 1e-6) {
            double ol = r.o_look_yaw;
            facing = (Math.cos(ol) * -rx + Math.sin(ol) * -rz) / dist;
        }
        double oFwd = r.odx * c + r.odz * s;
        double oStrafe = -r.odx * s + r.odz * c;

        int ui = (int) r.use_item;
        double bowDraw = ui == KitItems.BOW ? Math.min(1.0, r.use_ticks / KitItems.BOW_FULL_DRAW) : 0.0;
        double xbowLoad = ui == KitItems.CROSSBOW ? Math.min(1.0, r.use_ticks / KitItems.CROSSBOW_LOAD_TICKS) : 0.0;
        int oui = (int) r.o_use_item;
        double oBowDraw = oui == KitItems.BOW ? Math.min(1.0, r.o_use_ticks / KitItems.BOW_FULL_DRAW) : 0.0;

        // self
        put(out, k, fwd);
        put(out, k, strafe);
        put(out, k, r.sdy);
        put(out, k, r.on_ground);
        put(out, k, Math.min(r.fall_dist / 4.0, 1.0));
        put(out, k, r.pitch_deg / 90.0);
        put(out, k, r.health / 20.0);
        put(out, k, r.food / 20.0);
        put(out, k, r.saturation / 20.0);
        put(out, k, r.food >= KitItems.SPRINT_MIN_FOOD ? 1.0 : 0.0);
        put(out, k, r.sprinting);
        onehot(out, k, (int) r.main);
        onehot(out, k, (int) r.off);
        put(out, k, r.charge);
        put(out, k, ui != KitItems.EMPTY ? 1.0 : 0.0);
        put(out, k, r.blocking);
        put(out, k, r.shield_cd_frac);
        put(out, k, bowDraw);
        put(out, k, xbowLoad);
        put(out, k, r.xbow_loaded);
        put(out, k, Math.min(r.arrows / 16.0, 1.0));
        // relative
        put(out, k, Math.min(dist / 10.0, 3.0));
        put(out, k, clip(ry, -5.0, 5.0));
        put(out, k, Math.sin(relYaw));
        put(out, k, Math.cos(relYaw));
        put(out, k, Math.sin(relPitch));
        put(out, k, Math.cos(relPitch));
        put(out, k, r.can_hit);
        put(out, k, facing);
        put(out, k, oFwd);
        put(out, k, oStrafe);
        // opponent
        put(out, k, r.o_health / 20.0);
        put(out, k, r.o_sprinting);
        put(out, k, r.o_on_ground);
        onehot(out, k, (int) r.o_main);
        onehot(out, k, (int) r.o_off);
        put(out, k, oui != KitItems.EMPTY ? 1.0 : 0.0);
        put(out, k, r.o_blocking);
        put(out, k, r.o_shield_cd_frac);
        put(out, k, oBowDraw);
        put(out, k, r.o_xbow_loaded);
        // nearest incoming arrow
        if (r.has_arrow > 0.5) {
            double ax = r.arx, ay = r.ary, az = r.arz;
            double ad = Math.sqrt(ax * ax + ay * ay + az * az);
            double ab = Math.atan2(az, ax) - ly;
            double closing = -(ax * r.avx + ay * r.avy + az * r.avz) / Math.max(ad, 1e-6);
            put(out, k, 1.0);
            put(out, k, Math.min(ad / KitItems.ARROW_SENSE_RANGE, 1.0));
            put(out, k, Math.sin(ab));
            put(out, k, Math.cos(ab));
            put(out, k, clip(closing / 3.0, -1.0, 1.0));
        } else {
            for (int i = 0; i < 5; i++) put(out, k, 0.0);
        }
        if (k[0] != OBS_DIM) {
            throw new IllegalStateException("kit obs length " + k[0] + " != " + OBS_DIM);
        }
        return out;
    }

    private static void put(float[] out, int[] k, double v) {
        out[k[0]++] = (float) v;
    }

    private static void onehot(float[] out, int[] k, int item) {
        for (int i = 0; i < KitItems.NUM_ITEMS; i++) out[k[0]++] = (i == item) ? 1f : 0f;
    }

    // ------------------------------------------------------------------------- gather (world)
    /** MC yaw (degrees) -> simulator atan2(z, x) bearing (radians). */
    static double simYaw(float mcYawDeg) {
        double y = Math.toRadians(mcYawDeg);
        return Math.atan2(Math.cos(y), -Math.sin(y));
    }

    private static int usingId(LivingEntity e) {
        return e.isUsingItem() ? KitItems.id(e.getUseItem()) : KitItems.EMPTY;
    }

    private static double shieldCooldown(LivingEntity e) {
        if (!(e instanceof Player p)) return 0.0;
        ItemStack shield = KitItems.id(p.getOffhandItem()) == KitItems.SHIELD ? p.getOffhandItem()
                : KitItems.id(p.getMainHandItem()) == KitItems.SHIELD ? p.getMainHandItem() : ItemStack.EMPTY;
        if (shield.isEmpty()) return 0.0;
        return p.getCooldowns().getCooldownPercent(shield, 0.0f);      // VERIFY(26.2) signature
    }

    /** Is a crossbow in the hotbar/offhand charged? (sim: one crossbow per kit). */
    private static boolean crossbowLoaded(Player p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack st = inv.getItem(i);
            if (st.is(Items.CROSSBOW) && CrossbowItem.isCharged(st)) return true;
        }
        ItemStack off = p.getOffhandItem();
        return off.is(Items.CROSSBOW) && CrossbowItem.isCharged(off);
    }

    /**
     * @param prevSelf   bot position at the previous decision (one tick ago); null on the first tick
     * @param prevTarget target position at the previous decision; null on the first tick
     */
    public static Raw gather(BotPlayer bot, LivingEntity target, Vec3 prevSelf, Vec3 prevTarget) {
        Raw r = new Raw();
        Vec3 bp = bot.position();
        Vec3 tp = target.position();
        Vec3 ps = prevSelf != null ? prevSelf : bp;
        Vec3 pt = prevTarget != null ? prevTarget : tp;

        r.sdx = bp.x - ps.x;
        r.sdy = bp.y - ps.y;
        r.sdz = bp.z - ps.z;
        r.look_yaw = simYaw(bot.getYRot());
        r.pitch_deg = bot.getXRot();
        r.on_ground = bot.onGround() ? 1 : 0;
        r.fall_dist = bot.fallDistance;
        r.health = bot.getHealth();
        r.food = bot.getFoodData().getFoodLevel();
        r.saturation = bot.getFoodData().getSaturationLevel();
        r.sprinting = bot.isSprinting() ? 1 : 0;
        r.main = KitItems.id(bot.getMainHandItem());
        r.off = KitItems.id(bot.getOffhandItem());
        r.charge = bot.getAttackStrengthScale(0.0f);     // sim `charge` = ticker / recharge
        r.use_item = usingId(bot);
        r.use_ticks = bot.isUsingItem() ? bot.getTicksUsingItem() : 0;
        r.blocking = bot.isBlocking() ? 1 : 0;
        r.shield_cd_frac = shieldCooldown(bot);
        r.xbow_loaded = crossbowLoaded(bot) ? 1 : 0;
        r.arrows = bot.getInventory().countItem(Items.ARROW);   // VERIFY(26.2)

        r.rx = tp.x - bp.x;
        r.ry = tp.y - bp.y;
        r.rz = tp.z - bp.z;
        Vec3 eye = bot.getEyePosition();
        Vec3 look = bot.getLookAngle();
        double reach = bot.getAttribute(Attributes.ENTITY_INTERACTION_RANGE).getValue();
        r.can_hit = target.getBoundingBox().inflate(target.getPickRadius())
                .clip(eye, eye.add(look.scale(reach))).isPresent() ? 1 : 0;

        r.odx = tp.x - pt.x;
        r.odz = tp.z - pt.z;
        r.o_look_yaw = simYaw(target.getYRot());
        r.o_health = target.getHealth();
        r.o_sprinting = target.isSprinting() ? 1 : 0;
        r.o_on_ground = target.onGround() ? 1 : 0;
        r.o_main = KitItems.id(target.getMainHandItem());
        r.o_off = KitItems.id(target.getOffhandItem());
        r.o_use_item = usingId(target);
        r.o_use_ticks = target.isUsingItem() ? target.getTicksUsingItem() : 0;
        r.o_blocking = target.isBlocking() ? 1 : 0;
        r.o_shield_cd_frac = shieldCooldown(target);
        boolean oXbowHeld = r.o_main == KitItems.CROSSBOW || r.o_off == KitItems.CROSSBOW;
        r.o_xbow_loaded = oXbowHeld && target instanceof Player tpl && crossbowLoaded(tpl) ? 1 : 0;

        AbstractArrow best = null;
        double bestD = KitItems.ARROW_SENSE_RANGE;
        for (AbstractArrow ar : bot.level().getEntitiesOfClass(AbstractArrow.class,
                bot.getBoundingBox().inflate(KitItems.ARROW_SENSE_RANGE), a -> a.getOwner() != bot)) {
            Vec3 d = ar.position().subtract(bp);
            Vec3 v = ar.getDeltaMovement();
            double len = d.length();
            if (len <= bestD && d.dot(v) < 0.0) {
                best = ar;
                bestD = len;
            }
        }
        if (best != null) {
            Vec3 d = best.position().subtract(bp);
            Vec3 v = best.getDeltaMovement();
            r.has_arrow = 1;
            r.arx = d.x; r.ary = d.y; r.arz = d.z;
            r.avx = v.x; r.avy = v.y; r.avz = v.z;
        }
        return r;
    }
}
