package net.nerol.pvp_bot.bot.controller.kit;

import net.minecraft.world.entity.player.Inventory;
import net.nerol.pvp_bot.bot.BotPlayer;
import net.nerol.pvp_bot.bot.action.ActionPack;

/**
 * Scripted "patient" shooter layered over the neural melee policy (port of
 * {@code train_ranged.ScriptedShooter(patient=True)} + the hysteresis switch in {@code ranged_force.py}).
 * While the opponent is at least {@link #FAR} blocks away (and stays ranged until it is closer than
 * {@link #NEAR}) and the bot has ammo, this overrides the policy's ITEM and CLICK branches; the
 * policy keeps MOVE and JUMP. Prefers a loaded crossbow, else the bow; holds the full draw (or the
 * loaded crossbow) while the opponent's shield is up, and releases when it drops. On leaving ranged
 * mode it releases right-click and pulls the last melee weapon back out.
 * Sim (br5 s4, far starts, 10 opponents): 97% win vs 87% for the policy alone.
 */
final class KitRangedScript {
    static final double FAR = 8.0;
    static final double NEAR = 4.0;

    private boolean on;
    private KitAction lastMelee = KitAction.HOLD_AXE;

    /** The {item, click} actions to run this tick, or null to let the policy decide both. */
    KitAction[] decide(BotPlayer bot, ActionPack pack, KitObservation.Raw r) {
        int main = (int) r.main;
        if (main == KitItems.AXE) lastMelee = KitAction.HOLD_AXE;
        else if (main == KitItems.SWORD) lastMelee = KitAction.HOLD_SWORD;

        Inventory inv = bot.getInventory();
        boolean xbowLoaded = r.xbow_loaded > 0.5;
        boolean hasBow = KitItems.hotbarSlotOf(inv, KitItems.BOW) >= 0;
        boolean hasXbow = KitItems.hotbarSlotOf(inv, KitItems.CROSSBOW) >= 0;
        boolean ammo = (hasXbow && xbowLoaded) || (r.arrows > 0 && (hasBow || hasXbow));
        double dist = Math.hypot(r.rx, r.rz);
        boolean far = dist >= FAR || (on && dist >= NEAR);

        if (!far || !ammo) {
            if (!on) return null;
            on = false;                                       // hand back to melee
            boolean using = pack.isUsing() || r.use_item != KitItems.EMPTY;
            return new KitAction[]{lastMelee, using ? KitAction.USE_STOP : null};
        }
        on = true;

        boolean wait = r.o_use_item == KitItems.SHIELD && r.o_shield_cd_frac <= 0.0;
        int using = (int) r.use_item;
        if (using == KitItems.SHIELD) {                       // own shield up: lower it first
            return new KitAction[]{KitAction.IDLE, KitAction.USE_STOP};
        }
        if (using == KitItems.BOW) {
            boolean release = r.use_ticks >= KitItems.BOW_FULL_DRAW && !wait;
            return new KitAction[]{KitAction.IDLE, release ? KitAction.USE_STOP : KitAction.IDLE};
        }
        if (using == KitItems.CROSSBOW) {
            boolean loaded = r.use_ticks >= KitItems.CROSSBOW_LOAD_TICKS;
            return new KitAction[]{KitAction.IDLE, loaded ? KitAction.USE_STOP : KitAction.IDLE};
        }
        if (pack.isUsing()) {                                 // just fired / sticky right-click: reset
            return new KitAction[]{KitAction.IDLE, KitAction.USE_STOP};
        }
        boolean useXbow = hasXbow && (xbowLoaded || !hasBow);
        int want = useXbow ? KitItems.CROSSBOW : KitItems.BOW;
        KitAction item = main == want ? KitAction.IDLE
                : useXbow ? KitAction.HOLD_CROSSBOW : KitAction.HOLD_BOW;
        // a loaded crossbow fires on use: keep it loaded while the shield is up
        KitAction click = useXbow && xbowLoaded && wait ? KitAction.IDLE : KitAction.USE_START;
        return new KitAction[]{item, click};
    }
}
